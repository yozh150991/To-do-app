package io.github.yozh150991.todo.ui.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yozh150991.todo.core.model.Task
import io.github.yozh150991.todo.core.model.TimeZones
import io.github.yozh150991.todo.ui.quickadd.AddTaskButton
import io.github.yozh150991.todo.ui.quickadd.QuickAddContext
import io.github.yozh150991.todo.ui.resources.Res
import io.github.yozh150991.todo.ui.resources.about
import io.github.yozh150991.todo.ui.resources.back
import io.github.yozh150991.todo.ui.resources.completed_header
import io.github.yozh150991.todo.ui.resources.database_unavailable
import io.github.yozh150991.todo.ui.resources.empty_inbox
import io.github.yozh150991.todo.ui.resources.empty_inbox_hint
import io.github.yozh150991.todo.ui.resources.empty_project
import io.github.yozh150991.todo.ui.resources.error_blank_content
import io.github.yozh150991.todo.ui.resources.error_not_saved
import io.github.yozh150991.todo.ui.resources.ic_back
import io.github.yozh150991.todo.ui.resources.inbox_title
import io.github.yozh150991.todo.ui.resources.pending_sync
import io.github.yozh150991.todo.ui.tasklist.ListHeader
import io.github.yozh150991.todo.ui.tasklist.ScheduleSheet
import io.github.yozh150991.todo.ui.tasklist.ShowCompletedMenu
import io.github.yozh150991.todo.ui.tasklist.SwipeableTaskRow
import io.github.yozh150991.todo.ui.tasklist.TaskItem
import io.github.yozh150991.todo.ui.tasklist.TaskRow
import io.github.yozh150991.todo.ui.tasklist.UndoSnackbars
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/**
 * Inbox (UX_SPEC §3.1): the project list of the system Inbox, with "About" in the top bar (release
 * builds show no Browse tab). New tasks come from "+" ([onAddTask]); a tap opens the details.
 */
@Composable
fun InboxScreen(
    viewModel: ProjectViewModel,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
    onAddTask: (QuickAddContext) -> Unit = {},
    onOpenTask: (String) -> Unit = {},
    selectedTaskId: String? = null,
) {
    ProjectTasksScreen(
        viewModel = viewModel,
        modifier = modifier.testTag("screen-inbox"),
        onAddTask = onAddTask,
        onOpenTask = onOpenTask,
        selectedTaskId = selectedTaskId,
        actions = { TextButton(onClick = onOpenAbout) { Text(stringResource(Res.string.about)) } },
    )
}

/**
 * A project's tasks (UX_SPEC §3.1): open tasks in tree order with sub-tasks indented, completion
 * with Undo, completed tasks behind "Show completed" in the screen menu, and "+" with the project
 * as the context. [onBack] adds a back arrow (projects opened from Browse).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectTasksScreen(
    viewModel: ProjectViewModel,
    modifier: Modifier = Modifier,
    onAddTask: (QuickAddContext) -> Unit = {},
    onOpenTask: (String) -> Unit = {},
    selectedTaskId: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val failed by viewModel.error.collectAsStateWithLifecycle()
    val unavailable by viewModel.databaseError.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val fallbackToday = remember { Clock.System.todayIn(TimeZones.systemDefault()) }
    val today = state.today ?: fallbackToday
    UndoSnackbars(viewModel.undo, snackbar)
    var scheduling by remember { mutableStateOf<Task?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.name ?: stringResource(Res.string.inbox_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("back")) {
                            Icon(painterResource(Res.drawable.ic_back), contentDescription = stringResource(Res.string.back))
                        }
                    }
                },
                actions = {
                    actions()
                    ShowCompletedMenu(state.showCompleted, onToggle = viewModel::toggleShowCompleted)
                },
            )
        },
        floatingActionButton = { AddTaskButton(onClick = { onAddTask(QuickAddContext(projectId = state.projectId)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            (unavailable ?: failed)?.let {
                Text(it.text(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            }
            Text(
                text = stringResource(Res.string.pending_sync, state.pendingSyncOperations),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (!state.loading && state.items.isEmpty()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(if (state.isInbox) Res.string.empty_inbox else Res.string.empty_project),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        stringResource(Res.string.empty_inbox_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Room under the last row for the "+" button.
            LazyColumn(modifier = Modifier.fillMaxSize().testTag("task-list"), contentPadding = PaddingValues(bottom = 88.dp)) {
                items(state.items, key = { it.task.id }) { item ->
                    SwipeableTaskRow(
                        item = item,
                        today = today,
                        selected = item.task.id == selectedTaskId,
                        onOpen = { onOpenTask(item.task.id) },
                        onToggle = { viewModel.toggle(item.task) },
                        onSchedule = { scheduling = item.task },
                    )
                    HorizontalDivider()
                }
                if (state.showCompleted && state.completed.isNotEmpty()) {
                    item(key = "completed-header") { ListHeader(stringResource(Res.string.completed_header)) }
                    items(state.completed, key = { "done-" + it.id }) { task ->
                        TaskRow(
                            item = TaskItem(task),
                            today = today,
                            selected = task.id == selectedTaskId,
                            onOpen = { onOpenTask(task.id) },
                            onToggle = { viewModel.toggle(task) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    scheduling?.let { task ->
        ScheduleSheet(task, today, onPick = { viewModel.schedule(task, it) }, onDismiss = { scheduling = null })
    }
}

@Composable
internal fun UiMessage.text(): String = when (this) {
    is UiMessage.DatabaseUnavailable -> stringResource(Res.string.database_unavailable, reason.orEmpty()).trim()
    UiMessage.BlankContent -> stringResource(Res.string.error_blank_content)
    is UiMessage.NotSaved -> stringResource(Res.string.error_not_saved, reason.orEmpty())
}
