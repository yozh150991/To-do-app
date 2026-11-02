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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yozh150991.todo.core.model.Task
import io.github.yozh150991.todo.ui.Strings

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
        topBar = { TopAppBar(title = { Text(Strings.inboxTitle) }) },
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
                    placeholder = { Text(Strings.newTaskHint) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = ::submit, enabled = draft.isNotBlank()) { Text(Strings.add) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text(
                text = Strings.pendingSync(state.pendingSyncOperations),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!state.loading && state.tasks.isEmpty()) {
                Text(Strings.emptyInbox, modifier = Modifier.padding(top = 24.dp))
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
        Checkbox(checked = task.isCompleted, onCheckedChange = { if (it) onComplete() })
        Text(task.content, style = MaterialTheme.typography.bodyLarge)
    }
}
