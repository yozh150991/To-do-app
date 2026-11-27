package io.github.yozh150991.todo.ui.task

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.yozh150991.todo.core.domain.TaskRepository
import io.github.yozh150991.todo.core.model.Project
import io.github.yozh150991.todo.core.model.Task
import io.github.yozh150991.todo.ui.resources.Res
import io.github.yozh150991.todo.ui.resources.close
import io.github.yozh150991.todo.ui.resources.inbox_title
import io.github.yozh150991.todo.ui.resources.task_completed
import io.github.yozh150991.todo.ui.resources.task_deadline
import io.github.yozh150991.todo.ui.resources.task_due
import io.github.yozh150991.todo.ui.resources.task_no_date
import io.github.yozh150991.todo.ui.resources.task_not_found
import io.github.yozh150991.todo.ui.resources.task_priority
import io.github.yozh150991.todo.ui.resources.task_project
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.compose.resources.stringResource

sealed interface TaskDetailUiState {
    data object Loading : TaskDetailUiState

    /** The task does not exist or is in the bin (a stale notification, a deep link to a deleted task). */
    data object NotFound : TaskDetailUiState

    data class Loaded(val task: Task, val project: Project?) : TaskDetailUiState
}

/**
 * Task details (UX_SPEC §3.3). Ф1a PR 1 shows the fields; editing in place, complete and the bin
 * with Undo come in PR 3.
 */
class TaskDetailViewModel(taskId: String, repository: TaskRepository) : ViewModel() {
    val state: StateFlow<TaskDetailUiState> = repository.observeTask(taskId)
        .map { task ->
            if (task == null || task.isDeleted) TaskDetailUiState.NotFound
            else TaskDetailUiState.Loaded(task, repository.projects.get(task.projectId))
        }
        .catch { emit(TaskDetailUiState.NotFound) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskDetailUiState.Loading)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(viewModel: TaskDetailViewModel, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier.testTag("screen-task"),
        topBar = {
            TopAppBar(
                title = {},
                actions = { TextButton(onClick = onClose, modifier = Modifier.testTag("task-close")) { Text(stringResource(Res.string.close)) } },
            )
        },
    ) { padding ->
        val content = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
        when (val s = state) {
            TaskDetailUiState.Loading -> Unit
            TaskDetailUiState.NotFound -> Text(
                stringResource(Res.string.task_not_found),
                modifier = content.testTag("task-not-found"),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is TaskDetailUiState.Loaded -> TaskFields(s.task, s.project, content.verticalScroll(rememberScrollState()))
        }
    }
}

@Composable
private fun TaskFields(task: Task, project: Project?, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(task.content, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("task-detail-title"))
        if (task.description.isNotEmpty()) Text(task.description, style = MaterialTheme.typography.bodyLarge)
        val projectName = when {
            project == null -> "—"
            project.isInbox -> stringResource(Res.string.inbox_title)
            else -> project.name
        }
        Field(stringResource(Res.string.task_project, projectName))
        val due = task.due
        Field(
            if (due == null) {
                stringResource(Res.string.task_no_date)
            } else {
                // Locale-aware relative dates ("Tomorrow 14:00", UX_SPEC §8) come with the lists in PR 3.
                stringResource(Res.string.task_due, listOfNotNull(due.date.toString(), due.time?.toString(), due.zoneId).joinToString(" "))
            },
        )
        task.deadline?.let { Field(stringResource(Res.string.task_deadline, it.toString())) }
        Field(stringResource(Res.string.task_priority, task.priority.level))
        if (task.isCompleted) Field(stringResource(Res.string.task_completed))
    }
}

@Composable
private fun Field(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
