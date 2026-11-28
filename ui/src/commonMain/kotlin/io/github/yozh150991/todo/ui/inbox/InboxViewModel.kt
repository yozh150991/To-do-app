package io.github.yozh150991.todo.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yozh150991.todo.core.domain.TaskRepository
import io.github.yozh150991.todo.core.model.Task
import io.github.yozh150991.todo.ui.tasklist.DayTicker
import io.github.yozh150991.todo.ui.tasklist.TaskActions
import io.github.yozh150991.todo.ui.tasklist.TaskItem
import io.github.yozh150991.todo.ui.tasklist.UndoCenter
import io.github.yozh150991.todo.ui.tasklist.treeOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate

/** Something to tell the user; the screen turns it into localized text (composeResources). */
sealed interface UiMessage {
    data class DatabaseUnavailable(val reason: String?) : UiMessage
    data object BlankContent : UiMessage
    data class NotSaved(val reason: String?) : UiMessage
}

data class ProjectUiState(
    val projectId: String? = null,
    /** The project's name; null for the Inbox, which the screen names in the UI language. */
    val name: String? = null,
    val isInbox: Boolean = false,
    /** Open tasks in tree order. */
    val items: List<TaskItem> = emptyList(),
    val completed: List<Task> = emptyList(),
    val showCompleted: Boolean = false,
    val today: LocalDate? = null,
    val pendingSyncOperations: Int = 0,
    val loading: Boolean = true,
) {
    /** Open tasks, for callers that do not care about the tree. */
    val tasks: List<Task> get() = items.map { it.task }
}

/**
 * The tasks of one project (UX_SPEC §3.1 «Проєкт», «Вхідні»): open tasks in tree order and, behind
 * the "Show completed" switch, the completed ones. [projectId] null means the Inbox.
 */
@OptIn(ExperimentalCoroutinesApi::class)
open class ProjectViewModel(
    private val projectId: String?,
    private val repository: TaskRepository,
    days: DayTicker? = null,
    val undo: UndoCenter = UndoCenter(),
) : ViewModel() {

    private val actions = TaskActions(repository, viewModelScope, undo)
    val error: StateFlow<UiMessage?> = actions.error
    private val showCompleted = MutableStateFlow(false)
    private val today: Flow<LocalDate?> = days?.today ?: flowOf(null)

    /** Opening failures; separate from [error] (failed commands), both shown above the list. */
    private val unavailable = MutableStateFlow<UiMessage?>(null)
    val databaseError: StateFlow<UiMessage?> = unavailable

    val state: StateFlow<ProjectUiState> = flow {
        val id = projectId ?: repository.workspace().inboxId
        val project = repository.projects.get(id)
        val completed = showCompleted.flatMapLatest { show ->
            if (show) repository.observeCompletedTasks(id) else flowOf(emptyList())
        }
        emitAll(
            combine(
                repository.observeOpenTasks(id),
                completed,
                showCompleted,
                repository.observePendingOperationCount(),
                today,
            ) { open, done, show, pending, day ->
                ProjectUiState(
                    projectId = id,
                    name = project?.takeUnless { it.isInbox }?.name,
                    isInbox = project?.isInbox ?: true,
                    items = treeOrder(open),
                    completed = done,
                    showCompleted = show,
                    today = day,
                    pendingSyncOperations = pending,
                    loading = false,
                )
            },
        )
    }
        // A database that cannot be opened (e.g. a rolled-back upgrade) is shown, never a crash.
        .catch { failure ->
            unavailable.value = UiMessage.DatabaseUnavailable(failure.message)
            emit(ProjectUiState(loading = false))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectUiState())

    fun toggle(task: Task) = actions.toggle(task)

    fun complete(taskId: String) {
        state.value.tasks.firstOrNull { it.id == taskId }?.let(actions::complete)
    }

    fun uncomplete(taskId: String) = actions.uncomplete(taskId)

    fun schedule(task: Task, date: LocalDate?) = actions.schedule(task, date)

    fun toggleShowCompleted() = showCompleted.update { !it }
}

/** The Inbox (UX_SPEC §3.1): the project list of the system Inbox. */
class InboxViewModel(repository: TaskRepository, days: DayTicker? = null, undo: UndoCenter = UndoCenter()) :
    ProjectViewModel(null, repository, days, undo)
