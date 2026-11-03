package io.github.yozh150991.todo.ui.navigation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.yozh150991.todo.ui.FeatureFlags
import io.github.yozh150991.todo.ui.resources.Res
import io.github.yozh150991.todo.ui.resources.ic_tab_browse
import io.github.yozh150991.todo.ui.resources.ic_tab_inbox
import io.github.yozh150991.todo.ui.resources.ic_tab_today
import io.github.yozh150991.todo.ui.resources.ic_tab_upcoming
import io.github.yozh150991.todo.ui.resources.tab_browse
import io.github.yozh150991.todo.ui.resources.tab_inbox
import io.github.yozh150991.todo.ui.resources.tab_today
import io.github.yozh150991.todo.ui.resources.tab_upcoming
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The four top-level destinations of docs/UX_SPEC.md §2.1, in the order of the navigation bar. */
enum class Tab(val route: String, val label: StringResource, val icon: DrawableResource) {
    Inbox("inbox", Res.string.tab_inbox, Res.drawable.ic_tab_inbox),
    Today("today", Res.string.tab_today, Res.drawable.ic_tab_today),
    Upcoming("upcoming", Res.string.tab_upcoming, Res.drawable.ic_tab_upcoming),
    Browse("browse", Res.string.tab_browse, Res.drawable.ic_tab_browse),
}

/** Tabs the build shows: Inbox always, the others while their feature flag is on. */
fun FeatureFlags.visibleTabs(): List<Tab> = buildList {
    add(Tab.Inbox)
    if (todayTab) add(Tab.Today)
    if (upcomingTab) add(Tab.Upcoming)
    if (browseTab) add(Tab.Browse)
}

/** UX_SPEC §2.1: the app starts on Today; while Today is hidden, on Inbox. */
fun FeatureFlags.startTab(): Tab = if (todayTab) Tab.Today else Tab.Inbox

/** Below this width a bottom navigation bar, from it a navigation rail (UX_SPEC §2.2). */
private val RailWidth = 600.dp

/**
 * Top-level navigation. Each tab keeps its own back stack and scroll position; Back from the root
 * of another tab returns to the start tab, and from the start tab leaves the app (UX_SPEC §2.1).
 * With a single visible tab there is no navigation bar at all.
 */
@Composable
fun AppNavigation(
    features: FeatureFlags,
    onOpenAbout: () -> Unit,
    inbox: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val tabs = features.visibleTabs()
    val start = features.startTab()
    val entry by navController.currentBackStackEntryAsState()
    val current = tabs.firstOrNull { tab -> entry?.destination?.hierarchy?.any { it.route == tab.route } == true } ?: start

    fun open(tab: Tab) {
        navController.navigate(tab.route) {
            popUpTo(start.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val host: @Composable (Modifier) -> Unit = { hostModifier ->
        NavHost(navController = navController, startDestination = start.route, modifier = hostModifier) {
            composable(Tab.Inbox.route) { inbox() }
            composable(Tab.Today.route) { PlaceholderScreen(Tab.Today) }
            composable(Tab.Upcoming.route) { PlaceholderScreen(Tab.Upcoming) }
            composable(Tab.Browse.route) { BrowseScreen(onOpenAbout = onOpenAbout) }
        }
    }

    if (tabs.size == 1) {
        host(modifier.fillMaxSize())
    } else {
        BoxWithConstraints(modifier.fillMaxSize()) {
            if (maxWidth < RailWidth) {
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            tabs.forEach { tab ->
                                NavigationBarItem(
                                    selected = tab == current,
                                    onClick = { open(tab) },
                                    icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                                    label = { Text(stringResource(tab.label)) },
                                    modifier = Modifier.testTag("tab-${tab.route}"),
                                )
                            }
                        }
                    },
                    // The screens inside have their own top bars and take the status bar inset.
                    contentWindowInsets = WindowInsets(0),
                ) { padding ->
                    host(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding))
                }
            } else {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail {
                        tabs.forEach { tab ->
                            NavigationRailItem(
                                selected = tab == current,
                                onClick = { open(tab) },
                                icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                                label = { Text(stringResource(tab.label)) },
                                modifier = Modifier.testTag("tab-${tab.route}"),
                            )
                        }
                    }
                    host(Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}
