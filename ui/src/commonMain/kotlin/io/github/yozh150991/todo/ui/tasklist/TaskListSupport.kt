package io.github.yozh150991.todo.ui.tasklist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.yozh150991.todo.core.domain.TaskRepository
import io.github.yozh150991.todo.core.model.Project
import io.github.yozh150991.todo.core.model.Task
import io.github.yozh150991.todo.core.model.ValidationException
import io.github.yozh150991.todo.ui.inbox.UiMessage
import io.github.yozh150991.todo.ui.resources.Res
import io.github.yozh150991.todo.ui.resources.ic_more
import io.github.yozh150991.todo.ui.resources.more_options
import io.github.yozh150991.todo.ui.resources.show_completed
import io.github.yozh150991.todo.ui.resources.snackbar_completed
import io.github.yozh150991.todo.ui.resources.snackbar_undo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Something a list tells the user once, with an Undo (UX_SPEC §5.5). */
sealed interface ListEvent {
    data class Completed(val taskId: String, val title: String) : ListEvent
}

/**
 * Complete and reopen from any list, through the repository; a completion is announced once with
 * Undo, which is a new command (`uncomplete`), not the removal of the first one (plan Ф1a §5).
 */
class TaskActions(private val repository: TaskRepository, private val scope: CoroutineScope) {
    private val _events = MutableSharedFlow<ListEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<ListEvent> = _events.asSharedFlow()

    private val _error = MutableStateFlow<UiMessage?>(null)
    val error: StateFlow<UiMessage?> = _error.asStateFlow()

    fun toggle(task: Task) {
        if (task.isCompleted) uncomplete(task.id) else complete(task)
    }

    fun complete(task: Task) = launchSafely {
        if (repository.complete(task.id)) _events.emit(ListEvent.Completed(task.id, task.content))
    }

    fun uncomplete(taskId: String) = launchSafely { repository.uncomplete(taskId) }

    private fun launchSafely(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                _error.value = null
            } catch (e: ValidationException) {
                _error.value = UiMessage.NotSaved(e.message)
            } catch (e: IllegalStateException) {
                _error.value = UiMessage.DatabaseUnavailable(e.message)
            }
        }
    }
}

/** Shows each completion as a snackbar with Undo (UX_SPEC §3.1: «Виконано · Скасувати»). */
@Composable
fun CompletionSnackbars(events: Flow<ListEvent>, snackbar: SnackbarHostState, onUndo: (String) -> Unit) {
    LaunchedEffect(events, snackbar) {
        events.collect { event ->
            when (event) {
                is ListEvent.Completed -> {
                    val result = snackbar.showSnackbar(
                        message = getString(Res.string.snackbar_completed, event.title),
                        actionLabel = getString(Res.string.snackbar_undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) onUndo(event.taskId)
                }
            }
        }
    }
}

/** Screen menu with the "Show completed" switch (UX_SPEC §12: completed tasks hidden by default). */
@Composable
fun ShowCompletedMenu(showCompleted: Boolean, onToggle: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag("menu")) {
            Icon(painterResource(Res.drawable.ic_more), contentDescription = stringResource(Res.string.more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.show_completed)) },
                trailingIcon = { Switch(checked = showCompleted, onCheckedChange = null) },
                onClick = {
                    onToggle()
                    open = false
                },
                modifier = Modifier.testTag("menu-show-completed"),
            )
        }
    }
}

/** A group or day heading inside a list; marked as a heading for screen readers. */
@Composable
fun ListHeader(text: String, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = color,
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
    )
}

/** Project names by ID for the meta line of mixed lists; the Inbox by its localized name. */
fun projectNames(projects: List<Project>, inboxName: String): Map<String, String> =
    projects.associate { it.id to if (it.isInbox) inboxName else it.name }

/** Active projects in tree order with their depth (Browse). */
fun projectTree(projects: List<Project>): List<Pair<Project, Int>> {
    val ids = projects.mapTo(HashSet()) { it.id }
    val children = projects.filter { it.parentId != null && it.parentId in ids }.groupBy { it.parentId }
    val result = mutableListOf<Pair<Project, Int>>()
    fun visit(project: Project, depth: Int) {
        result += project to depth
        children[project.id].orEmpty().sortedWith(compareBy({ it.childOrder }, { it.id })).forEach { visit(it, depth + 1) }
    }
    projects.filter { it.parentId == null || it.parentId !in ids }.sortedWith(compareBy({ it.childOrder }, { it.id })).forEach { visit(it, 0) }
    return result
}
