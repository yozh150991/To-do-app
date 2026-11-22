package io.github.yozh150991.todo.ui.task

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.yozh150991.todo.core.domain.FieldEdit
import io.github.yozh150991.todo.core.domain.TaskEdit
import io.github.yozh150991.todo.core.domain.TaskRepository
import io.github.yozh150991.todo.core.model.CivilResolution
import io.github.yozh150991.todo.core.model.CivilTimeOrigin
import io.github.yozh150991.todo.core.model.Due
import io.github.yozh150991.todo.core.model.Priority
import io.github.yozh150991.todo.core.model.Project
import io.github.yozh150991.todo.core.model.Task
import io.github.yozh150991.todo.core.model.TimeZones
import io.github.yozh150991.todo.core.model.ValidationCode
import io.github.yozh150991.todo.core.model.ValidationException
import io.github.yozh150991.todo.core.model.resolve
import io.github.yozh150991.todo.ui.inbox.UiMessage
import io.github.yozh150991.todo.ui.inbox.text
import io.github.yozh150991.todo.ui.resources.Res
import io.github.yozh150991.todo.ui.resources.cancel
import io.github.yozh150991.todo.ui.resources.close
import io.github.yozh150991.todo.ui.resources.complete_task
import io.github.yozh150991.todo.ui.resources.ic_deadline
import io.github.yozh150991.todo.ui.resources.ic_delete
import io.github.yozh150991.todo.ui.resources.ic_priority
import io.github.yozh150991.todo.ui.resources.ic_project
import io.github.yozh150991.todo.ui.resources.ic_schedule
import io.github.yozh150991.todo.ui.resources.ic_time
import io.github.yozh150991.todo.ui.resources.inbox_title
import io.github.yozh150991.todo.ui.resources.task_add_deadline
import io.github.yozh150991.todo.ui.resources.task_add_time
import io.github.yozh150991.todo.ui.resources.task_completed
import io.github.yozh150991.todo.ui.resources.task_deadline
import io.github.yozh150991.todo.ui.resources.task_delete
import io.github.yozh150991.todo.ui.resources.task_description_hint
import io.github.yozh150991.todo.ui.resources.task_due
import io.github.yozh150991.todo.ui.resources.task_no_date
import io.github.yozh150991.todo.ui.resources.task_not_found
import io.github.yozh150991.todo.ui.resources.task_priority
import io.github.yozh150991.todo.ui.resources.task_project
import io.github.yozh150991.todo.ui.resources.task_remove_date
import io.github.yozh150991.todo.ui.resources.task_remove_deadline
import io.github.yozh150991.todo.ui.resources.task_remove_time
import io.github.yozh150991.todo.ui.resources.task_time
import io.github.yozh150991.todo.ui.resources.task_time_gap
import io.github.yozh150991.todo.ui.resources.task_title_hint
import io.github.yozh150991.todo.ui.resources.uncomplete_task
import io.github.yozh150991.todo.ui.tasklist.ClearIcon
import io.github.yozh150991.todo.ui.tasklist.DateDialog
import io.github.yozh150991.todo.ui.tasklist.DayLabel
import io.github.yozh150991.todo.ui.tasklist.DayTicker
import io.github.yozh150991.todo.ui.tasklist.LocalDetailBesideList
import io.github.yozh150991.todo.ui.tasklist.ScheduleSheet
import io.github.yozh150991.todo.ui.tasklist.TimeDialog
import io.github.yozh150991.todo.ui.tasklist.UndoCenter
import io.github.yozh150991.todo.ui.tasklist.UndoSnackbars
import io.github.yozh150991.todo.ui.tasklist.UndoText
import io.github.yozh150991.todo.ui.tasklist.clock
import io.github.yozh150991.todo.ui.tasklist.deadlineDaysLeft
import io.github.yozh150991.todo.ui.tasklist.deadlineText
import io.github.yozh150991.todo.ui.tasklist.priorityColor
import io.github.yozh150991.todo.ui.tasklist.projectTree
import io.github.yozh150991.todo.ui.tasklist.text
import io.github.yozh150991.todo.ui.tasklist.zoneCity
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

sealed interface TaskDetailUiState {
    data object Loading : TaskDetailUiState

    /** The task does not exist or is in the bin (a stale notification, a deep link to a deleted task). */
    data object NotFound : TaskDetailUiState

    data class Loaded(
        val task: Task,
        val project: Project?,
        /** Where the task can move: the Inbox first, then the active projects in tree order. */
        val projects: List<Pair<Project, Int>> = emptyList(),
        val today: LocalDate? = null,
    ) : TaskDetailUiState
}

/**
 * A due time that does not exist on its day — the clocks go forward (TIME-08). Nothing is saved;
 * the details offer [choices] instead of shifting the time silently.
 */
data class TimeGap(val due: Due, val choices: List<LocalTime>)

/**
 * Task details (UX_SPEC §3.3). Every change is saved at once as its own operation, without a
 * "Save" button, and offered for Undo; Undo writes the old values back as a new command.
 */
class TaskDetailViewModel(
    private val taskId: String,
    private val repository: TaskRepository,
    val undo: UndoCenter = UndoCenter(),
    days: DayTicker? = null,
    /** The zone of floating due times, for the DST check (TIME-08); the repository checks again. */
    private val deviceZone: () -> TimeZone = { TimeZones.systemDefault() },
) : ViewModel() {
    private val today: Flow<LocalDate> = days?.today ?: flow { emit(Clock.System.todayIn(deviceZone())) }

    val state: StateFlow<TaskDetailUiState> =
        combine(repository.observeTask(taskId), repository.projects.observeActive(), today) { task, projects, day ->
            if (task == null || task.isDeleted) {
                TaskDetailUiState.NotFound
            } else {
                val (inbox, others) = projects.partition { it.isInbox }
                TaskDetailUiState.Loaded(
                    task = task,
                    project = projects.firstOrNull { it.id == task.projectId } ?: repository.projects.get(task.projectId),
                    projects = projectTree(inbox) + projectTree(others),
                    today = day,
                )
            }
        }
            .catch { emit(TaskDetailUiState.NotFound) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskDetailUiState.Loading)

    private val _error = MutableStateFlow<UiMessage?>(null)
    val error: StateFlow<UiMessage?> = _error.asStateFlow()

    private val _timeGap = MutableStateFlow<TimeGap?>(null)
    val timeGap: StateFlow<TimeGap?> = _timeGap.asStateFlow()

    /** A name typed in the details; blank is refused with "A task needs a name" (UX_SPEC §3.3). */
    fun rename(content: String) = save {
        edit(UndoText::Updated) { task ->
            if (content.isBlank()) throw ValidationException("blank", ValidationCode.BLANK_CONTENT)
            if (content.trim() == task.content) null else TaskEdit(content = FieldEdit.Set(content))
        }
    }

    fun describe(description: String) = save {
        edit(UndoText::Updated) { task -> if (description == task.description) null else TaskEdit(description = FieldEdit.Set(description)) }
    }

    fun setPriority(priority: Priority) = save {
        edit(UndoText::Updated) { task -> if (priority == task.priority) null else TaskEdit(priority = FieldEdit.Set(priority)) }
    }

    /** A new date keeping the time and zone; null removes the date (and with it the time). */
    fun schedule(date: LocalDate?) = save {
        val task = live() ?: return@save
        saveDue(task, date?.let { Due(it, task.due?.time, task.due?.zoneId) }, UndoText::Scheduled)
    }

    /** A time on the task's date; null makes it all-day again (an all-day date has no zone). */
    fun setTime(time: LocalTime?) = save {
        val task = live() ?: return@save
        val due = task.due ?: return@save
        saveDue(task, Due(due.date, time, if (time == null) null else due.zoneId), UndoText::Updated)
    }

    /** One of the times offered for a time in a DST gap (TIME-08). */
    fun chooseGapTime(time: LocalTime) = save {
        val gap = _timeGap.value ?: return@save
        val task = live() ?: return@save
        saveDue(task, gap.due.copy(time = time), if (gap.due.date == task.due?.date) UndoText::Updated else UndoText::Scheduled)
    }

    fun dismissTimeGap() {
        _timeGap.value = null
    }

    fun setDeadline(deadline: LocalDate?) = save {
        edit(UndoText::Updated) { task -> if (deadline == task.deadline) null else TaskEdit(deadline = FieldEdit.Set(deadline)) }
    }

    /** To the top level of another project with its sub-tasks (plan Ф1a §5); Undo puts it back exactly. */
    fun moveTo(projectId: String) = save {
        val task = live() ?: return@save
        if (task.projectId == projectId) return@save
        val move = repository.moveTask(taskId, projectId = projectId) ?: return@save
        val name = repository.projects.get(projectId)?.takeUnless { it.isInbox }?.name
        _error.value = null
        undo.post(UndoText.Moved(task.content, name)) { repository.undoMove(move) }
    }

    fun toggleComplete() = save {
        val task = live() ?: return@save
        if (task.isCompleted) {
            repository.uncomplete(taskId)
        } else if (repository.complete(taskId)) {
            undo.post(UndoText.Completed(task.content)) { repository.uncomplete(taskId) }
        }
        _error.value = null
    }

    /** To the bin with the sub-tasks; the details close and the list offers Undo. */
    fun delete() = save {
        val task = live() ?: return@save
        if (repository.delete(taskId)) undo.post(UndoText.Binned(task.content)) { repository.restore(taskId) }
    }

    private suspend fun live(): Task? = repository.getTask(taskId)?.takeUnless { it.isDeleted }

    private suspend fun saveDue(task: Task, due: Due?, text: (String) -> UndoText) {
        val resolution = due?.resolve(deviceZone(), CivilTimeOrigin.EXPLICIT)
        if (due != null && resolution is CivilResolution.Nonexistent) {
            _timeGap.value = TimeGap(due, resolution.choices.map { it.time })
            return
        }
        _timeGap.value = null
        edit(text) { current -> if (due == current.due) null else TaskEdit(due = FieldEdit.Set(due)) }
    }

    /** Applies the edit [change] builds from the current task and offers Undo with the values it replaced. */
    private suspend fun edit(text: (String) -> UndoText, change: (Task) -> TaskEdit?) {
        val before = live() ?: return
        val edit = change(before) ?: return
        if (repository.updateTask(taskId, edit)) {
            _error.value = null
            undo.post(text(before.content)) { repository.updateTask(taskId, edit.revertTo(before)) }
        }
    }

    /** The user is typing again: the reason a name was refused goes away. */
    fun clearError() {
        _error.value = null
    }

    /**
     * Saves outside the screen's lifetime: a name typed just before Back or "Close" is still
     * written (the view model is cleared as the details close). An error stays until a change
     * succeeds — a save that changes nothing (leaving a field untouched) does not hide it.
     */
    private fun save(block: suspend () -> Unit) {
        viewModelScope.launch(NonCancellable) {
            try {
                block()
            } catch (e: ValidationException) {
                _error.value = if (e.code == ValidationCode.BLANK_CONTENT) UiMessage.BlankContent else UiMessage.NotSaved(e.message)
            } catch (e: IllegalStateException) {
                _error.value = UiMessage.DatabaseUnavailable(e.message)
            }
        }
    }
}

/** The edit that puts back what [this] changed, with the values of [before]. */
internal fun TaskEdit.revertTo(before: Task): TaskEdit = TaskEdit(
    content = content.ifSet(before.content),
    description = description.ifSet(before.description),
    priority = priority.ifSet(before.priority),
    due = due.ifSet(before.due),
    deadline = deadline.ifSet(before.deadline),
    durationMinutes = durationMinutes.ifSet(before.durationMinutes),
    durationDays = durationDays.ifSet(before.durationDays),
)

private fun <T> FieldEdit<T>.ifSet(old: T): FieldEdit<T> = if (this is FieldEdit.Set) FieldEdit.Set(old) else FieldEdit.Keep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(viewModel: TaskDetailViewModel, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // Next to a list, the list shows the snackbars; on their own the details do.
    val ownSnackbars = !LocalDetailBesideList.current
    if (ownSnackbars) UndoSnackbars(viewModel.undo, snackbar)
    Scaffold(
        modifier = modifier.testTag("screen-task"),
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    if (state is TaskDetailUiState.Loaded) {
                        IconButton(
                            onClick = {
                                viewModel.delete()
                                onClose()
                            },
                            modifier = Modifier.testTag("task-delete"),
                        ) {
                            Icon(painterResource(Res.drawable.ic_delete), contentDescription = stringResource(Res.string.task_delete))
                        }
                    }
                    TextButton(onClick = onClose, modifier = Modifier.testTag("task-close")) { Text(stringResource(Res.string.close)) }
                },
            )
        },
        snackbarHost = { if (ownSnackbars) SnackbarHost(snackbar) },
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
            is TaskDetailUiState.Loaded -> TaskEditor(s, viewModel, content.verticalScroll(rememberScrollState()))
        }
    }
}

@Composable
private fun TaskEditor(state: TaskDetailUiState.Loaded, viewModel: TaskDetailViewModel, modifier: Modifier) {
    val task = state.task
    val today = state.today ?: remember { Clock.System.todayIn(TimeZones.systemDefault()) }
    val error by viewModel.error.collectAsStateWithLifecycle()
    val gap by viewModel.timeGap.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current

    var title by remember(task.id) { mutableStateOf(task.content) }
    var titleFocused by remember { mutableStateOf(false) }
    var description by remember(task.id) { mutableStateOf(task.description) }
    var descriptionFocused by remember { mutableStateOf(false) }
    // Changes from elsewhere (Undo, another screen) show unless the field is being edited.
    LaunchedEffect(task.content) { if (!titleFocused) title = task.content }
    LaunchedEffect(task.description) { if (!descriptionFocused) description = task.description }

    fun commitTitle() {
        viewModel.rename(title)
        // A blank name is not saved: the field gets the old one back (UX_SPEC §3.3).
        if (title.isBlank()) title = task.content
    }

    // Back and "Close" save what is typed, like leaving the field does.
    val latestTitle by rememberUpdatedState(title)
    val latestDescription by rememberUpdatedState(description)
    DisposableEffect(task.id) {
        onDispose {
            if (latestTitle.isNotBlank()) viewModel.rename(latestTitle)
            viewModel.describe(latestDescription)
        }
    }

    var scheduling by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    var pickingDeadline by remember { mutableStateOf(false) }
    var priorityMenu by remember { mutableStateOf(false) }
    var projectMenu by remember { mutableStateOf(false) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            val color = priorityColor(task.priority)
            val checkboxLabel = stringResource(if (task.isCompleted) Res.string.uncomplete_task else Res.string.complete_task, task.content)
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { viewModel.toggleComplete() },
                colors = CheckboxDefaults.colors(checkedColor = color, uncheckedColor = color),
                modifier = Modifier.semantics { contentDescription = checkboxLabel }.testTag("task-detail-complete"),
            )
            BasicTextField(
                value = title,
                onValueChange = { value ->
                    // A name is one line: Enter saves it, as "Done" on the keyboard does.
                    if ('\n' in value) {
                        title = value.replace("\n", "")
                        commitTitle()
                        focus.clearFocus()
                    } else {
                        title = value
                        viewModel.clearError()
                    }
                },
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    commitTitle()
                    focus.clearFocus()
                }),
                decorationBox = { field ->
                    Box {
                        if (title.isEmpty()) {
                            Text(stringResource(Res.string.task_title_hint), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        field()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 8.dp)
                    .onFocusChanged { focusState ->
                        if (titleFocused && !focusState.isFocused) commitTitle()
                        titleFocused = focusState.isFocused
                    }
                    .testTag("task-detail-title"),
            )
        }
        error?.let {
            Text(it.text(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 48.dp).testTag("task-detail-error"))
        }
        BasicTextField(
            value = description,
            onValueChange = { description = it },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            decorationBox = { field ->
                Box {
                    if (description.isEmpty()) {
                        Text(stringResource(Res.string.task_description_hint), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    field()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 48.dp, top = 4.dp, bottom = 8.dp)
                .onFocusChanged { focusState ->
                    if (descriptionFocused && !focusState.isFocused) viewModel.describe(description)
                    descriptionFocused = focusState.isFocused
                }
                .testTag("task-detail-description"),
        )
        HorizontalDivider()

        val due = task.due
        FieldRow(
            icon = Res.drawable.ic_schedule,
            text = if (due == null) stringResource(Res.string.task_no_date) else stringResource(Res.string.task_due, DayLabel.of(due.date, today).text()),
            tag = "field-date",
            onClick = { scheduling = true },
            clear = if (due == null) null else ClearAction(stringResource(Res.string.task_remove_date), "clear-date") { viewModel.schedule(null) },
        )
        if (due != null) {
            val time = due.time
            FieldRow(
                icon = Res.drawable.ic_time,
                text = if (time == null) {
                    stringResource(Res.string.task_add_time)
                } else {
                    val zone = due.zoneId?.takeIf { it != TimeZones.systemDefault().id }?.let { " (${zoneCity(it)})" }.orEmpty()
                    stringResource(Res.string.task_time, clock(time) + zone)
                },
                tag = "field-time",
                onClick = { pickingTime = true },
                clear = if (time == null) null else ClearAction(stringResource(Res.string.task_remove_time), "clear-time") { viewModel.setTime(null) },
            )
        }
        gap?.let { TimeGapNotice(it, onChoose = viewModel::chooseGapTime, onDismiss = viewModel::dismissTimeGap) }

        val deadline = task.deadline
        FieldRow(
            icon = Res.drawable.ic_deadline,
            text = if (deadline == null) {
                stringResource(Res.string.task_add_deadline)
            } else {
                // The date itself, then what is left: "Nov 30 · 9 days".
                stringResource(Res.string.task_deadline, "${DayLabel.shortDate(deadline, today).text()} · ${deadlineText(deadlineDaysLeft(deadline, today))}")
            },
            tag = "field-deadline",
            onClick = { pickingDeadline = true },
            clear = if (deadline == null) null else ClearAction(stringResource(Res.string.task_remove_deadline), "clear-deadline") { viewModel.setDeadline(null) },
        )

        Box {
            FieldRow(
                icon = Res.drawable.ic_priority,
                tint = priorityColor(task.priority),
                text = stringResource(Res.string.task_priority, task.priority.level),
                tag = "field-priority",
                onClick = { priorityMenu = true },
            )
            DropdownMenu(expanded = priorityMenu, onDismissRequest = { priorityMenu = false }) {
                Priority.entries.forEach { priority ->
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.task_priority, priority.level)) },
                        leadingIcon = { Icon(painterResource(Res.drawable.ic_priority), contentDescription = null, tint = priorityColor(priority)) },
                        onClick = {
                            priorityMenu = false
                            viewModel.setPriority(priority)
                        },
                        modifier = Modifier.testTag("priority-${priority.level}"),
                    )
                }
            }
        }

        Box {
            val inbox = stringResource(Res.string.inbox_title)
            val project = state.project
            FieldRow(
                icon = Res.drawable.ic_project,
                text = stringResource(Res.string.task_project, if (project == null) "—" else if (project.isInbox) inbox else project.name),
                tag = "field-project",
                onClick = { projectMenu = true },
            )
            DropdownMenu(expanded = projectMenu, onDismissRequest = { projectMenu = false }) {
                state.projects.forEach { (candidate, depth) ->
                    DropdownMenuItem(
                        text = { Text(if (candidate.isInbox) inbox else candidate.name, modifier = Modifier.padding(start = 16.dp * depth)) },
                        onClick = {
                            projectMenu = false
                            viewModel.moveTo(candidate.id)
                        },
                        modifier = Modifier.testTag("move-to-${candidate.id}"),
                    )
                }
            }
        }
        if (task.isCompleted) {
            Text(stringResource(Res.string.task_completed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (scheduling) ScheduleSheet(task, today, onPick = viewModel::schedule, onDismiss = { scheduling = false })
    if (pickingTime) {
        TimeDialog(
            initial = task.due?.time ?: LocalTime(9, 0),
            onPick = {
                pickingTime = false
                viewModel.setTime(it)
            },
            onDismiss = { pickingTime = false },
        )
    }
    if (pickingDeadline) {
        DateDialog(
            initial = task.deadline ?: today,
            onPick = {
                pickingDeadline = false
                viewModel.setDeadline(it)
            },
            onDismiss = { pickingDeadline = false },
        )
    }
}

private class ClearAction(val description: String, val tag: String, val onClick: () -> Unit)

/** One field of the details: an icon, its value (or what to add), and "×" to clear it. 48 dp high. */
@Composable
private fun FieldRow(
    icon: DrawableResource,
    text: String,
    tag: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    clear: ClearAction? = null,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).heightIn(min = 48.dp).clickable(onClick = onClick).testTag(tag),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.padding(start = 12.dp).size(20.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
        }
        if (clear != null) {
            IconButton(onClick = clear.onClick, modifier = Modifier.testTag(clear.tag)) { ClearIcon(clear.description) }
        }
    }
}

/** TIME-08 (UX_SPEC §3.3): the time does not exist that day; pick one of the nearest ones. */
@Composable
private fun TimeGapNotice(gap: TimeGap, onChoose: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    Column(Modifier.padding(start = 48.dp, bottom = 8.dp).testTag("time-gap"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(Res.string.task_time_gap, gap.due.time?.let(::clock).orEmpty()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            gap.choices.forEach { choice ->
                OutlinedButton(onClick = { onChoose(choice) }, modifier = Modifier.testTag("gap-${clock(choice)}")) { Text(clock(choice)) }
            }
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        }
    }
}
