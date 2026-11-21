package io.github.yozh150991.todo.core.domain

import io.github.yozh150991.todo.core.database.LocalStore
import io.github.yozh150991.todo.core.database.entity.ProjectEntity
import io.github.yozh150991.todo.core.database.entity.TaskEntity
import io.github.yozh150991.todo.core.model.ActivityEvent
import io.github.yozh150991.todo.core.model.CivilResolution
import io.github.yozh150991.todo.core.model.CivilTimeOrigin
import io.github.yozh150991.todo.core.model.Due
import io.github.yozh150991.todo.core.model.OperationType
import io.github.yozh150991.todo.core.model.OrderKeys
import io.github.yozh150991.todo.core.model.TaskRules
import io.github.yozh150991.todo.core.model.ValidationCode
import io.github.yozh150991.todo.core.model.ValidationException
import io.github.yozh150991.todo.core.model.resolve
import kotlinx.datetime.IllegalTimeZoneException

/*
 * Task tree rules shared by the task and project commands (spec §4.1 `parent_id`, §4.3 «Move між
 * проєктами», plan Ф1a PR 4). All functions run inside one [Mutations.Tx]:
 * - a task and its parent are in the same project, the parent is not in the bin, no cycles, and no
 *   task is deeper than [TaskRules.MAX_DEPTH];
 * - a subtree always moves as a whole, binned rows included, so restoring one of them later never
 *   finds it in another project than its parent;
 * - sub-tasks share the section of the top task of their subtree.
 */

/** A row of a subtree and its depth below the subtree's root (the root has depth 0). */
internal data class TreeNode(val row: TaskEntity, val depth: Int)

/**
 * The task and its descendants, parents before children (breadth first). [descend] decides whether
 * a child — and everything below it — belongs to the result; the root always does.
 */
internal suspend fun LocalStore.taskSubtree(root: TaskEntity, descend: (TaskEntity) -> Boolean = { true }): List<TreeNode> {
    val result = mutableListOf(TreeNode(root, 0))
    val seen = mutableSetOf(root.id)
    var next = 0
    while (next < result.size) {
        val node = result[next++]
        for (child in tasks.children(node.row.id)) {
            if (descend(child) && seen.add(child.id)) result += TreeNode(child, node.depth + 1)
        }
    }
    return result
}

/** Depth of [task]: 0 for a top-level task. */
internal suspend fun LocalStore.depthOf(task: TaskEntity): Int {
    var depth = 0
    var parentId = task.parentId
    val seen = mutableSetOf(task.id)
    while (parentId != null) {
        check(seen.add(parentId)) { "Cycle in the task tree at $parentId" }
        val parent = checkNotNull(tasks.getById(parentId)) { "Task ${task.id} has a missing ancestor $parentId" }
        depth++
        parentId = parent.parentId
    }
    return depth
}

internal fun TaskEntity.placement() = TaskPlacement(id, projectId, sectionId, parentId, childOrder)

internal fun notFound(what: String): Nothing = throw ValidationException("$what does not exist or is in the bin", ValidationCode.NOT_FOUND)

internal fun hierarchy(message: String): Nothing = throw ValidationException(message, ValidationCode.HIERARCHY)

internal suspend fun Mutations.Tx.requireLiveTask(taskId: String): TaskEntity {
    val row = store.tasks.getById(taskId) ?: notFound("Task $taskId")
    if (row.deletedAt != null) notFound("Task $taskId")
    return row
}

/**
 * A project that can receive tasks: it exists, is not in the bin and — unless [allowArchived], as
 * for an Undo that puts tasks back where they were — is not archived.
 */
internal suspend fun Mutations.Tx.requireProjectForTasks(projectId: String, allowArchived: Boolean = false): ProjectEntity {
    val project = store.projects.getById(projectId) ?: notFound("Project $projectId")
    if (project.deletedAt != null) notFound("Project $projectId")
    if (project.archivedAt != null && !allowArchived) {
        throw ValidationException("Project $projectId is archived", ValidationCode.ARCHIVED_PROJECT)
    }
    return project
}

/**
 * Checks that [subtree] (a task with its descendants) may sit under [parent] in [projectId]:
 * the project takes tasks, the parent is live and in that project, the parent is not inside the
 * subtree (no cycle) and the deepest open or completed row stays within [TaskRules.MAX_DEPTH].
 */
internal suspend fun Mutations.Tx.checkPlacement(
    subtree: List<TreeNode>,
    projectId: String,
    parent: TaskEntity?,
    allowArchived: Boolean = false,
) {
    requireProjectForTasks(projectId, allowArchived)
    if (parent == null) return
    if (parent.deletedAt != null) notFound("Parent task ${parent.id}")
    if (parent.projectId != projectId) hierarchy("A sub-task must be in the project of its parent")
    if (subtree.any { it.row.id == parent.id }) hierarchy("A task cannot become a sub-task of itself or of its own sub-task")
    val height = subtree.filter { it.row.deletedAt == null }.maxOf { it.depth }
    if (store.depthOf(parent) + 1 + height > TaskRules.MAX_DEPTH) {
        hierarchy("Sub-tasks go at most ${TaskRules.MAX_DEPTH} levels deep")
    }
}

/** Writes [target] into the row when it differs; one `task.move` operation per changed row. */
internal suspend fun Mutations.Tx.place(row: TaskEntity, target: TaskPlacement): Boolean {
    val changes = FieldChanges()
    changes.diff("project_id", row.projectId, target.projectId) { jsonOf(it) }
    changes.diff("section_id", row.sectionId, target.sectionId) { jsonOf(it) }
    changes.diff("parent_id", row.parentId, target.parentId) { jsonOf(it) }
    changes.diff("child_order", row.childOrder, target.childOrder) { jsonOf(it) }
    if (changes.isEmpty) return false
    store.tasks.update(
        row.copy(
            projectId = target.projectId,
            sectionId = target.sectionId,
            parentId = target.parentId,
            childOrder = target.childOrder,
            updatedAt = nowMillis,
        ),
    )
    record(OperationType.TASK_MOVE, row.id, changes.patch(), row.revision, Activity(ActivityEvent.MOVED, changes.activity()))
    return true
}

/**
 * Puts [root] with its whole subtree under [parent], or at the top level of [projectId], after
 * the last sibling there. Call [checkPlacement] first. Returns the previous placement of every
 * row that changed, root first.
 */
internal suspend fun Mutations.Tx.moveSubtree(root: TaskEntity, projectId: String, parent: TaskEntity?): List<TaskPlacement> {
    val subtree = store.taskSubtree(root)
    val sectionId = when {
        parent != null -> parent.sectionId
        projectId != root.projectId -> null
        else -> root.sectionId
    }
    val order = if (parent != null) {
        OrderKeys.after(store.tasks.lastChildOrder(parent.id))
    } else {
        OrderKeys.after(store.tasks.lastTopLevelOrder(projectId))
    }
    val previous = mutableListOf<TaskPlacement>()
    for (node in subtree) {
        val row = node.row
        val target = if (node.depth == 0) {
            TaskPlacement(row.id, projectId, sectionId, parent?.id, order)
        } else {
            TaskPlacement(row.id, projectId, sectionId, row.parentId, row.childOrder)
        }
        if (place(row, target)) previous += row.placement()
    }
    return previous
}

/**
 * Undo of one [moveSubtree]: the task goes back to its old parent and order, and the rows that are
 * in its subtree *now* go back to the old project — with their old section when they were moved
 * along, the task's old section otherwise. Rows that left the subtree since are not touched, rows
 * that joined it come along, so the subtree stays whole. Validated like a move, except that the
 * old project may have been archived meanwhile. Returns false when nothing had to change.
 */
internal suspend fun Mutations.Tx.undoSubtreeMove(move: TaskMove): Boolean {
    val old = move.previous.firstOrNull { it.taskId == move.taskId } ?: return false
    val root = requireLiveTask(move.taskId)
    val oldParent = old.parentId?.let { store.tasks.getById(it) ?: notFound("Parent task $it") }
    val subtree = store.taskSubtree(root)
    checkPlacement(subtree, old.projectId, oldParent, allowArchived = true)
    val previous = move.previous.associateBy { it.taskId }
    var changed = false
    for (node in subtree) {
        val row = node.row
        val target = if (node.depth == 0) {
            old.copy(childOrder = freeOrder(old.projectId, old.parentId, old.childOrder, row.id))
        } else {
            TaskPlacement(row.id, old.projectId, previous[row.id]?.sectionId ?: old.sectionId, row.parentId, row.childOrder)
        }
        changed = place(row, target) || changed
    }
    return changed
}

/**
 * [wanted] when no other sibling at that place uses it, else a key after the last sibling: a task
 * created at the end of the list after a move may have taken the old key.
 */
private suspend fun Mutations.Tx.freeOrder(projectId: String, parentId: String?, wanted: String, selfId: String): String {
    val siblings = if (parentId != null) store.tasks.children(parentId) else store.tasks.topLevelInProject(projectId)
    val others = siblings.filter { it.id != selfId }
    if (others.none { it.childOrder == wanted }) return wanted
    return OrderKeys.after(others.maxOfOrNull { it.childOrder })
}

/** Bins [root] and every descendant not already in the bin; all get [Mutations.Tx.now]. */
internal suspend fun Mutations.Tx.binSubtree(root: TaskEntity): Int {
    var count = 0
    for (node in store.taskSubtree(root) { it.deletedAt == null }) {
        val row = node.row
        if (row.deletedAt != null) continue
        store.tasks.update(row.copy(deletedAt = nowMillis, updatedAt = nowMillis))
        val changes = FieldChanges().apply { add("deleted_at", jsonOf(null as String?), jsonOfMillis(nowMillis)) }
        record(OperationType.TASK_DELETE, row.id, changes.patch(), row.revision, Activity(ActivityEvent.DELETED, changes.activity()))
        count++
    }
    return count
}

/** Takes one binned row out of the bin. The caller checks the parent and the project. */
internal suspend fun Mutations.Tx.unbin(row: TaskEntity) {
    val changes = FieldChanges().apply { add("deleted_at", jsonOfMillis(row.deletedAt), jsonOf(null as String?)) }
    store.tasks.update(row.copy(deletedAt = null, updatedAt = nowMillis))
    record(OperationType.TASK_RESTORE, row.id, changes.patch(), row.revision, Activity(ActivityEvent.RESTORED, changes.activity()))
}

/**
 * Restores [root] together with the descendants binned in the same command (the same
 * `deleted_at`); rows binned earlier on their own stay in the bin. The parent must be live.
 */
internal suspend fun Mutations.Tx.restoreSubtree(root: TaskEntity): Int {
    val deletedAt = root.deletedAt ?: return 0
    root.parentId?.let { parentId ->
        if (store.tasks.getById(parentId)?.deletedAt != null) hierarchy("Restore the parent task first")
    }
    val project = store.projects.getById(root.projectId)
    if (project == null || project.deletedAt != null) notFound("Project ${root.projectId}")
    val group = store.taskSubtree(root) { it.deletedAt == deletedAt }
    if (store.depthOf(root) + group.maxOf { it.depth } > TaskRules.MAX_DEPTH) {
        hierarchy("Sub-tasks go at most ${TaskRules.MAX_DEPTH} levels deep")
    }
    group.forEach { unbin(it.row) }
    return group.size
}

/**
 * spec §4.0.1 п. 4 / TIME-08: a one-off time typed for a date where it does not exist (DST gap)
 * is rejected, never shifted silently. Floating times are checked in the device zone.
 */
internal fun Mutations.checkExplicitTime(due: Due?) {
    if (due == null || due.time == null) return
    val resolution = try {
        due.resolve(deviceZone(), CivilTimeOrigin.EXPLICIT)
    } catch (e: IllegalTimeZoneException) {
        throw ValidationException("Unknown time zone ${due.zoneId}: ${e.message}")
    }
    if (resolution is CivilResolution.Nonexistent) {
        throw ValidationException(
            "${resolution.requested} does not exist in ${resolution.zone} (DST gap); choose ${resolution.choices.joinToString()}",
            ValidationCode.NONEXISTENT_LOCAL_TIME,
        )
    }
}
