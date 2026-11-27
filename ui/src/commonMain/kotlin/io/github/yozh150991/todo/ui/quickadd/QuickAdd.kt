package io.github.yozh150991.todo.ui.quickadd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.yozh150991.todo.core.domain.TaskRepository
import io.github.yozh150991.todo.core.model.Due
import io.github.yozh150991.todo.core.model.Task
import io.github.yozh150991.todo.core.model.ValidationCode
import io.github.yozh150991.todo.core.model.ValidationException
import io.github.yozh150991.todo.ui.inbox.UiMessage
import io.github.yozh150991.todo.ui.inbox.text
import io.github.yozh150991.todo.ui.resources.Res
import io.github.yozh150991.todo.ui.resources.add_task
import io.github.yozh150991.todo.ui.resources.add_task_fab
import io.github.yozh150991.todo.ui.resources.new_task_hint
import io.github.yozh150991.todo.ui.resources.quick_add_added
import io.github.yozh150991.todo.ui.resources.quick_add_for_date
import io.github.yozh150991.todo.ui.resources.quick_add_for_today
import io.github.yozh150991.todo.ui.resources.quick_add_open
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/**
 * Where Quick Add was opened from (UX_SPEC §2.1): in a project — that project, in Today — today's
 * date, in a day of Upcoming — that day. Null fields mean the defaults (Inbox, no date).
 */
data class QuickAddContext(
    val projectId: String? = null,
    val date: LocalDate? = null,
    /** Today's date when the sheet opened, to say "For today" instead of the date. */
    val today: LocalDate? = null,
)

data class QuickAddUiState(
    /** Null while the sheet is closed. */
    val context: QuickAddContext? = null,
    val draft: String = "",
    val lastAdded: Task? = null,
    val error: UiMessage? = null,
)

/**
 * Quick Add sheet state (UX_SPEC §3.4). Lives as long as the window, so an unsent draft survives
 * closing the sheet and rotating the screen until the app is closed. Ф1a PR 6b adds the parser
 * preview and chips on top of this; here the line becomes the title as typed.
 */
class QuickAddViewModel(private val repository: TaskRepository) : ViewModel() {
    private val _state = MutableStateFlow(QuickAddUiState())
    val state: StateFlow<QuickAddUiState> = _state.asStateFlow()

    fun open(context: QuickAddContext) = _state.update { it.copy(context = context, lastAdded = null, error = null) }

    fun close() = _state.update { it.copy(context = null, lastAdded = null, error = null) }

    fun updateDraft(text: String) = _state.update { it.copy(draft = text, error = null) }

    /** Creates the task and keeps the sheet open for the next one, with an empty field. */
    fun add() {
        val current = _state.value
        val context = current.context ?: return
        if (current.draft.isBlank()) return
        viewModelScope.launch {
            try {
                val task = repository.createTask(
                    content = current.draft,
                    projectId = context.projectId,
                    due = context.date?.let { Due(it) },
                )
                _state.update { it.copy(draft = "", lastAdded = task, error = null) }
            } catch (e: ValidationException) {
                val message = if (e.code == ValidationCode.BLANK_CONTENT) UiMessage.BlankContent else UiMessage.NotSaved(e.message)
                _state.update { it.copy(error = message) }
            } catch (e: IllegalStateException) {
                _state.update { it.copy(error = UiMessage.DatabaseUnavailable(e.message)) }
            }
        }
    }
}

/** The "+" button of every task list (UX_SPEC §2.1). */
@Composable
fun AddTaskButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(Res.string.add_task_fab)
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.testTag("fab-add").semantics { contentDescription = label },
    ) {
        Text("+", style = MaterialTheme.typography.headlineMedium)
    }
}

/**
 * The Quick Add sheet while [QuickAddViewModel] has a context. `Enter` or "Add" creates the task
 * and the sheet stays open; swipe down, Back, `Esc` or a tap outside closes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddHost(viewModel: QuickAddViewModel, onOpenTask: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = state.context ?: return
    ModalBottomSheet(
        onDismissRequest = viewModel::close,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("quick-add"),
    ) {
        QuickAddContent(
            state = state,
            context = context,
            onDraftChange = viewModel::updateDraft,
            onAdd = viewModel::add,
            onOpen = { id ->
                viewModel.close()
                onOpenTask(id)
            },
        )
    }
}

@Composable
internal fun QuickAddContent(
    state: QuickAddUiState,
    context: QuickAddContext,
    onDraftChange: (String) -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // The field exists after the first frame of the sheet; focus brings up the keyboard.
        withFrameNanos { }
        runCatching { focus.requestFocus() }
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp).imePadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        context.date?.let { date ->
            val text = if (date == context.today) {
                stringResource(Res.string.quick_add_for_today)
            } else {
                stringResource(Res.string.quick_add_for_date, date.toString())
            }
            Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f).focusRequester(focus).testTag("quick-add-field"),
                singleLine = true,
                placeholder = { Text(stringResource(Res.string.new_task_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAdd() }),
            )
            Button(onClick = onAdd, enabled = state.draft.isNotBlank(), modifier = Modifier.testTag("quick-add-submit")) {
                Text(stringResource(Res.string.add_task))
            }
        }
        state.error?.let { Text(it.text(), color = MaterialTheme.colorScheme.error) }
        state.lastAdded?.let { task ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.quick_add_added, task.content),
                    modifier = Modifier.weight(1f).testTag("quick-add-added"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { onOpen(task.id) }) { Text(stringResource(Res.string.quick_add_open)) }
            }
        }
    }
}
