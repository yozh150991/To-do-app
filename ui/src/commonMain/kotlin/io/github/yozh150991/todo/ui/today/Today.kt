package io.github.yozh150991.todo.ui.today

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.yozh150991.todo.core.domain.TaskRepository
import io.github.yozh150991.todo.core.model.Task
import io.github.yozh150991.todo.ui.inbox.UiMessage
import io.github.yozh150991.todo.ui.inbox.text
import io.github.yozh150991.todo.ui.quickadd.AddTaskButton
import io.github.yozh150991.todo.ui.quickadd.QuickAddContext
import io.github.yozh150991.todo.ui.resources.Res
import io.github.yozh150991.todo.ui.resources.empty_today
import io.github.yozh150991.todo.ui.resources.group_deadline_today
import io.github.yozh150991.todo.ui.resources.group_overdue
import io.github.yozh150991.todo.ui.resources.group_today
import io.github.yozh150991.todo.ui.resources.inbox_title
import io.github.yozh150991.todo.ui.resources.tab_today
import io.github.yozh150991.todo.ui.tasklist.CompletionSnackbars
import io.github.yozh150991.todo.ui.tasklist.DayLabel
import io.github.yozh150991.todo.ui.tasklist.DayTicker
import io.github.yozh150991.todo.ui.tasklist.ListEvent
import io.github.yozh150991.todo.ui.tasklist.ListHeader
import io.github.yozh150991.todo.ui.tasklist.TaskActions
import io.github.yozh150991.todo.ui.tasklist.TaskItem
import io.github.yozh150991.todo.ui.tasklist.TaskRow
import io.github.yozh150991.todo.ui.tasklist.text
import io.github.yozh150991.todo.ui.tasklist.weekdayName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import org.jetbrains.compose.resources.stringResource

/** Today's groups (UX_SPEC §3.1): overdue, today, and today's deadlines of tasks without a date. */
data class TodayGroups(val overdue: List<Task>, val today: List<Task>, val deadlineToday: List<Task>) {
    val isEmpty: Boolean get() = overdue.isEmpty() && today.isEmpty() && deadlineToday.isEmpty()

    companion object {
        /** [tasks] as `observeToday` returns them (date, timed first, time, priority, list order). */
        fun of(tasks: List<Task>, today: LocalDate): TodayGroups {
            val overdue = mutableListOf<Task>()
            val due = mutableListOf<Task>()
            val deadline = mutableListOf<Task>()
            for (task in tasks) {
                val date = task.due?.date
                when {
                    date == null -> if (task.deadline == today) deadline += task
                    date < today -> overdue += task
                    date == today -> due += task
                }
            }
            return TodayGroups(overdue, due, deadline)
        }
    }
}

data class TodayUiState(
    val today: LocalDate? = null,
    val groups: TodayGroups = TodayGroups(emptyList(), emptyList(), emptyList()),
    /** Project ID → name (the Inbox as null: the screen names it in the UI language). */
    val projectNames: Map<String, String?> = emptyMap(),
    val loading: Boolean = true,
    val unavailable: UiMessage? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val repository: TaskRepository, days: DayTicker) : ViewModel() {
    private val actions = TaskActions(repository, viewModelScope)
    val events: SharedFlow<ListEvent> = actions.events
    val error: StateFlow<UiMessage?> = actions.error

    val state: StateFlow<TodayUiState> = days.today
        .flatMapLatest { day ->
            combine(repository.observeToday(day), repository.projects.observeActive()) { tasks, projects ->
                TodayUiState(
                    today = day,
                    groups = TodayGroups.of(tasks, day),
                    projectNames = projects.associate { it.id to if (it.isInbox) null else it.name },
                    loading = false,
                )
            }
        }
        .catch { emit(TodayUiState(loading = false, unavailable = UiMessage.DatabaseUnavailable(it.message))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    fun toggle(task: Task) = actions.toggle(task)

    fun uncomplete(taskId: String) = actions.uncomplete(taskId)
}

/** Today (UX_SPEC §3.1): overdue first, then today, then today's deadlines; "+" adds for today. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    onAddTask: (QuickAddContext) -> Unit,
    onOpenTask: (String) -> Unit,
    modifier: Modifier = Modifier,
    selectedTaskId: String? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val failed by viewModel.error.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CompletionSnackbars(viewModel.events, snackbar, onUndo = viewModel::uncomplete)
    val today = state.today
    val inbox = stringResource(Res.string.inbox_title)

    Scaffold(
        modifier = modifier.testTag("screen-today"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(Res.string.tab_today))
                        if (today != null) {
                            Text(
                                "${weekdayName(today.dayOfWeek.isoDayNumber)}, ${DayLabel.shortDate(today, today).text()}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            AddTaskButton(onClick = { onAddTask(QuickAddContext(date = today, today = today)) })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            (state.unavailable ?: failed)?.let {
                Text(it.text(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (!state.loading && state.groups.isEmpty) {
                Text(
                    stringResource(Res.string.empty_today),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp).testTag("today-empty"),
                )
            }
            if (today == null) return@Column
            LazyColumn(Modifier.fillMaxSize().testTag("task-list"), contentPadding = PaddingValues(bottom = 88.dp)) {
                fun section(key: String, title: @Composable () -> String, tasks: List<Task>, error: Boolean = false) {
                    if (tasks.isEmpty()) return
                    item(key = "header-$key") {
                        ListHeader(title(), color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    }
                    items(tasks, key = { "$key-${it.id}" }) { task ->
                        TaskRow(
                            item = TaskItem(task),
                            today = today,
                            projectName = state.projectNames[task.projectId] ?: inbox,
                            selected = task.id == selectedTaskId,
                            onOpen = { onOpenTask(task.id) },
                            onToggle = { viewModel.toggle(task) },
                        )
                        HorizontalDivider()
                    }
                }
                section("overdue", { stringResource(Res.string.group_overdue) }, state.groups.overdue, error = true)
                section("today", { stringResource(Res.string.group_today) }, state.groups.today)
                section("deadline", { stringResource(Res.string.group_deadline_today) }, state.groups.deadlineToday)
            }
        }
    }
}
