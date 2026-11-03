package io.github.yozh150991.todo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
import kotlin.test.Test
import kotlin.test.assertEquals

/** Top-level navigation of docs/UX_SPEC.md §2.1 and the feature flags that hide unfinished tabs. */
@OptIn(ExperimentalTestApi::class)
class NavigationTest {
    private val inbox = "inbox content"

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
}
