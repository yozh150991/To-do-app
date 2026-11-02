package io.github.yozh150991.todo.ui.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yozh150991.todo.core.model.Task
import io.github.yozh150991.todo.ui.resources.Res
import io.github.yozh150991.todo.ui.resources.add_task
import io.github.yozh150991.todo.ui.resources.complete_task
import io.github.yozh150991.todo.ui.resources.database_unavailable
import io.github.yozh150991.todo.ui.resources.empty_inbox
import io.github.yozh150991.todo.ui.resources.error_blank_content
import io.github.yozh150991.todo.ui.resources.error_not_saved
import io.github.yozh150991.todo.ui.resources.inbox_title
import io.github.yozh150991.todo.ui.resources.new_task_hint
import io.github.yozh150991.todo.ui.resources.pending_sync
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(viewModel: InboxViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }

    fun submit() {
        if (draft.isNotBlank()) {
            viewModel.addTask(draft)
            draft = ""
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(Res.string.inbox_title)) }) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text(stringResource(Res.string.new_task_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = ::submit, enabled = draft.isNotBlank()) { Text(stringResource(Res.string.add_task)) }
            }
            error?.let { Text(it.text(), color = MaterialTheme.colorScheme.error) }
            Text(
                text = stringResource(Res.string.pending_sync, state.pendingSyncOperations),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!state.loading && state.tasks.isEmpty()) {
                Text(stringResource(Res.string.empty_inbox), modifier = Modifier.padding(top = 24.dp))
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.tasks, key = { it.id }) { task ->
                    TaskRow(task = task, onComplete = { viewModel.complete(task.id) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun TaskRow(task: Task, onComplete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val label = stringResource(Res.string.complete_task, task.content)
        Checkbox(
            checked = task.isCompleted,
            onCheckedChange = { if (it) onComplete() },
            modifier = Modifier.semantics { contentDescription = label },
        )
        Text(task.content, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun UiMessage.text(): String = when (this) {
    is UiMessage.DatabaseUnavailable -> stringResource(Res.string.database_unavailable, reason.orEmpty()).trim()
    UiMessage.BlankContent -> stringResource(Res.string.error_blank_content)
    is UiMessage.NotSaved -> stringResource(Res.string.error_not_saved, reason.orEmpty())
}
