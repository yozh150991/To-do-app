package io.github.yozh150991.todo.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import io.github.takahirom.roborazzi.captureRoboImage
import io.github.yozh150991.todo.core.database.LocalStore
import io.github.yozh150991.todo.core.database.LocalStoreProvider
import io.github.yozh150991.todo.core.database.openAppDatabase
import io.github.yozh150991.todo.core.domain.TaskRepository
import io.github.yozh150991.todo.ui.about.AboutDialog
import io.github.yozh150991.todo.ui.about.AboutViewModel
import io.github.yozh150991.todo.ui.about.AppInfo
import io.github.yozh150991.todo.ui.inbox.InboxScreen
import io.github.yozh150991.todo.ui.inbox.InboxViewModel
import java.io.File
import java.nio.file.Files
import java.util.Locale
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * Screenshots of the shared UI in all three languages (spec §12.1: Roborazzi, cut-off uk/pl
 * strings). Ф0 records them as CI evidence; baselines and `verify` come with the Ф1a screens.
 */
@OptIn(ExperimentalTestApi::class)
class ScreenshotTest {
    private val dir: File = Files.createTempDirectory("ui-screenshots").toFile()
    private val output = File(System.getProperty("roborazzi.output.dir") ?: "build/outputs/roborazzi")
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
            onRoot().captureRoboImage(File(output, "inbox-$language.png").path)
        }
    }

    @Test fun inboxEnglish() = inbox("en")

    @Test fun inboxUkrainian() = inbox("uk")

    @Test fun inboxPolish() = inbox("pl")

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
            onAllNodes(isRoot()).onLast().captureRoboImage(File(output, "about-uk.png").path)
        }
    }
}
