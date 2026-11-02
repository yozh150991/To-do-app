package io.github.yozh150991.todo.core.domain

import io.github.yozh150991.todo.core.database.LocalStore
import io.github.yozh150991.todo.core.database.entity.LocalWorkspaceEntity
import io.github.yozh150991.todo.core.database.entity.TaskActivityEntity
import io.github.yozh150991.todo.core.database.entity.TaskEntity
import io.github.yozh150991.todo.core.database.toDomain
import io.github.yozh150991.todo.core.database.toEntity
import io.github.yozh150991.todo.core.model.ActivityEvent
import io.github.yozh150991.todo.core.model.ClaimStatus
import io.github.yozh150991.todo.core.model.Due
import io.github.yozh150991.todo.core.model.IdGenerator
import io.github.yozh150991.todo.core.model.LocalWorkspace
import io.github.yozh150991.todo.core.model.OperationType
import io.github.yozh150991.todo.core.model.OrderKeys
import io.github.yozh150991.todo.core.model.OutboxOperation
import io.github.yozh150991.todo.core.model.Priority
import io.github.yozh150991.todo.core.model.Project
import io.github.yozh150991.todo.core.model.RandomUuidGenerator
import io.github.yozh150991.todo.core.model.Task
import io.github.yozh150991.todo.core.model.TaskRules
import io.github.yozh150991.todo.core.model.ValidationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Local-first repository for tasks (spec §3.2).
 *
 * Every mutation runs in one [LocalStore.write] transaction that changes the entity, appends a
 * `TaskActivity` and enqueues a typed `OutboxOperation` — all or nothing (invariant §2.1.1).
 * The UI, widgets and notification actions must go through this class; nothing writes Room directly.
 */
class TaskRepository(
    private val store: LocalStore,
    private val clock: Clock = Clock.System,
    private val ids: IdGenerator = RandomUuidGenerator,
    /** Separate from [ids] so tests can force an outbox failure after the entity row was written. */
    private val operationIds: IdGenerator = ids,
) {
    private val bootstrapMutex = Mutex()
    private var cachedWorkspace: LocalWorkspace? = null

    /**
     * Returns the local workspace, creating it together with the system Inbox on first use.
     * Identity fields (workspace, device, Inbox IDs) never change before the cloud claim (Ф5a),
     * so the result is cached; `lastLocalSeq` in the cached copy is informational only.
     */
    suspend fun workspace(): LocalWorkspace = bootstrapMutex.withLock {
        cachedWorkspace ?: run {
            if (store.workspaces.get() == null) {
                store.write { if (workspaces.get() == null) createWorkspace() }
            }
            checkNotNull(store.workspaces.get()) { "Workspace bootstrap failed" }.toDomain()
        }.also { cachedWorkspace = it }
    }

    fun observeInbox(): Flow<List<Task>> = flow { emitAll(observeOpenTasks(workspace().inboxId)) }

    fun observeOpenTasks(projectId: String): Flow<List<Task>> =
        store.tasks.observeOpenInProject(projectId).map { rows -> rows.map(TaskEntity::toDomain) }

    fun observeCompletedTasks(projectId: String): Flow<List<Task>> =
        store.tasks.observeCompletedInProject(projectId).map { rows -> rows.map(TaskEntity::toDomain) }

    fun observePendingOperationCount(): Flow<Int> = store.outbox.observePendingCount()

    suspend fun getTask(id: String): Task? = store.tasks.getById(id)?.toDomain()

    suspend fun pendingOperations(): List<OutboxOperation> = store.outbox.pending().map { it.toDomain() }

    suspend fun createTask(
        content: String,
        projectId: String? = null,
        priority: Priority = Priority.DEFAULT,
        due: Due? = null,
    ): Task {
        val normalized = TaskRules.normalizeContent(content)
        val ws = workspace()
        return store.write {
            val targetProjectId = projectId ?: ws.inboxId
            val project = projects.getById(targetProjectId)
            if (project == null || project.deletedAt != null) {
                throw ValidationException("Project $targetProjectId does not exist")
            }
            val now = clock.now()
            val task = Task(
                id = ids.newId(),
                projectId = targetProjectId,
                childOrder = OrderKeys.after(tasks.lastTopLevelOrder(targetProjectId)),
                content = normalized,
                priority = priority,
                due = due,
                createdAt = now,
                updatedAt = now,
            )
            tasks.insert(task.toEntity())
            val snapshot = task.snapshot()
            commit(ws, OperationType.TASK_CREATE, ActivityEvent.CREATED, task.id, snapshot, snapshot, null, now)
            task
        }
    }

    /** Renames a task. Returns false when the trimmed content is unchanged (no operation is recorded). */
    suspend fun updateContent(taskId: String, content: String): Boolean {
        val normalized = TaskRules.normalizeContent(content)
        val ws = workspace()
        return store.write {
            val row = requireLiveTask(taskId)
            if (row.content == normalized) return@write false
            val now = clock.now()
            tasks.update(row.copy(content = normalized, updatedAt = now.toEpochMilliseconds()))
            commit(
                ws, OperationType.TASK_UPDATE, ActivityEvent.CONTENT_CHANGED, taskId,
                patch = buildJsonObject { put("content", normalized) },
                changes = buildJsonObject { put("content", change(row.content, normalized)) },
                baseRevision = row.revision, now = now,
            )
            true
        }
    }

    /** Completes a one-off task. Completing an already completed task is a no-op and returns false. */
    suspend fun complete(taskId: String): Boolean {
        val ws = workspace()
        return store.write {
            val row = requireLiveTask(taskId)
            if (row.rrule != null) {
                // Invariant §2.1.13: recurring tasks are completed by the recurrence engine (phase Ф2) only.
                throw ValidationException("Recurring tasks cannot be completed before the recurrence engine exists")
            }
            if (!row.isCompletable) throw ValidationException("Task $taskId is not completable")
            if (row.completedAt != null) return@write false
            val now = clock.now()
            tasks.update(row.copy(completedAt = now.toEpochMilliseconds(), updatedAt = now.toEpochMilliseconds()))
            commit(
                ws, OperationType.TASK_COMPLETE, ActivityEvent.COMPLETED, taskId,
                patch = buildJsonObject { put("completed_at", now.toString()) },
                changes = buildJsonObject { put("completed_at", change(null, now.toString())) },
                baseRevision = row.revision, now = now,
            )
            true
        }
    }

    /** Reopens a completed task. Returns false when the task was not completed. */
    suspend fun uncomplete(taskId: String): Boolean {
        val ws = workspace()
        return store.write {
            val row = requireLiveTask(taskId)
            val completedAt = row.completedAt ?: return@write false
            val now = clock.now()
            tasks.update(row.copy(completedAt = null, updatedAt = now.toEpochMilliseconds()))
            commit(
                ws, OperationType.TASK_UNCOMPLETE, ActivityEvent.UNCOMPLETED, taskId,
                patch = buildJsonObject { put("completed_at", null as String?) },
                changes = buildJsonObject {
                    put("completed_at", change(Instant.fromEpochMilliseconds(completedAt).toString(), null))
                },
                baseRevision = row.revision, now = now,
            )
            true
        }
    }

    /** Soft delete (spec §4.3): the row stays for Trash/Undo and sync tombstones. */
    suspend fun delete(taskId: String): Boolean {
        val ws = workspace()
        return store.write {
            val row = tasks.getById(taskId) ?: throw ValidationException("Task $taskId does not exist")
            if (row.deletedAt != null) return@write false
            val now = clock.now()
            tasks.update(row.copy(deletedAt = now.toEpochMilliseconds(), updatedAt = now.toEpochMilliseconds()))
            commit(
                ws, OperationType.TASK_DELETE, ActivityEvent.DELETED, taskId,
                patch = buildJsonObject { put("deleted_at", now.toString()) },
                changes = buildJsonObject { put("deleted_at", change(null, now.toString())) },
                baseRevision = row.revision, now = now,
            )
            true
        }
    }

    suspend fun restore(taskId: String): Boolean {
        val ws = workspace()
        return store.write {
            val row = tasks.getById(taskId) ?: throw ValidationException("Task $taskId does not exist")
            val deletedAt = row.deletedAt ?: return@write false
            val now = clock.now()
            tasks.update(row.copy(deletedAt = null, updatedAt = now.toEpochMilliseconds()))
            commit(
                ws, OperationType.TASK_RESTORE, ActivityEvent.RESTORED, taskId,
                patch = buildJsonObject { put("deleted_at", null as String?) },
                changes = buildJsonObject {
                    put("deleted_at", change(Instant.fromEpochMilliseconds(deletedAt).toString(), null))
                },
                baseRevision = row.revision, now = now,
            )
            true
        }
    }

    // --- internals -------------------------------------------------------------------------

    private suspend fun LocalStore.createWorkspace(): LocalWorkspaceEntity {
        val now = clock.now()
        val workspaceId = ids.newId()
        val deviceId = ids.newId()
        val inbox = Project(
            id = ids.newId(),
            name = INBOX_NAME,
            childOrder = OrderKeys.first(),
            isInbox = true,
            createdAt = now,
            updatedAt = now,
        )
        val workspace = LocalWorkspaceEntity(
            workspaceId = workspaceId,
            accountScope = LocalWorkspace.localScope(workspaceId),
            inboxId = inbox.id,
            deviceId = deviceId,
            claimStatus = ClaimStatus.LOCAL.wire,
            boundUserId = null,
            claimOperationId = null,
            backupCheckpoint = null,
            lastLocalSeq = 0,
            createdAt = now.toEpochMilliseconds(),
        )
        workspaces.insert(workspace)
        projects.insert(inbox.toEntity())
        enqueue(
            operationId = operationIds.newId(),
            workspaceId = workspaceId,
            type = OperationType.PROJECT_CREATE,
            entityId = inbox.id,
            fieldPatchJson = buildJsonObject {
                put("id", inbox.id)
                put("name", inbox.name)
                put("is_inbox", true)
                put("child_order", inbox.childOrder)
                put("created_at", now.toString())
            }.toString(),
            baseRevision = null,
            deviceId = deviceId,
            createdAtMillis = now.toEpochMilliseconds(),
        )
        return workspace
    }

    private suspend fun LocalStore.requireLiveTask(taskId: String): TaskEntity {
        val row = tasks.getById(taskId) ?: throw ValidationException("Task $taskId does not exist")
        if (row.deletedAt != null) throw ValidationException("Task $taskId is deleted")
        return row
    }

    /** Activity + outbox for one mutation; called inside the same write transaction as the entity change. */
    private suspend fun LocalStore.commit(
        ws: LocalWorkspace,
        type: OperationType,
        event: ActivityEvent,
        entityId: String,
        patch: JsonObject,
        changes: JsonObject,
        baseRevision: Long?,
        now: Instant,
    ) {
        val operationId = operationIds.newId()
        activities.insert(
            TaskActivityEntity(
                id = ids.newId(),
                taskId = entityId,
                eventType = event.wire,
                fieldChanges = changes.toString(),
                createdAt = now.toEpochMilliseconds(),
                deviceId = ws.deviceId,
                operationId = operationId,
            ),
        )
        enqueue(
            operationId = operationId,
            workspaceId = ws.workspaceId,
            type = type,
            entityId = entityId,
            fieldPatchJson = patch.toString(),
            baseRevision = baseRevision,
            deviceId = ws.deviceId,
            createdAtMillis = now.toEpochMilliseconds(),
        )
    }

    private fun change(old: String?, new: String?): JsonObject = buildJsonObject {
        put("old", old)
        put("new", new)
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
        put("is_completable", isCompletable)
        put("created_at", createdAt.toString())
    }

    companion object {
        /** Stored name of the system Inbox; the UI shows a localized label for `is_inbox` projects. */
        const val INBOX_NAME = "Inbox"
    }
}
