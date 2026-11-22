package io.github.yozh150991.todo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.yozh150991.todo.core.database.LocalStore
import io.github.yozh150991.todo.core.database.LocalStoreProvider
import io.github.yozh150991.todo.core.database.openAppDatabase
import io.github.yozh150991.todo.core.domain.TaskRepository
import io.github.yozh150991.todo.core.model.Due
import io.github.yozh150991.todo.core.model.Priority
import io.github.yozh150991.todo.core.model.Task
import io.github.yozh150991.todo.core.model.TimeZones
import io.github.yozh150991.todo.ui.task.TaskDetailScreen
import io.github.yozh150991.todo.ui.task.TaskDetailViewModel
import io.github.yozh150991.todo.ui.tasklist.DayTicker
import io.github.yozh150991.todo.ui.tasklist.UndoCenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
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

/**
 * Editing in the task details (UX_SPEC §3.3, plan Ф1a PR 3b) with a real database, in English:
 * every change saved at once and undoable, a blank name refused, TIME-08 choices, the bin.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class TaskDetailTest {
    private val dir: File = Files.createTempDirectory("details").toFile()
    private val stores = LocalStoreProvider { LocalStore(openAppDatabase(File(dir, "todo.db")).database) }
    private val zone = TimeZones.of("Europe/Warsaw")
    private val repository = TaskRepository(stores, deviceZone = { zone })
    private val defaultLocale = Locale.getDefault()
    private val today = LocalDate(2026, 11, 21) // a Saturday
    private val days = DayTicker(
        object : Clock {
            override fun now(): Instant = LocalDateTime(2026, 11, 21, 10, 0).toInstant(zone)
        },
    ) { zone }
    private val undo = UndoCenter()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun cleanUp() {
        // The details save what is typed as they close; let those writes finish before the database goes.
        runBlocking { kotlinx.coroutines.delay(300) }
        Dispatchers.resetMain()
        Locale.setDefault(defaultLocale)
        stores.closeIfOpen()
        dir.deleteRecursively()
    }

    private fun task(id: String): Task = assertNotNull(runBlocking { repository.getTask(id) })

    private fun ComposeUiTest.waitFor(what: String, timeoutMillis: Long = 10_000, condition: () -> Boolean) =
        waitUntil(conditionDescription = what, timeoutMillis = timeoutMillis, condition = condition)

    private fun ComposeUiTest.showDetails(viewModel: TaskDetailViewModel, onClose: () -> Unit = {}) {
        setContent { AppTheme { Box(Modifier.size(420.dp, 800.dp)) { TaskDetailScreen(viewModel, onClose = onClose) } } }
        // The first test of a cold JVM also loads fonts and Compose resources, next to a busy build.
        waitFor("details shown", timeoutMillis = 30_000) {
            onAllNodesWithTag("task-detail-title").fetchSemanticsNodes().isNotEmpty() &&
                onAllNodesWithTag("task-delete").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun ComposeUiTest.undoLast(snackbar: String) {
        waitFor("snackbar \"$snackbar\"") { onAllNodesWithText(snackbar).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Undo").performClick()
    }

    @Test
    fun nameIsSavedOnDoneAndUndone() = runComposeUiTest {
        val created = runBlocking { repository.createTask("Buy milk") }
        showDetails(TaskDetailViewModel(created.id, repository, undo, days) { zone })

        onNodeWithTag("task-detail-title").performTextReplacement("Buy oat milk")
        onNodeWithTag("task-detail-title").performImeAction()
        waitFor("renamed") { task(created.id).content == "Buy oat milk" }

        undoLast("Changed: Buy milk")
        waitFor("name back") { task(created.id).content == "Buy milk" }
        waitFor("field shows the old name") { onAllNodesWithText("Buy milk").fetchSemanticsNodes().isNotEmpty() }
    }

    /** UX_SPEC §3.3: a blank name is not saved; the field gets the old name back, with the reason. */
    @Test
    fun blankNameIsRefused() = runComposeUiTest {
        val created = runBlocking { repository.createTask("Buy milk") }
        showDetails(TaskDetailViewModel(created.id, repository, undo, days) { zone })

        onNodeWithTag("task-detail-title").performTextReplacement("   ")
        onNodeWithTag("task-detail-title").performImeAction()
        waitFor("reason shown") { onAllNodesWithText("A task needs a name").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Buy milk").assertExists()
        assertEquals("Buy milk", task(created.id).content)
    }

    /** What was typed is saved when the details close without leaving the field (Back, "Close"). */
    @Test
    fun typedTextIsSavedWhenTheDetailsClose() = runComposeUiTest {
        val created = runBlocking { repository.createTask("Buy milk") }
        val viewModel = TaskDetailViewModel(created.id, repository, undo, days) { zone }
        var open by mutableStateOf(true)
        setContent { AppTheme { if (open) TaskDetailScreen(viewModel, onClose = {}) } }
        waitFor("details shown", timeoutMillis = 30_000) { onAllNodesWithTag("task-detail-title").fetchSemanticsNodes().isNotEmpty() }

        onNodeWithTag("task-detail-title").performTextReplacement("Buy milk and bread")
        onNodeWithTag("task-detail-description").performTextReplacement("2 litres")
        open = false
        waitFor("saved on close") { task(created.id).let { it.content == "Buy milk and bread" && it.description == "2 litres" } }
    }

    @Test
    fun priorityDateTimeAndDeadline() = runComposeUiTest {
        val created = runBlocking { repository.createTask("Buy milk") }
        showDetails(TaskDetailViewModel(created.id, repository, undo, days) { zone })

        onNodeWithTag("field-priority").performClick()
        onNodeWithTag("priority-1").performClick()
        waitFor("priority 1") { task(created.id).priority == Priority.P1 }
        undoLast("Changed: Buy milk")
        waitFor("priority back") { task(created.id).priority == Priority.P4 }

        // Swipe left's choices are the date field's too (UX_SPEC §4).
        onNodeWithTag("field-date").performClick()
        onNodeWithTag("schedule-tomorrow").performClick()
        waitFor("tomorrow") { task(created.id).due == Due(LocalDate(2026, 11, 22)) }
        waitFor("date field") { onAllNodesWithText("Date: Tomorrow").fetchSemanticsNodes().isNotEmpty() }

        onNodeWithTag("field-time").performClick()
        onNodeWithTag("time-ok").performClick()
        waitFor("9:00 by default") { task(created.id).due == Due(LocalDate(2026, 11, 22), LocalTime(9, 0)) }
        waitFor("time field") { onAllNodesWithText("Time: 09:00").fetchSemanticsNodes().isNotEmpty() }

        onNodeWithTag("field-date").performClick()
        onNodeWithTag("schedule-next-week").performClick()
        waitFor("next Monday, the time kept") { task(created.id).due == Due(LocalDate(2026, 11, 23), LocalTime(9, 0)) }

        onNodeWithTag("clear-time").performClick()
        waitFor("all day") { task(created.id).due == Due(LocalDate(2026, 11, 23)) }

        onNodeWithTag("field-deadline").performClick()
        onNodeWithTag("date-ok").performClick()
        waitFor("deadline today") { task(created.id).deadline == today }
        waitFor("deadline field") { onAllNodesWithText("Deadline: Nov 21 · today").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("clear-deadline").performClick()
        waitFor("no deadline") { task(created.id).deadline == null }

        onNodeWithTag("clear-date").performClick()
        waitFor("no date") { task(created.id).due == null }
        undoLast("Date changed: Buy milk")
        waitFor("date back") { task(created.id).due == Due(LocalDate(2026, 11, 23)) }
    }

    /** TIME-08: 02:30 on 29 March 2026 does not exist in Warsaw; nothing is saved until a choice. */
    @Test
    fun timeInTheDstGapOffersChoices() = runComposeUiTest {
        val created = runBlocking { repository.createTask("Night train", due = Due(LocalDate(2026, 3, 29))) }
        val viewModel = TaskDetailViewModel(created.id, repository, undo, days) { zone }
        showDetails(viewModel)

        viewModel.setTime(LocalTime(2, 30))
        waitFor("choices shown") { onAllNodesWithTag("time-gap").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(Due(LocalDate(2026, 3, 29)), task(created.id).due, "nothing saved")
        onNodeWithTag("gap-03:00").assertExists()

        onNodeWithTag("gap-03:30").performClick()
        waitFor("chosen time saved") { task(created.id).due == Due(LocalDate(2026, 3, 29), LocalTime(3, 30)) }
        waitFor("choices gone") { onAllNodesWithTag("time-gap").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun moveToAnotherProjectAndBack() = runComposeUiTest {
        val created = runBlocking { repository.createTask("Buy milk") }
        val home = runBlocking { repository.projects.create("Home") }
        val inboxId = created.projectId
        showDetails(TaskDetailViewModel(created.id, repository, undo, days) { zone })
        waitFor("project field") { onAllNodesWithText("Project: Inbox").fetchSemanticsNodes().isNotEmpty() }

        onNodeWithTag("field-project").performClick()
        onNodeWithTag("move-to-${home.id}").performClick()
        waitFor("moved") { task(created.id).projectId == home.id }
        waitFor("field shows Home") { onAllNodesWithText("Project: Home").fetchSemanticsNodes().isNotEmpty() }

        undoLast("Moved to Home: Buy milk")
        waitFor("back in the Inbox") { task(created.id).projectId == inboxId }
    }

    /** Delete closes the details; Undo takes the task out of the bin. */
    @Test
    fun deleteClosesAndUndoRestores() = runComposeUiTest {
        val created = runBlocking { repository.createTask("Buy milk") }
        var closed = false
        showDetails(TaskDetailViewModel(created.id, repository, undo, days) { zone }, onClose = { closed = true })

        onNodeWithTag("task-delete").performClick()
        assertTrue(closed)
        waitFor("binned") { task(created.id).deletedAt != null }

        undoLast("Moved to bin: Buy milk")
        waitFor("restored") { task(created.id).deletedAt == null }
    }

    @Test
    fun completeFromTheDetails() = runComposeUiTest {
        val created = runBlocking { repository.createTask("Buy milk") }
        showDetails(TaskDetailViewModel(created.id, repository, undo, days) { zone })

        onNodeWithTag("task-detail-complete").performClick()
        waitFor("completed") { task(created.id).completedAt != null }
        waitFor("shown as completed") { onAllNodesWithText("Completed").fetchSemanticsNodes().isNotEmpty() }
        undoLast("Completed: Buy milk")
        waitFor("open again") { task(created.id).completedAt == null }
        assertNull(task(created.id).deletedAt)
    }
}
