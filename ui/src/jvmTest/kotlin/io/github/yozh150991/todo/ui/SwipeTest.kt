package io.github.yozh150991.todo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import io.github.yozh150991.todo.core.database.LocalStore
import io.github.yozh150991.todo.core.database.LocalStoreProvider
import io.github.yozh150991.todo.core.database.openAppDatabase
import io.github.yozh150991.todo.core.domain.TaskRepository
import io.github.yozh150991.todo.core.model.Due
import io.github.yozh150991.todo.core.model.Task
import io.github.yozh150991.todo.core.model.TimeZones
import io.github.yozh150991.todo.ui.inbox.InboxScreen
import io.github.yozh150991.todo.ui.inbox.InboxViewModel
import io.github.yozh150991.todo.ui.tasklist.DayTicker
import io.github.yozh150991.todo.ui.tasklist.UndoCenter
import io.github.yozh150991.todo.ui.tasklist.nextWeek
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import java.io.File
import java.nio.file.Files
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.Instant

/** Swipes on task rows (UX_SPEC §4) and their TalkBack actions, on the Inbox with a real database. */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class SwipeTest {
    private val dir: File = Files.createTempDirectory("swipes").toFile()
    private val stores = LocalStoreProvider { LocalStore(openAppDatabase(File(dir, "todo.db")).database) }
    private val zone = TimeZones.of("Europe/Warsaw")
    private val repository = TaskRepository(stores, deviceZone = { zone })
    private val defaultLocale = Locale.getDefault()
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

    private fun task(id: String): Task = assertNotNull(runBlocking { repository.getTask(id) })

    private fun ComposeUiTest.waitFor(what: String, condition: () -> Boolean) =
        waitUntil(conditionDescription = what, timeoutMillis = 10_000, condition = condition)

    private fun ComposeUiTest.showInbox(vararg titles: String) {
        setContent {
            AppTheme { Box(Modifier.size(400.dp, 700.dp)) { InboxScreen(InboxViewModel(repository, days, UndoCenter()), onOpenAbout = {}) } }
        }
        waitFor("rows shown") { titles.all { onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() } }
    }

    @Test
    fun swipeRightCompletesWithUndo() = runComposeUiTest {
        val created = runBlocking { repository.createTask("Swipe me") }
        showInbox("Swipe me")

        onNodeWithTag("task-row-${created.id}").performTouchInput { swipeRight() }
        waitFor("completed") { task(created.id).completedAt != null }
        waitFor("snackbar") { onAllNodesWithText("Completed: Swipe me").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Undo").performClick()
        waitFor("open again") { task(created.id).completedAt == null }
    }

    @Test
    fun swipeLeftSchedules() = runComposeUiTest {
        val created = runBlocking { repository.createTask("Plan me") }
        showInbox("Plan me")

        onNodeWithTag("task-row-${created.id}").performTouchInput { swipeLeft() }
        waitFor("date choices") { onAllNodesWithTag("schedule-sheet").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("schedule-tomorrow").performClick()
        waitFor("scheduled for tomorrow") { task(created.id).due == Due(LocalDate(2026, 11, 22)) }
        waitFor("snackbar") { onAllNodesWithText("Date changed: Plan me").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Undo").performClick()
        waitFor("no date again") { task(created.id).due == null }
    }

    /** A short drag springs back and changes nothing. */
    @Test
    fun shortSwipeDoesNothing() = runComposeUiTest {
        val created = runBlocking { repository.createTask("Stay") }
        showInbox("Stay")

        onNodeWithTag("task-row-${created.id}").performTouchInput { swipeRight(startX = centerX, endX = centerX + width * 0.1f) }
        waitForIdle()
        assertEquals(null, task(created.id).completedAt)
        onAllNodesWithTag("schedule-sheet").fetchSemanticsNodes().let { assertEquals(0, it.size) }
    }

    /** TalkBack: the same actions without a gesture (UX_SPEC §4, "Альтернатива без жесту"). */
    @Test
    fun accessibilityActionsCompleteAndSchedule() = runComposeUiTest {
        val first = runBlocking { repository.createTask("Read aloud") }
        val second = runBlocking { repository.createTask("Plan aloud") }
        showInbox("Read aloud", "Plan aloud")

        onNodeWithTag("task-row-${first.id}").performCustomAccessibilityActionWithLabel("Complete")
        waitFor("completed") { task(first.id).completedAt != null }

        onNodeWithTag("task-row-${second.id}").performCustomAccessibilityActionWithLabel("Schedule")
        waitFor("date choices") { onAllNodesWithTag("schedule-sheet").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("schedule-next-week").performClick()
        waitFor("next Monday") { task(second.id).due == Due(nextWeek(LocalDate(2026, 11, 21))) }
        assertEquals(LocalDate(2026, 11, 23), nextWeek(LocalDate(2026, 11, 21)))
        assertEquals(LocalDate(2026, 11, 30), nextWeek(LocalDate(2026, 11, 23)), "from a Monday: the Monday after")
        assertEquals(LocalDate(2026, 11, 23), nextWeek(LocalDate(2026, 11, 22)), "from a Sunday: tomorrow")
    }
}
