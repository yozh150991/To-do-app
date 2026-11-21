package io.github.yozh150991.todo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.takahirom.roborazzi.captureRoboImage
import io.github.yozh150991.todo.core.database.LocalStore
import io.github.yozh150991.todo.core.database.LocalStoreProvider
import io.github.yozh150991.todo.core.database.openAppDatabase
import io.github.yozh150991.todo.core.domain.TaskRepository
import io.github.yozh150991.todo.core.model.Due
import io.github.yozh150991.todo.core.model.Priority
import io.github.yozh150991.todo.ui.about.AboutDialog
import io.github.yozh150991.todo.ui.about.AboutViewModel
import io.github.yozh150991.todo.ui.about.AppInfo
import io.github.yozh150991.todo.ui.inbox.InboxScreen
import io.github.yozh150991.todo.ui.inbox.InboxViewModel
import io.github.yozh150991.todo.ui.navigation.AppNavigation
import io.github.yozh150991.todo.ui.navigation.TaskLink
import io.github.yozh150991.todo.ui.quickadd.QuickAddContent
import io.github.yozh150991.todo.ui.quickadd.QuickAddContext
import io.github.yozh150991.todo.ui.quickadd.QuickAddUiState
import io.github.yozh150991.todo.ui.task.TaskDetailScreen
import io.github.yozh150991.todo.ui.task.TaskDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.io.File
import java.nio.file.Files
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * Screenshots of the shared UI (spec §12.1: Roborazzi, cut-off uk/pl strings), each compared with
 * its committed baseline in ui/screenshots/ (Baselines). Fixed data and dates, so the pictures
 * only change when the UI does.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class ScreenshotTest {
    private val dir: File = Files.createTempDirectory("ui-screenshots").toFile()
    private val opened = mutableListOf<LocalStoreProvider>()
    private val defaultLocale: Locale = Locale.getDefault()

    private val sample = mapOf("en" to "Buy milk", "uk" to "Купити молоко", "pl" to "Kupić mleko")

    private fun repository(name: String): TaskRepository {
        val stores = LocalStoreProvider { LocalStore(openAppDatabase(File(dir, "$name.db")).database) }
        opened += stores
        return TaskRepository(stores)
    }

    @AfterTest
    fun cleanUp() {
        Locale.setDefault(defaultLocale)
        opened.forEach { it.closeIfOpen() }
        dir.deleteRecursively()
    }

    private fun inbox(language: String) {
        Locale.setDefault(Locale.forLanguageTag(language))
        val task = sample.getValue(language)
        val repository = repository("inbox-$language")
        runBlocking { repository.createTask(task) }
        val viewModel = InboxViewModel(repository)
        runComposeUiTest {
            setContent { AppTheme { InboxScreen(viewModel = viewModel, onOpenAbout = {}) } }
            waitUntil(timeoutMillis = 15_000) { onAllNodesWithText(task).fetchSemanticsNodes().isNotEmpty() }
            onRoot().captureRoboImage(Baselines.path("inbox-$language"))
        }
        Baselines.verify("inbox-$language")
    }

    @Test fun inboxEnglish() = inbox("en")

    @Test fun inboxUkrainian() = inbox("uk")

    @Test fun inboxPolish() = inbox("pl")

    /** Development build: the four tabs as a bottom bar (phone) and as a rail (tablet, desktop). */
    private fun navigation(name: String, width: Int, height: Int) {
        Locale.setDefault(Locale.forLanguageTag("uk"))
        val repository = repository("navigation-$name")
        val viewModel = InboxViewModel(repository)
        runComposeUiTest {
            setContent {
                AppTheme {
                    Box(Modifier.size(width.dp, height.dp).testTag("frame")) {
                        AppNavigation(
                            features = FeatureFlags.Development,
                            onOpenAbout = {},
                            inbox = { InboxScreen(viewModel = viewModel, onOpenAbout = {}) },
                        )
                    }
                }
            }
            onNodeWithTag("screen-today").assertExists()
            onNodeWithTag("frame").captureRoboImage(Baselines.path("navigation-$name-uk"))
        }
        Baselines.verify("navigation-$name-uk")
    }

    @Test fun navigationPhone() = navigation("phone", 390, 740)

    @Test fun navigationTablet() = navigation("tablet", 900, 600)

    @Test
    fun aboutUkrainian() {
        Locale.setDefault(Locale.forLanguageTag("uk"))
        val viewModel = AboutViewModel(repository("about"), AppInfo(version = "test", platform = "JVM"))
        runComposeUiTest {
            setContent { AppTheme { AboutDialog(viewModel = viewModel, onDismiss = {}) } }
            // The dialog shows the database facts once diagnostics() has read them through Room.
            waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("SQLite 3.", substring = true).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("FTS5: так", substring = true).assertExists()
            // The dialog is drawn in its own layer: the last semantics root.
            onAllNodes(isRoot()).onLast().captureRoboImage(Baselines.path("about-uk"))
        }
        // SQLite version and counts are machine facts, not UI: recorded as evidence, not compared.
    }

    /** Quick Add with a typed line and the previous task just added (UX_SPEC §3.4). */
    @Test
    fun quickAddUkrainian() {
        Locale.setDefault(Locale.forLanguageTag("uk"))
        val today = LocalDate(2026, 11, 21)
        val repository = repository("quick-add")
        val added = runBlocking { repository.createTask("Купити хліб") }
        runComposeUiTest {
            setContent {
                AppTheme {
                    Surface(Modifier.size(390.dp, 220.dp).testTag("frame")) {
                        QuickAddContent(
                            state = QuickAddUiState(draft = "Подзвонити мамі", lastAdded = added),
                            context = QuickAddContext(date = today, today = today),
                            onDraftChange = {},
                            onAdd = {},
                            onOpen = {},
                        )
                    }
                }
            }
            onNodeWithTag("quick-add-added").assertExists()
            onNodeWithTag("frame").captureRoboImage(Baselines.path("quick-add-uk"))
        }
        Baselines.verify("quick-add-uk")
    }

    /** Task details on a phone (UX_SPEC §3.3), with every field Ф1a shows. */
    @Test
    fun taskDetailUkrainian() {
        Locale.setDefault(Locale.forLanguageTag("uk"))
        val repository = repository("task-detail")
        val task = runBlocking {
            repository.createTask(
                "Оплатити рахунок за інтернет",
                priority = Priority.P1,
                due = Due(LocalDate(2026, 11, 23), LocalTime(10, 0), "Europe/Warsaw"),
                description = "Договір № 12345, оплата до кінця місяця",
                deadline = LocalDate(2026, 11, 30),
            )
        }
        val viewModel = TaskDetailViewModel(task.id, repository)
        runComposeUiTest {
            setContent {
                AppTheme {
                    Box(Modifier.size(390.dp, 600.dp).testTag("frame")) { TaskDetailScreen(viewModel = viewModel, onClose = {}) }
                }
            }
            waitUntil(timeoutMillis = 15_000) { onAllNodesWithText(task.content).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("frame").captureRoboImage(Baselines.path("task-detail-phone-uk"))
        }
        Baselines.verify("task-detail-phone-uk")
    }

    /** Expanded width (UX_SPEC §2.2): the Inbox list and the opened task side by side. */
    @Test
    fun listDetailUkrainian() {
        Locale.setDefault(Locale.forLanguageTag("uk"))
        val repository = repository("list-detail")
        val tasks = runBlocking { listOf("Купити молоко", "Здати звіт", "Записатися до лікаря").map { repository.createTask(it) } }
        val opened = tasks[1]
        // The deep link navigates from a coroutine on the test thread; navigation moves lifecycles
        // and needs that thread to be the main one (as in NavigationTest).
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val inboxViewModel = InboxViewModel(repository)
        val detailViewModel = TaskDetailViewModel(opened.id, repository)
        try {
            listDetail(tasks.map { it.content }, opened.id, inboxViewModel, detailViewModel)
        } finally {
            Dispatchers.resetMain()
        }
        Baselines.verify("list-detail-tablet-uk")
    }

    private fun listDetail(titles: List<String>, openedId: String, inboxViewModel: InboxViewModel, detailViewModel: TaskDetailViewModel) {
        runComposeUiTest {
            setContent {
                AppTheme {
                    Box(Modifier.size(1000.dp, 600.dp).testTag("frame")) {
                        AppNavigation(
                            features = FeatureFlags.Release,
                            onOpenAbout = {},
                            inbox = { slot ->
                                InboxScreen(
                                    viewModel = inboxViewModel,
                                    onOpenAbout = {},
                                    onOpenTask = slot.openTask,
                                    selectedTaskId = slot.selectedTaskId,
                                )
                            },
                            taskDetail = { _, onClose -> TaskDetailScreen(viewModel = detailViewModel, onClose = onClose) },
                            openTask = TaskLink(openedId),
                        )
                    }
                }
            }
            waitUntil(timeoutMillis = 15_000) { onAllNodesWithTag("detail-pane").fetchSemanticsNodes().isNotEmpty() }
            waitUntil(timeoutMillis = 15_000) { onAllNodesWithText(titles[2]).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("frame").captureRoboImage(Baselines.path("list-detail-tablet-uk"))
        }
    }
}
