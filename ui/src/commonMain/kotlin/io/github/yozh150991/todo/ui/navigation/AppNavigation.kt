package io.github.yozh150991.todo.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import io.github.yozh150991.todo.ui.FeatureFlags
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import io.github.yozh150991.todo.ui.quickadd.QuickAddContext
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

/**
 * The four top-level destinations of docs/UX_SPEC.md §2.1, in the order of the navigation bar.
 * Each tab is a nested graph: its list, and — for the task lists — task details on top of it, so
 * Back from details returns to the list and every tab keeps its own back stack.
 */
enum class Tab(val route: String, val label: StringResource, val icon: DrawableResource) {
    Inbox("inbox", Res.string.tab_inbox, Res.drawable.ic_tab_inbox),
    Today("today", Res.string.tab_today, Res.drawable.ic_tab_today),
    Upcoming("upcoming", Res.string.tab_upcoming, Res.drawable.ic_tab_upcoming),

    /** Browse → a project's tasks → task details. */
    Browse("browse", Res.string.tab_browse, Res.drawable.ic_tab_browse),
    ;

    val listRoute: String get() = "$route/list"

    /** Route pattern of task details over this tab's list (or, in Browse, over a project). */
    val taskRoute: String get() = "$route/task/{$TASK_ID}"

    fun taskRoute(taskId: String): String = "$route/task/$taskId"

    companion object {
        const val TASK_ID = "taskId"
        const val PROJECT_ID = "projectId"

        /** A project's tasks, opened from Browse. */
        const val PROJECT_ROUTE = "browse/project/{$PROJECT_ID}"

        fun projectRoute(projectId: String): String = "browse/project/$projectId"
    }
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

/**
 * What a task list gets from the navigation: which task the detail pane shows next to it (on
 * expanded widths), how to open a task over this list, and how to open Quick Add.
 */
class TaskListSlot(
    val selectedTaskId: String?,
    val openTask: (String) -> Unit,
    val addTask: (QuickAddContext) -> Unit,
)

/** What Browse gets from the navigation. */
class BrowseSlot(val openProject: (String) -> Unit, val openAbout: () -> Unit)

/**
 * A request to show a task's details: from a reminder notification (Ф1a PR 7), the "Open" action
 * of Quick Add, a deep link. [nonce] makes the same task open again when asked twice.
 */
data class TaskLink(val taskId: String, val nonce: Long = 0)

/** Below this width a bottom navigation bar, from it a navigation rail (UX_SPEC §2.2). */
private val RailWidth = 600.dp

/** From this width the list and the task details sit side by side (UX_SPEC §2.2, expanded). */
internal val ListDetailWidth = 840.dp

private val DetailPaneWidth = 400.dp

/**
 * Top-level navigation. Each tab keeps its own back stack and scroll position; Back from task
 * details returns to the list, from the root of another tab to the start tab, and from the start
 * tab leaves the app (UX_SPEC §2.1). With a single visible tab there is no navigation bar at all.
 *
 * The screens come in as slots, so tests can put stand-ins; a slot left null shows the Ф0
 * placeholder (Today, Upcoming) or a Browse with "About" only.
 */
@Composable
fun AppNavigation(
    features: FeatureFlags,
    onOpenAbout: () -> Unit,
    inbox: @Composable (TaskListSlot) -> Unit,
    modifier: Modifier = Modifier,
    todayList: (@Composable (TaskListSlot) -> Unit)? = null,
    upcomingList: (@Composable (TaskListSlot) -> Unit)? = null,
    browse: (@Composable (BrowseSlot) -> Unit)? = null,
    /** A project's tasks: project ID, the list slot, and Back. */
    project: (@Composable (String, TaskListSlot, () -> Unit) -> Unit)? = null,
    taskDetail: @Composable (taskId: String, onClose: () -> Unit) -> Unit = { _, _ -> },
    onAddTask: (QuickAddContext) -> Unit = {},
    /** Today's date for the context of Quick Add opened from the Today placeholder. */
    today: () -> LocalDate? = { null },
    openTask: TaskLink? = null,
    navController: NavHostController = rememberNavController(),
) {
    val tabs = features.visibleTabs()
    val start = features.startTab()
    val entry by navController.currentBackStackEntryAsState()

    fun tabOf(e: NavBackStackEntry?): Tab =
        tabs.firstOrNull { tab -> e?.destination?.hierarchy?.any { it.route == tab.route } == true } ?: start

    val current = tabOf(entry)

    fun open(tab: Tab) {
        navController.navigate(tab.route) {
            // The start tab's list, not its graph: popping up to the graph would remove the list too.
            popUpTo(start.listRoute) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun isTaskRoute(e: NavBackStackEntry?): Boolean = e?.destination?.route?.let { route -> Tab.entries.any { it.taskRoute == route } } == true

    /** Details over the current list of [tab] — replacing details already open there, so Back goes to the list. */
    fun openTaskIn(tab: Tab, taskId: String) {
        if (tab != tabOf(navController.currentBackStackEntry)) open(tab)
        if (isTaskRoute(navController.currentBackStackEntry)) navController.popBackStack()
        navController.navigate(tab.taskRoute(taskId))
    }

    LaunchedEffect(openTask) {
        val link = openTask ?: return@LaunchedEffect
        // The NavHost sets its graph while it is laid out; wait until it shows its first entry.
        snapshotFlow { entry }.first { it != null }
        openTaskIn(tabOf(navController.currentBackStackEntry), link.taskId)
    }

    /** The list a back stack entry shows, with [selectedTaskId] highlighted (null: none). */
    val listContent: @Composable (Tab, NavBackStackEntry, String?) -> Unit = { tab, listEntry, selectedTaskId ->
        val slot = TaskListSlot(
            selectedTaskId = selectedTaskId,
            openTask = { id -> openTaskIn(tab, id) },
            addTask = onAddTask,
        )
        when {
            listEntry.destination.route == Tab.PROJECT_ROUTE -> {
                val projectId = listEntry.savedStateHandle.get<String>(Tab.PROJECT_ID).orEmpty()
                project?.invoke(projectId, slot) { navController.popBackStack() }
            }
            tab == Tab.Inbox -> inbox(slot)
            tab == Tab.Today -> todayList?.invoke(slot)
                ?: PlaceholderScreen(tab, onAdd = { onAddTask(QuickAddContext(date = today(), today = today())) })
            tab == Tab.Upcoming -> upcomingList?.invoke(slot)
                ?: PlaceholderScreen(tab, onAdd = { onAddTask(QuickAddContext(today = today())) })
            else -> {
                val browseSlot = BrowseSlot(
                    openProject = { id -> navController.navigate(Tab.projectRoute(id)) },
                    openAbout = onOpenAbout,
                )
                browse?.invoke(browseSlot) ?: BrowseScreen(onOpenAbout = onOpenAbout)
            }
        }
    }

    fun NavGraphBuilder.tabGraph(tab: Tab) {
        navigation(startDestination = tab.listRoute, route = tab.route) {
            composable(tab.listRoute) { listEntry -> listContent(tab, listEntry, null) }
            if (tab == Tab.Browse) {
                composable(Tab.PROJECT_ROUTE) { projectEntry -> listContent(tab, projectEntry, null) }
            }
            composable(tab.taskRoute) { taskEntry ->
                val taskId = taskEntry.savedStateHandle.get<String>(Tab.TASK_ID).orEmpty()
                val close: () -> Unit = { navController.popBackStack() }
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    // The list under the details: the tab's list, or in Browse the project it was opened from.
                    val below: NavBackStackEntry? = remember(taskEntry) { navController.previousBackStackEntry }
                    if (maxWidth >= ListDetailWidth && below != null && below.destination.route != tab.taskRoute) {
                        Row(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                // The list keeps its own view model: that of its entry below.
                                CompositionLocalProvider(LocalViewModelStoreOwner provides below) {
                                    listContent(tab, below, taskId)
                                }
                            }
                            VerticalDivider()
                            Box(Modifier.width(DetailPaneWidth).fillMaxHeight().testTag("detail-pane")) {
                                taskDetail(taskId, close)
                            }
                        }
                    } else {
                        taskDetail(taskId, close)
                    }
                }
            }
        }
    }

    val host: @Composable (Modifier) -> Unit = { hostModifier ->
        NavHost(navController = navController, startDestination = start.route, modifier = hostModifier) {
            Tab.entries.forEach { tabGraph(it) }
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
