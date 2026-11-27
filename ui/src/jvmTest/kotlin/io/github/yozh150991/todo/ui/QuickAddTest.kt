package io.github.yozh150991.todo.ui

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yozh150991.todo.core.database.LocalStore
import io.github.yozh150991.todo.core.database.LocalStoreProvider
import io.github.yozh150991.todo.core.database.openAppDatabase
import io.github.yozh150991.todo.core.domain.TaskRepository
import io.github.yozh150991.todo.ui.quickadd.QuickAddContent
import io.github.yozh150991.todo.ui.quickadd.QuickAddContext
import io.github.yozh150991.todo.ui.quickadd.QuickAddHost
import io.github.yozh150991.todo.ui.quickadd.QuickAddViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Quick Add sheet (UX_SPEC §3.4, plan Ф1a PR 1): context, stays open after "Add", draft kept. */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class QuickAddTest {
    private val dir: File = Files.createTempDirectory("quick-add").toFile()
    private val stores = LocalStoreProvider { LocalStore(openAppDatabase(File(dir, "todo.db")).database) }
    private val repository = TaskRepository(stores)

    @BeforeTest
    fun useTestThreadAsMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun cleanUp() {
        Dispatchers.resetMain()
        stores.closeIfOpen()
        dir.deleteRecursively()
    }

    private fun tasks() = runBlocking { repository.observeInbox().first() }

    @Test
    fun addKeepsTheSheetOpenAndUsesTheContext() = runComposeUiTest {
        val date = LocalDate(2026, 11, 21)
        val viewModel = QuickAddViewModel(repository)
        viewModel.open(QuickAddContext(date = date, today = date))
        setContent {
            AppTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val context = state.context
                if (context != null) {
                    QuickAddContent(state, context, viewModel::updateDraft, viewModel::add, onOpen = {})
                }
            }
        }
        onNodeWithTag("quick-add-submit").assertIsNotEnabled()
        onNodeWithTag("quick-add-field").performTextInput("Купити хліб")
        onNodeWithTag("quick-add-submit").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("quick-add-added").fetchSemanticsNodes().isNotEmpty() }

        val created = tasks().single()
        assertEquals("Купити хліб", created.content)
        assertEquals(date, created.due?.date, "Quick Add from Today puts today's date")
        assertEquals("", viewModel.state.value.draft, "the field is empty for the next task")
        assertEquals(created.id, viewModel.state.value.lastAdded?.id)

        onNodeWithTag("quick-add-field").performTextInput("Друга")
        onNodeWithTag("quick-add-submit").performClick()
        waitUntil(timeoutMillis = 10_000) { tasks().size == 2 }
    }

    @Test
    fun closingKeepsTheDraftUntilTheAppCloses() {
        val viewModel = QuickAddViewModel(repository)
        viewModel.open(QuickAddContext())
        viewModel.updateDraft("Ще не додано")
        viewModel.close()
        assertNull(viewModel.state.value.context)
        viewModel.open(QuickAddContext())
        assertEquals("Ще не додано", viewModel.state.value.draft)
    }

    @Test
    fun hostShowsTheSheetOnlyWhileOpen() = runComposeUiTest {
        val viewModel = QuickAddViewModel(repository)
        setContent { AppTheme { QuickAddHost(viewModel, onOpenTask = {}) } }
        onNodeWithTag("quick-add").assertDoesNotExist()
        runOnIdle { viewModel.open(QuickAddContext()) }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("quick-add-field").fetchSemanticsNodes().isNotEmpty() }
        runOnIdle { viewModel.close() }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("quick-add-field").fetchSemanticsNodes().isEmpty() }
    }
}
