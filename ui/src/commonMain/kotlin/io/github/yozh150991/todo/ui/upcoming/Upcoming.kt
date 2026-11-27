package io.github.yozh150991.todo.ui.upcoming

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import io.github.yozh150991.todo.ui.resources.empty_upcoming
import io.github.yozh150991.todo.ui.resources.inbox_title
import io.github.yozh150991.todo.ui.resources.tab_upcoming
import io.github.yozh150991.todo.ui.resources.upcoming_add_for_day
import io.github.yozh150991.todo.ui.tasklist.CompletionSnackbars
import io.github.yozh150991.todo.ui.tasklist.DayLabel
import io.github.yozh150991.todo.ui.tasklist.DayTicker
import io.github.yozh150991.todo.ui.tasklist.ListEvent
import io.github.yozh150991.todo.ui.tasklist.TaskActions
import io.github.yozh150991.todo.ui.tasklist.TaskItem
import io.github.yozh150991.todo.ui.tasklist.TaskRow
import io.github.yozh150991.todo.ui.tasklist.text
import io.github.yozh150991.todo.ui.tasklist.weekdayName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.stringResource

/** One day of Upcoming with its tasks; empty days are shown too (owner's decision, plan Ф1a §8). */
data class UpcomingDay(val date: LocalDate, val tasks: List<Task>)

data class UpcomingUiState(
    val today: LocalDate? = null,
    val days: List<UpcomingDay> = emptyList(),
    val projectNames: Map<String, String?> = emptyMap(),
    val loading: Boolean = true,
    val unavailable: UiMessage? = null,
) {
    val isEmpty: Boolean get() = days.all { it.tasks.isEmpty() }
}

/**
 * Upcoming (UX_SPEC §3.1): every day from tomorrow on, one after another, with its tasks. The list
 * is endless: it starts with [INITIAL_DAYS] days and grows by [MORE_DAYS] as the user scrolls,
 * querying only the dates on screen so far (plan Ф1a §6).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UpcomingViewModel(private val repository: TaskRepository, days: DayTicker) : ViewModel() {
    private val actions = TaskActions(repository, viewModelScope)
    val events: SharedFlow<ListEvent> = actions.events
    val error: StateFlow<UiMessage?> = actions.error
    private val horizon = MutableStateFlow(INITIAL_DAYS)

    val state: StateFlow<UpcomingUiState> = combine(days.today, horizon) { day, count -> day to count }
        .distinctUntilChanged()
        .flatMapLatest { (today, count) ->
            val first = today.plus(1, DateTimeUnit.DAY)
            val last = first.plus(count - 1, DateTimeUnit.DAY)
            combine(repository.observeDueBetween(first, last), repository.projects.observeActive()) { tasks, projects ->
                UpcomingUiState(
                    today = today,
                    days = daysOf(first, count, tasks),
                    projectNames = projects.associate { it.id to if (it.isInbox) null else it.name },
                    loading = false,
                )
            }
        }
        .catch { emit(UpcomingUiState(loading = false, unavailable = UiMessage.DatabaseUnavailable(it.message))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpcomingUiState())

    /** Loads [MORE_DAYS] more days at the end of the list, up to [MAX_DAYS]. */
    fun loadMore() = horizon.update { (it + MORE_DAYS).coerceAtMost(MAX_DAYS) }

    fun toggle(task: Task) = actions.toggle(task)

    fun uncomplete(taskId: String) = actions.uncomplete(taskId)

    companion object {
        const val INITIAL_DAYS = 60
        const val MORE_DAYS = 60

        /** About ten years ahead; further dates stay reachable through projects and search. */
        const val MAX_DAYS = 3660

        /** [count] consecutive days from [first], each with its tasks ([tasks] sorted by date). */
        fun daysOf(first: LocalDate, count: Int, tasks: List<Task>): List<UpcomingDay> {
            val byDate = tasks.groupBy { it.due?.date }
            return (0 until count).map { offset ->
                val date = first.plus(offset, DateTimeUnit.DAY)
                UpcomingDay(date, byDate[date].orEmpty())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpcomingScreen(
    viewModel: UpcomingViewModel,
    onAddTask: (QuickAddContext) -> Unit,
    onOpenTask: (String) -> Unit,
    modifier: Modifier = Modifier,
    selectedTaskId: String? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val failed by viewModel.error.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CompletionSnackbars(viewModel.events, snackbar, onUndo = viewModel::uncomplete)
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val inbox = stringResource(Res.string.inbox_title)
    val today = state.today

    // List index of each day's header, for the week strip.
    val headerIndex: Map<LocalDate, Int> = remember(state.days) {
        var index = 0
        buildMap {
            state.days.forEach { day ->
                put(day.date, index)
                index += 1 + day.tasks.size
            }
        }
    }

    // Endless list: near the end, ask for more days.
    LaunchedEffect(list, viewModel) {
        var requestedAt = -1
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index to list.layoutInfo.totalItemsCount }
            .collect { (last, total) ->
                // Once per list length: the request is answered by a longer list.
                if (last != null && total > 0 && last >= total - 20 && total != requestedAt) {
                    requestedAt = total
                    viewModel.loadMore()
                }
            }
    }

    Scaffold(
        modifier = modifier.testTag("screen-upcoming"),
        topBar = { TopAppBar(title = { Text(stringResource(Res.string.tab_upcoming)) }) },
        floatingActionButton = { AddTaskButton(onClick = { onAddTask(QuickAddContext(today = today)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            (state.unavailable ?: failed)?.let {
                Text(it.text(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (today == null) return@Column
            WeekStrip(
                days = state.days.take(7).map { it.date },
                busy = state.days.take(7).filter { it.tasks.isNotEmpty() }.map { it.date }.toSet(),
                onDay = { date -> headerIndex[date]?.let { scope.launch { list.animateScrollToItem(it) } } },
            )
            HorizontalDivider()
            if (!state.loading && state.isEmpty) {
                Text(
                    stringResource(Res.string.empty_upcoming),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("upcoming-empty"),
                )
            }
            LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("task-list"), contentPadding = PaddingValues(bottom = 88.dp)) {
                state.days.forEach { day ->
                    item(key = "day-${day.date}") {
                        DayHeader(day.date, today, onAdd = { onAddTask(QuickAddContext(date = day.date, today = today)) })
                    }
                    items(day.tasks, key = { "${day.date}-${it.id}" }) { task ->
                        TaskRow(
                            item = TaskItem(task),
                            today = today,
                            projectName = state.projectNames[task.projectId] ?: inbox,
                            selected = task.id == selectedTaskId,
                            onOpen = { onOpenTask(task.id) },
                            onToggle = { viewModel.toggle(task) },
                        )
                    }
                }
            }
        }
    }
}

/** "Tomorrow · Sat 22 Nov"; a tap opens Quick Add for that day (plan Ф1a PR 3). */
@Composable
private fun DayHeader(date: LocalDate, today: LocalDate, onAdd: () -> Unit) {
    val label = DayLabel.of(date, today)
    val dateText = "${weekdayName(date.dayOfWeek.isoDayNumber)} ${DayLabel.shortDate(date, today).text()}"
    val text = if (label is DayLabel.Weekday || label is DayLabel.Date) dateText else "${label.text()} · $dateText"
    val spoken = stringResource(Res.string.upcoming_add_for_day, dateText)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onAdd)
            .semantics { contentDescription = spoken }
            .padding(horizontal = 16.dp)
            .testTag("day-$date"),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp).semantics { heading() },
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** The next seven days (UX_SPEC §3.1 «смуга тижня»); a tap scrolls the list to that day. */
@Composable
private fun WeekStrip(days: List<LocalDate>, busy: Set<LocalDate>, onDay: (LocalDate) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEach { date ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).clickable { onDay(date) }.padding(vertical = 4.dp).testTag("week-$date"),
            ) {
                Text(weekdayName(date.dayOfWeek.isoDayNumber), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                Text(
                    date.day.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (date in busy) FontWeight.Bold else FontWeight.Normal,
                    color = if (date in busy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
