package io.github.yozh150991.todo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.yozh150991.todo.core.database.LocalStore
import io.github.yozh150991.todo.core.database.LocalStoreProvider
import io.github.yozh150991.todo.core.database.openAppDatabase
import io.github.yozh150991.todo.core.domain.TaskRepository
import io.github.yozh150991.todo.core.model.Due
import io.github.yozh150991.todo.core.model.TimeZones
import io.github.yozh150991.todo.ui.inbox.InboxScreen
import io.github.yozh150991.todo.ui.inbox.InboxViewModel
import io.github.yozh150991.todo.ui.quickadd.QuickAddContext
import io.github.yozh150991.todo.ui.tasklist.DayTicker
import io.github.yozh150991.todo.ui.today.TodayScreen
import io.github.yozh150991.todo.ui.today.TodayViewModel
import io.github.yozh150991.todo.ui.upcoming.UpcomingScreen
import io.github.yozh150991.todo.ui.upcoming.UpcomingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import java.io.File
import java.nio.file.Files
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.measureTime

/** Today, Upcoming and the Inbox list with a real database (plan Ф1a PR 3), in English. */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class ListScreensTest {
    private val dir: File = Files.createTempDirectory("lists").toFile()
    private val stores = LocalStoreProvider { LocalStore(openAppDatabase(File(dir, "todo.db")).database) }
    private val repository = TaskRepository(stores)
    private val defaultLocale = Locale.getDefault()
    private val today = LocalDate(2026, 11, 21)
    private val zone = TimeZones.of("Europe/Warsaw")
    private val days = DayTicker(
        object : Clock {
            override fun now(): Instant = LocalDateTime(2026, 11, 21, 10, 0).toInstant(zone)
        },
    ) { zone }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun cleanUp() {
        Dispatchers.resetMain()
        Locale.setDefault(defaultLocale)
        stores.closeIfOpen()
        dir.deleteRecursively()
    }

    @Test
    fun todayGroupsAndCompletionWithUndo() = runComposeUiTest {
        val overdue = runBlocking { repository.createTask("Pay the bill", due = Due(LocalDate(2026, 11, 19))) }
        runBlocking { repository.createTask("Water the plants", due = Due(today)) }
        runBlocking { repository.createTask("Later", due = Due(LocalDate(2026, 11, 25))) }
        val contexts = mutableListOf<QuickAddContext>()
        setContent {
            AppTheme {
                Box(Modifier.size(390.dp, 700.dp)) {
                    TodayScreen(TodayViewModel(repository, days), onAddTask = { contexts += it }, onOpenTask = {})
                }
            }
        }
        waitUntil(10_000) { onAllNodesWithText("Water the plants").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Overdue").assertExists()
        onNodeWithText("Pay the bill").assertExists()
        onAllNodesWithText("Later").fetchSemanticsNodes().let { assertTrue(it.isEmpty(), "not due today") }

        onNodeWithTag("fab-add").performClick()
        runOnIdle { assertEquals(QuickAddContext(date = today, today = today), contexts.single()) }

        onNodeWithContentDescription("Complete “Pay the bill”, priority 4").performClick()
        waitUntil(10_000) { onAllNodesWithText("Completed: Pay the bill").fetchSemanticsNodes().isNotEmpty() }
        waitUntil(10_000) { onAllNodesWithText("Overdue").fetchSemanticsNodes().isEmpty() }
        assertNotNull(runBlocking { repository.getTask(overdue.id) }?.completedAt)

        onNodeWithText("Undo").performClick()
        waitUntil(10_000) { onAllNodesWithText("Pay the bill").fetchSemanticsNodes().isNotEmpty() }
        assertNull(runBlocking { repository.getTask(overdue.id) }?.completedAt)
    }

    @Test
    fun inboxShowsCompletedTasksOnRequest() = runComposeUiTest {
        val done = runBlocking { repository.createTask("Already done") }
        runBlocking { repository.createTask("Still open") }
        runBlocking { repository.complete(done.id) }
        setContent { AppTheme { InboxScreen(InboxViewModel(repository, days), onOpenAbout = {}) } }
        waitUntil(10_000) { onAllNodesWithText("Still open").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("Already done").fetchSemanticsNodes().let { assertTrue(it.isEmpty(), "completed tasks are hidden") }

        onNodeWithTag("menu").performClick()
        onNodeWithTag("menu-show-completed").performClick()
        waitUntil(10_000) { onAllNodesWithText("Already done").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Completed").assertExists()
    }

    /** Plan Ф1a PR 3: scrolling Upcoming with 2 000 tasks does not stall; empty days are there too. */
    @Test
    fun upcomingScrollsThroughTwoThousandTasks() {
        // Outside the Compose test, which has its own time limit.
        runBlocking {
            repository.workspace()
            repeat(2_000) { i ->
                // 40 tasks a day on 50 days, then the days after that are empty.
                repository.createTask("Task $i", due = Due(today.plus(1 + i / 40, DateTimeUnit.DAY)))
            }
        }
        upcomingScroll(UpcomingViewModel(repository, days))
    }

    private fun upcomingScroll(viewModel: UpcomingViewModel) = runComposeUiTest {
        setContent { AppTheme { Box(Modifier.size(390.dp, 700.dp)) { UpcomingScreen(viewModel, onAddTask = {}, onOpenTask = {}) } } }
        waitUntil(30_000) { onAllNodesWithText("Task 0").fetchSemanticsNodes().isNotEmpty() }
        val elapsed = measureTime {
            onNodeWithTag("task-list").performScrollToNode(hasText("Task 1999"))
        }
        onNodeWithText("Task 1999").assertExists()
        assertTrue(elapsed.inWholeSeconds < 20, "scrolling to the last task took $elapsed")
        // Past the busy days the empty ones follow: day 51 and on.
        val emptyDay = today.plus(55, DateTimeUnit.DAY)
        onNodeWithTag("task-list").performScrollToNode(androidx.compose.ui.test.hasTestTag("day-$emptyDay"))
        onAllNodesWithTag("day-$emptyDay").fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
        // Near the end of the first 60 days more days were asked for.
        onNodeWithTag("task-list").performScrollToIndex(2_000 + UpcomingViewModel.INITIAL_DAYS - 1)
        waitUntil(10_000) { viewModel.state.value.days.size > UpcomingViewModel.INITIAL_DAYS }
    }
}
