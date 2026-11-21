package io.github.yozh150991.todo.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yozh150991.todo.core.model.Task
import io.github.yozh150991.todo.ui.quickadd.AddTaskButton
import io.github.yozh150991.todo.ui.resources.Res
import io.github.yozh150991.todo.ui.resources.about
import io.github.yozh150991.todo.ui.resources.complete_task
import io.github.yozh150991.todo.ui.resources.database_unavailable
import io.github.yozh150991.todo.ui.resources.empty_inbox
import io.github.yozh150991.todo.ui.resources.empty_inbox_hint
import io.github.yozh150991.todo.ui.resources.error_blank_content
import io.github.yozh150991.todo.ui.resources.error_not_saved
import io.github.yozh150991.todo.ui.resources.inbox_title
import io.github.yozh150991.todo.ui.resources.pending_sync
import org.jetbrains.compose.resources.stringResource

/**
 * Inbox (UX_SPEC §3.1). New tasks come from the "+" button (Quick Add, [onAddTask]); a tap on a
 * row opens its details ([onOpenTask]); [selectedTaskId] is highlighted while the details sit next
 * to the list on expanded widths. The full row — dates, swipes, Undo — comes in PR 3.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    viewModel: InboxViewModel,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
    onAddTask: () -> Unit = {},
    onOpenTask: (String) -> Unit = {},
    selectedTaskId: String? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.testTag("screen-inbox"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.inbox_title)) },
                // A text button for now: the icon set comes with the Ф1a screens.
                actions = { TextButton(onClick = onOpenAbout) { Text(stringResource(Res.string.about)) } },
            )
        },
        floatingActionButton = { AddTaskButton(onClick = onAddTask) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            error?.let { Text(it.text(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
            Text(
                text = stringResource(Res.string.pending_sync, state.pendingSyncOperations),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (!state.loading && state.tasks.isEmpty()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(Res.string.empty_inbox), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(Res.string.empty_inbox_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Room under the last row for the "+" button.
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                items(state.tasks, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        selected = task.id == selectedTaskId,
                        onOpen = { onOpenTask(task.id) },
                        onComplete = { viewModel.complete(task.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun TaskRow(task: Task, selected: Boolean, onOpen: () -> Unit, onComplete: () -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(background)
            .clickable(onClick = onOpen)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("task-row-${task.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val label = stringResource(Res.string.complete_task, task.content)
        Checkbox(
            checked = task.isCompleted,
            onCheckedChange = { if (it) onComplete() },
            modifier = Modifier.semantics { contentDescription = label },
        )
        Text(task.content, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun UiMessage.text(): String = when (this) {
    is UiMessage.DatabaseUnavailable -> stringResource(Res.string.database_unavailable, reason.orEmpty()).trim()
    UiMessage.BlankContent -> stringResource(Res.string.error_blank_content)
    is UiMessage.NotSaved -> stringResource(Res.string.error_not_saved, reason.orEmpty())
}
