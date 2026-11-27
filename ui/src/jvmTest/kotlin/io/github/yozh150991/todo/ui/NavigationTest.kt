package io.github.yozh150991.todo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import io.github.yozh150991.todo.ui.navigation.AppNavigation
import io.github.yozh150991.todo.ui.navigation.TaskLink
import io.github.yozh150991.todo.ui.navigation.TaskListSlot
import io.github.yozh150991.todo.ui.quickadd.QuickAddContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Top-level navigation of docs/UX_SPEC.md §2.1–§2.2 and the feature flags that hide unfinished
 * tabs; task details over a list, side by side on expanded widths, and deep links (Ф1a PR 1).
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class NavigationTest {
    private val inbox = "inbox content"

    // Navigation moves back stack entries through their lifecycle, which must happen on the "main"
    // thread. In jvmTest that is Swing's EDT (kotlinx-coroutines-swing), while clicks and Back run
    // on the test thread; an unconfined Main makes the test thread the main one.
    @BeforeTest
    fun useTestThreadAsMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun restoreMain() = Dispatchers.resetMain()

    @Test
    fun releaseBuildShowsOnlyInboxWithoutNavigationBar() = runComposeUiTest {
        setContent { AppTheme { AppNavigation(FeatureFlags.Release, onOpenAbout = {}, inbox = { Text(inbox) }) } }
        onNodeWithText(inbox).assertExists()
        onNodeWithTag("tab-inbox").assertDoesNotExist()
        onNodeWithTag("tab-today").assertDoesNotExist()
    }

    @Test
    fun developmentBuildStartsOnTodayAndBackReturnsToIt() = runComposeUiTest {
        lateinit var navController: NavHostController
        var aboutOpened = 0
        setContent {
            navController = rememberNavController()
            AppTheme {
                Box(Modifier.size(390.dp, 760.dp)) {
                    AppNavigation(
                        features = FeatureFlags.Development,
                        onOpenAbout = { aboutOpened++ },
                        inbox = { Text(inbox) },
                        navController = navController,
                    )
                }
            }
        }
        onNodeWithTag("screen-today").assertExists()

        onNodeWithTag("tab-upcoming").performClick()
        onNodeWithTag("screen-upcoming").assertExists()
        // System Back from the root of another tab returns to the start tab.
        runOnIdle { navController.popBackStack() }
        onNodeWithTag("screen-today").assertExists()

        onNodeWithTag("tab-inbox").performClick()
        onNodeWithText(inbox).assertExists()

        onNodeWithTag("tab-browse").performClick()
        onNodeWithTag("browse-about").performClick()
        runOnIdle { assertEquals(1, aboutOpened) }
    }

    @Test
    fun reselectingTheCurrentTabKeepsOneEntry() = runComposeUiTest {
        lateinit var navController: NavHostController
        setContent {
            navController = rememberNavController()
            AppTheme {
                Box(Modifier.size(390.dp, 760.dp)) {
                    AppNavigation(FeatureFlags.Development, onOpenAbout = {}, inbox = { Text(inbox) }, navController = navController)
                }
            }
        }
        onNodeWithTag("tab-upcoming").performClick()
        onNodeWithTag("tab-upcoming").performClick()
        onNodeWithTag("tab-upcoming").performClick()
        runOnIdle { navController.popBackStack() }
        onNodeWithTag("screen-today").assertExists()
    }

    /** A stand-in list: shows which task is selected and opens "t1". */
    @androidx.compose.runtime.Composable
    private fun FakeList(slot: TaskListSlot) {
        Column {
            Text("$inbox selected=${slot.selectedTaskId}")
            Button(onClick = { slot.openTask("t1") }) { Text("open t1") }
            Button(onClick = { slot.addTask(QuickAddContext(projectId = "p")) }) { Text("add") }
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.app(
        width: Int,
        features: FeatureFlags = FeatureFlags.Development,
        openTask: TaskLink? = null,
        onAdd: (QuickAddContext) -> Unit = {},
    ): () -> NavHostController {
        lateinit var navController: NavHostController
        setContent {
            navController = rememberNavController()
            AppTheme {
                Box(Modifier.size(width.dp, 700.dp)) {
                    AppNavigation(
                        features = features,
                        onOpenAbout = {},
                        inbox = { FakeList(it) },
                        taskDetail = { id, onClose ->
                            Column {
                                Text("detail $id")
                                Button(onClick = onClose) { Text("close detail") }
                            }
                        },
                        onAddTask = onAdd,
                        openTask = openTask,
                        navController = navController,
                    )
                }
            }
        }
        return { navController }
    }

    @Test
    fun taskDetailsOpenOverTheListAndBackReturnsToIt() = runComposeUiTest {
        val nav = app(width = 390)
        onNodeWithTag("tab-inbox").performClick()
        onNodeWithText("open t1").performClick()
        onNodeWithText("detail t1").assertExists()
        onNodeWithText("$inbox selected=null").assertDoesNotExist()
        runOnIdle { nav().popBackStack() }
        onNodeWithText("$inbox selected=null").assertExists()
        onNodeWithText("detail t1").assertDoesNotExist()
        // Still on the Inbox tab: one more Back goes to the start tab.
        runOnIdle { nav().popBackStack() }
        onNodeWithTag("screen-today").assertExists()
    }

    @Test
    fun closeButtonOfDetailsReturnsToTheList() = runComposeUiTest {
        app(width = 390, features = FeatureFlags.Release)
        onNodeWithText("open t1").performClick()
        onNodeWithText("close detail").performClick()
        onNodeWithText("$inbox selected=null").assertExists()
    }

    /** UX_SPEC §2.2 expanded: list and details side by side, the opened task selected in the list. */
    @Test
    fun expandedWidthShowsListAndDetailsSideBySide() = runComposeUiTest {
        val nav = app(width = 1000, features = FeatureFlags.Release)
        onNodeWithText("open t1").performClick()
        onNodeWithText("detail t1").assertExists()
        onNodeWithText("$inbox selected=t1").assertExists()
        onNodeWithTag("detail-pane").assertExists()
        // Opening another task replaces the details instead of stacking them.
        onNodeWithText("open t1").performClick()
        runOnIdle { nav().popBackStack() }
        onNodeWithText("$inbox selected=null").assertExists()
        onNodeWithTag("detail-pane").assertDoesNotExist()
    }

    /** UX_SPEC §2.1: a reminder or a deep link opens details over the list; Back leads to the list. */
    @Test
    fun deepLinkOpensDetailsOverTheStartTab() = runComposeUiTest {
        val nav = app(width = 390, openTask = TaskLink("t9"))
        waitUntil(timeoutMillis = 5_000) { onAllNodes(androidx.compose.ui.test.hasText("detail t9")).fetchSemanticsNodes().isNotEmpty() }
        runOnIdle { nav().popBackStack() }
        onNodeWithTag("screen-today").assertExists()
    }

    @Test
    fun plusButtonsPassTheScreenContext() = runComposeUiTest {
        val contexts = mutableListOf<QuickAddContext>()
        app(width = 390, onAdd = { contexts += it })
        onNodeWithTag("fab-add").performClick() // Today
        onNodeWithTag("tab-inbox").performClick()
        onNodeWithText("add").performClick()
        runOnIdle {
            assertEquals(2, contexts.size)
            assertEquals(null, contexts[0].projectId)
            assertEquals(QuickAddContext(projectId = "p"), contexts[1])
        }
    }
}
