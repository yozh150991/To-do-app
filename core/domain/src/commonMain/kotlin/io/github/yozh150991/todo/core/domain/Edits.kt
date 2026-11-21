package io.github.yozh150991.todo.core.domain

import io.github.yozh150991.todo.core.model.Due
import io.github.yozh150991.todo.core.model.Priority
import io.github.yozh150991.todo.core.model.ProjectTaskFate
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * One field of an edit: left as it is, or set to a new value. Setting `null` clears an optional
 * field, which is different from leaving it alone.
 */
sealed interface FieldEdit<out T> {
    data object Keep : FieldEdit<Nothing>

    data class Set<out T>(val value: T) : FieldEdit<T>
}

internal fun <T> FieldEdit<T>.applyTo(current: T): T = when (this) {
    FieldEdit.Keep -> current
    is FieldEdit.Set -> value
}

/**
 * Changes to a task's own fields (plan Ф1a §5, `task.update`). Where a task sits — project,
 * parent, section, order — is changed by [TaskRepository.moveTask] instead.
 */
data class TaskEdit(
    val content: FieldEdit<String> = FieldEdit.Keep,
    val description: FieldEdit<String> = FieldEdit.Keep,
    val priority: FieldEdit<Priority> = FieldEdit.Keep,
    val due: FieldEdit<Due?> = FieldEdit.Keep,
    val deadline: FieldEdit<LocalDate?> = FieldEdit.Keep,
    val durationMinutes: FieldEdit<Int?> = FieldEdit.Keep,
    val durationDays: FieldEdit<Int?> = FieldEdit.Keep,
)

/** Changes to a project's own fields (`project.update`); its place in the tree is [ProjectRepository.move]. */
data class ProjectEdit(
    val name: FieldEdit<String> = FieldEdit.Keep,
    val description: FieldEdit<String> = FieldEdit.Keep,
    val color: FieldEdit<String?> = FieldEdit.Keep,
    val isFavorite: FieldEdit<Boolean> = FieldEdit.Keep,
)

/** Changes to a reminder (`reminder.update`). Its type and task never change; delete and create instead. */
data class ReminderEdit(
    val remindAt: FieldEdit<Instant?> = FieldEdit.Keep,
    val offsetMinutes: FieldEdit<Int?> = FieldEdit.Keep,
    val enabled: FieldEdit<Boolean> = FieldEdit.Keep,
)

/** Where a task sits. Enough to put a moved task back exactly, section included. */
data class TaskPlacement(
    val taskId: String,
    val projectId: String,
    val sectionId: String?,
    val parentId: String?,
    val childOrder: String,
)

/**
 * What [TaskRepository.moveTask] changed: the previous placement of every task of the moved
 * subtree that actually changed, the moved task first. [TaskRepository.undoMove] puts them back.
 */
class TaskMove internal constructor(
    val taskId: String,
    val previous: List<TaskPlacement>,
)

/**
 * What [ProjectRepository.delete] did, for one Undo ([ProjectRepository.undoDelete]): the projects
 * it binned (the project first, then its sub-projects) and, for [ProjectTaskFate.MOVE_TO_INBOX],
 * one [TaskMove] per subtree it moved to the Inbox.
 */
class ProjectDeletion internal constructor(
    val projectId: String,
    val fate: ProjectTaskFate,
    val projectIds: List<String>,
    val moves: List<TaskMove>,
    val deletedTaskCount: Int,
) {
    /** Tasks moved to the Inbox, sub-tasks included. */
    val movedTaskCount: Int get() = moves.sumOf { it.previous.size }
}
