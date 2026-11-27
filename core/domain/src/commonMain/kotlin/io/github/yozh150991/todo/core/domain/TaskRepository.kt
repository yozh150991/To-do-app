package io.github.yozh150991.todo.core.domain

import io.github.yozh150991.todo.core.database.DatabaseDiagnostics
import io.github.yozh150991.todo.core.database.LocalStore
import io.github.yozh150991.todo.core.database.LocalStoreProvider
import io.github.yozh150991.todo.core.database.diagnostics
import io.github.yozh150991.todo.core.database.entity.TaskEntity
import io.github.yozh150991.todo.core.database.toDomain
import io.github.yozh150991.todo.core.database.toEntity
import io.github.yozh150991.todo.core.model.ActivityEvent
import io.github.yozh150991.todo.core.model.Due
import io.github.yozh150991.todo.core.model.IdGenerator
import io.github.yozh150991.todo.core.model.LocalWorkspace
import io.github.yozh150991.todo.core.model.OperationType
import io.github.yozh150991.todo.core.model.OrderKeys
import io.github.yozh150991.todo.core.model.OutboxOperation
import io.github.yozh150991.todo.core.model.Priority
import io.github.yozh150991.todo.core.model.RandomUuidGenerator
import io.github.yozh150991.todo.core.model.Task
import io.github.yozh150991.todo.core.model.TaskRules
import io.github.yozh150991.todo.core.model.TimeZones
import io.github.yozh150991.todo.core.model.ValidationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock

/**
 * Local-first repository for tasks (spec §3.2), and the entry point to [projects] and [reminders].
 *
 * Every mutation runs in one write transaction that changes the entities, appends a `TaskActivity`
 * per changed task and enqueues one typed `OutboxOperation` per changed entity — all or nothing
 * (invariant §2.1.1). The UI, widgets and notification actions must go through these classes;
 * nothing writes Room directly. Commands that change nothing return `false` or `null` and record
 * nothing. Undo is a new command (plan Ф1a §5), never the removal of an outbox row.
 */
class TaskRepository(
    stores: LocalStoreProvider,
    clock: Clock = Clock.System,
    ids: IdGenerator = RandomUuidGenerator,
    /** Separate from [ids] so tests can force an outbox failure after the entity row was written. */
    operationIds: IdGenerator = ids,
    /** Zone for floating due times; tests pass a fixed one (spec §4.0.1 п. 7). */
    deviceZone: () -> TimeZone = { TimeZones.systemDefault() },
) {
    /** Convenience for an already opened store (tests, tools). */
    constructor(
        store: LocalStore,
        clock: Clock = Clock.System,
        ids: IdGenerator = RandomUuidGenerator,
        operationIds: IdGenerator = ids,
        deviceZone: () -> TimeZone = { TimeZones.systemDefault() },
    ) : this(LocalStoreProvider.of(store), clock, ids, operationIds, deviceZone)

    private val mutations = Mutations(stores, clock, ids, operationIds, deviceZone)

    /** Projects: create, edit, nest, archive, delete with a choice for their tasks (plan Ф1a PR 5). */
    val projects: ProjectRepository = ProjectRepository(mutations)

    /** Reminders of tasks (Ф1a: the automatic one; Ф3: the full set). */
    val reminders: ReminderRepository = ReminderRepository(mutations)

    private suspend fun store(): LocalStore = mutations.store()

    /** The local workspace, created together with the system Inbox on first use. */
    suspend fun workspace(): LocalWorkspace = mutations.workspace()

    fun observeInbox(): Flow<List<Task>> = flow { emitAll(observeOpenTasks(workspace().inboxId)) }

    fun observeOpenTasks(projectId: String): Flow<List<Task>> = flow {
        emitAll(store().tasks.observeOpenInProject(projectId).map { rows -> rows.map(TaskEntity::toDomain) })
    }

    fun observeCompletedTasks(projectId: String): Flow<List<Task>> = flow {
        emitAll(store().tasks.observeCompletedInProject(projectId).map { rows -> rows.map(TaskEntity::toDomain) })
    }

    fun observePendingOperationCount(): Flow<Int> = flow { emitAll(store().outbox.observePendingCount()) }

    /** SQLite version, FTS5, journal, schema, quick_check and counts (About dialog, device evidence). */
    suspend fun diagnostics(): DatabaseDiagnostics = store().diagnostics()

    suspend fun getTask(id: String): Task? = store().tasks.getById(id)?.toDomain()

    /** The task as it changes; null when it does not exist. Binned tasks are emitted with [Task.deletedAt]. */
    fun observeTask(id: String): Flow<Task?> = flow {
        emitAll(store().tasks.observeById(id).map { row -> row?.toDomain() })
    }

    /** Direct sub-tasks that are not in the bin, in list order. */
    suspend fun subtasks(parentId: String): List<Task> =
        store().tasks.children(parentId).filter { it.deletedAt == null }.map(TaskEntity::toDomain)

    suspend fun pendingOperations(): List<OutboxOperation> = store().outbox.pending().map { it.toDomain() }

    /**
     * Creates a task at the end of [projectId] (Inbox when null) or, with [parentId], at the end of
     * that task's sub-tasks — in the parent's project and section, at most [TaskRules.MAX_DEPTH] deep.
     */
    suspend fun createTask(
        content: String,
        projectId: String? = null,
        priority: Priority = Priority.DEFAULT,
        due: Due? = null,
        description: String = "",
        parentId: String? = null,
        deadline: LocalDate? = null,
        durationMinutes: Int? = null,
        durationDays: Int? = null,
    ): Task {
        val normalized = TaskRules.normalizeContent(content)
        TaskRules.checkDuration(durationMinutes, durationDays)
        mutations.checkExplicitTime(due)
        return mutations.write {
            val parent = parentId?.let { requireLiveTask(it) }
            val targetProjectId = parent?.projectId ?: projectId ?: ws.inboxId
            if (parent != null && projectId != null && projectId != parent.projectId) {
                hierarchy("A sub-task must be in the project of its parent")
            }
            requireProjectForTasks(targetProjectId)
            if (parent != null && store.depthOf(parent) + 1 > TaskRules.MAX_DEPTH) {
                hierarchy("Sub-tasks go at most ${TaskRules.MAX_DEPTH} levels deep")
            }
            val task = Task(
                id = mutations.ids.newId(),
                projectId = targetProjectId,
                sectionId = parent?.sectionId,
                parentId = parent?.id,
                childOrder = if (parent != null) {
                    OrderKeys.after(store.tasks.lastChildOrder(parent.id))
                } else {
                    OrderKeys.after(store.tasks.lastTopLevelOrder(targetProjectId))
                },
                content = normalized,
                description = description,
                priority = priority,
                due = due,
                deadline = deadline,
                durationMinutes = durationMinutes,
                durationDays = durationDays,
                createdAt = now,
                updatedAt = now,
            )
            store.tasks.insert(task.toEntity())
            val snapshot = task.snapshot()
            record(OperationType.TASK_CREATE, task.id, snapshot, null, Activity(ActivityEvent.CREATED, snapshot))
            task
        }
    }

    /**
     * Changes the task's own fields. Only fields that really change go into the operation; returns
     * false when nothing changes. The due date of a recurring task is the cache of its occurrence
     * and cannot be edited before the recurrence engine exists (Ф2).
     */
    suspend fun updateTask(taskId: String, edit: TaskEdit): Boolean {
        val content = when (val c = edit.content) {
            FieldEdit.Keep -> null
            is FieldEdit.Set -> TaskRules.normalizeContent(c.value)
        }
        val due = edit.due
        if (due is FieldEdit.Set) mutations.checkExplicitTime(due.value)
        return mutations.write {
            val row = requireLiveTask(taskId)
            val current = row.toDomain()
            val next = current.copy(
                content = content ?: current.content,
                description = edit.description.applyTo(current.description),
                priority = edit.priority.applyTo(current.priority),
                due = edit.due.applyTo(current.due),
                deadline = edit.deadline.applyTo(current.deadline),
                durationMinutes = edit.durationMinutes.applyTo(current.durationMinutes),
                durationDays = edit.durationDays.applyTo(current.durationDays),
            ) // Task's init checks the duration rule (ADR-005).
            if (next == current) return@write false
            if (next.due != current.due && row.rrule != null) {
                throw ValidationException("The due date of a recurring task changes through the recurrence engine (Ф2)")
            }

            val changes = FieldChanges()
            changes.diff("content", current.content, next.content) { jsonOf(it) }
            changes.diff("description", current.description, next.description) { jsonOf(it) }
            changes.diff("priority", current.priority.level, next.priority.level) { jsonOf(it) }
            if (next.due != current.due) {
                // The three due columns are one value: they always travel together.
                changes.add("due_date", jsonOf(current.due?.date?.toString()), jsonOf(next.due?.date?.toString()))
                changes.add("due_time", jsonOf(current.due?.time?.toString()), jsonOf(next.due?.time?.toString()))
                changes.add("due_tz", jsonOf(current.due?.zoneId), jsonOf(next.due?.zoneId))
            }
            changes.diff("deadline", current.deadline?.toString(), next.deadline?.toString()) { jsonOf(it) }
            changes.diff("duration_min", current.durationMinutes, next.durationMinutes) { jsonOf(it) }
            changes.diff("duration_days", current.durationDays, next.durationDays) { jsonOf(it) }

            store.tasks.update(next.copy(updatedAt = now).toEntity(existing = row))
            record(
                OperationType.TASK_UPDATE, taskId, changes.patch(), row.revision,
                Activity(eventFor(changes.keys), changes.activity()),
            )
            true
        }
    }

    /** Renames a task. Returns false when the trimmed content is unchanged (no operation is recorded). */
    suspend fun updateContent(taskId: String, content: String): Boolean =
        updateTask(taskId, TaskEdit(content = FieldEdit.Set(content)))

    /**
     * Moves the task with all its sub-tasks: under [parentId] (into the parent's project), or to the
     * top level of [projectId] — the task's own project when null, which is how a sub-task is
     * promoted. The task goes after the last sibling at the new place; sub-tasks keep their order.
     * Every changed task gets its own `task.move` in one transaction. Returns null when the task is
     * already there.
     */
    suspend fun moveTask(taskId: String, projectId: String? = null, parentId: String? = null): TaskMove? =
        mutations.write {
            val row = requireLiveTask(taskId)
            val parent = parentId?.let { store.tasks.getById(it) ?: notFound("Parent task $it") }
            if (parent != null && projectId != null && projectId != parent.projectId) {
                hierarchy("A sub-task must be in the project of its parent")
            }
            val targetProjectId = parent?.projectId ?: projectId ?: row.projectId
            if (targetProjectId == row.projectId && parent?.id == row.parentId) return@write null
            checkPlacement(store.taskSubtree(row), targetProjectId, parent)
            TaskMove(taskId, moveSubtree(row, targetProjectId, parent))
        }

    /**
     * Undo of [moveTask]: the task goes back to exactly where it was — section and order included —
     * with its subtree. Fails without changes when the old place is no longer valid (the old project
     * or parent was binned, or the old parent is now inside the moved subtree).
     */
    suspend fun undoMove(move: TaskMove): Boolean = mutations.write { undoSubtreeMove(move) }

    /**
     * Completes a one-off task. Completing an already completed task is a no-op and returns false.
     * Sub-tasks are not completed with their parent (spec §4.3).
     */
    suspend fun complete(taskId: String): Boolean = mutations.write {
        val row = requireLiveTask(taskId)
        if (row.rrule != null) {
            // Invariant §2.1.13: recurring tasks are completed by the recurrence engine (phase Ф2) only.
            throw ValidationException("Recurring tasks cannot be completed before the recurrence engine exists")
        }
        if (!row.isCompletable) throw ValidationException("Task $taskId is not completable")
        if (row.completedAt != null) return@write false
        store.tasks.update(row.copy(completedAt = nowMillis, updatedAt = nowMillis))
        val changes = FieldChanges().apply { add("completed_at", jsonOf(null as String?), jsonOf(now.toString())) }
        record(OperationType.TASK_COMPLETE, taskId, changes.patch(), row.revision, Activity(ActivityEvent.COMPLETED, changes.activity()))
        true
    }

    /** Reopens a completed task. Returns false when the task was not completed. */
    suspend fun uncomplete(taskId: String): Boolean = mutations.write {
        val row = requireLiveTask(taskId)
        val completedAt = row.completedAt ?: return@write false
        store.tasks.update(row.copy(completedAt = null, updatedAt = nowMillis))
        val changes = FieldChanges().apply { add("completed_at", jsonOfMillis(completedAt), jsonOf(null as String?)) }
        record(OperationType.TASK_UNCOMPLETE, taskId, changes.patch(), row.revision, Activity(ActivityEvent.UNCOMPLETED, changes.activity()))
        true
    }

    /**
     * Soft delete (spec §4.3): the task and all its sub-tasks go to the bin in one transaction, one
     * `task.delete` each; rows stay for Undo and sync tombstones. Returns false when already binned.
     */
    suspend fun delete(taskId: String): Boolean = mutations.write {
        val row = store.tasks.getById(taskId) ?: notFound("Task $taskId")
        if (row.deletedAt != null) return@write false
        binSubtree(row) > 0
    }

    /**
     * Takes the task out of the bin with the sub-tasks binned together with it (Undo of [delete]).
     * Its parent must not be in the bin. Returns false when the task is not binned.
     */
    suspend fun restore(taskId: String): Boolean = mutations.write {
        val row = store.tasks.getById(taskId) ?: notFound("Task $taskId")
        if (row.deletedAt == null) return@write false
        restoreSubtree(row) > 0
    }

    private fun eventFor(fields: Set<String>): ActivityEvent {
        val kinds = fields.map { field ->
            when (field) {
                "content" -> ActivityEvent.CONTENT_CHANGED
                "description" -> ActivityEvent.DESCRIPTION_CHANGED
                "priority" -> ActivityEvent.PRIORITY_CHANGED
                "due_date", "due_time", "due_tz" -> ActivityEvent.DUE_CHANGED
                "deadline" -> ActivityEvent.DEADLINE_CHANGED
                "duration_min", "duration_days" -> ActivityEvent.DURATION_CHANGED
                else -> ActivityEvent.UPDATED
            }
        }.toSet()
        return kinds.singleOrNull() ?: ActivityEvent.UPDATED
    }

    private fun Task.snapshot(): JsonObject = buildJsonObject {
        put("id", id)
        put("project_id", projectId)
        put("section_id", sectionId)
        put("parent_id", parentId)
        put("child_order", childOrder)
        put("content", content)
        put("description", description)
        put("priority", priority.level)
        put("due_date", due?.date?.toString())
        put("due_time", due?.time?.toString())
        put("due_tz", due?.zoneId)
        put("deadline", deadline?.toString())
        put("duration_min", durationMinutes)
        put("duration_days", durationDays)
        put("is_completable", isCompletable)
        put("created_at", createdAt.toString())
    }

    companion object {
        /** Stored name of the system Inbox; the UI shows a localized label for `is_inbox` projects. */
        const val INBOX_NAME = "Inbox"
    }
}
