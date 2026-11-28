package io.github.yozh150991.todo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import io.github.yozh150991.todo.ui.about.AboutDialog
import io.github.yozh150991.todo.ui.about.AboutViewModel
import io.github.yozh150991.todo.ui.inbox.InboxScreen
import io.github.yozh150991.todo.ui.inbox.InboxViewModel
import io.github.yozh150991.todo.ui.inbox.ProjectTasksScreen
import io.github.yozh150991.todo.ui.inbox.ProjectViewModel
import io.github.yozh150991.todo.ui.navigation.BrowseScreen
import io.github.yozh150991.todo.ui.navigation.BrowseViewModel
import io.github.yozh150991.todo.ui.tasklist.DayTicker
import io.github.yozh150991.todo.ui.tasklist.UndoCenter
import io.github.yozh150991.todo.ui.today.TodayScreen
import io.github.yozh150991.todo.ui.today.TodayViewModel
import io.github.yozh150991.todo.ui.upcoming.UpcomingScreen
import io.github.yozh150991.todo.ui.upcoming.UpcomingViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yozh150991.todo.core.model.TimeZones
import io.github.yozh150991.todo.ui.navigation.AppNavigation
import io.github.yozh150991.todo.ui.navigation.TaskLink
import io.github.yozh150991.todo.ui.quickadd.QuickAddHost
import io.github.yozh150991.todo.ui.quickadd.QuickAddViewModel
import io.github.yozh150991.todo.ui.task.TaskDetailScreen
import io.github.yozh150991.todo.ui.task.TaskDetailViewModel
import kotlinx.datetime.todayIn
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import kotlin.time.Clock

/**
 * Shared UI module wiring; platform entry points add their own bindings: the database
 * (LocalStoreProvider), TaskRepository and AppInfo.
 */
val uiModule = module {
    single { DayTicker(Clock.System) { TimeZones.systemDefault() } }
    // One pending Undo for the whole app (UX_SPEC §4), shared by every screen.
    single { UndoCenter() }
    viewModel { InboxViewModel(get(), get(), get()) }
    viewModel { params -> ProjectViewModel(params.get(), get(), get(), get()) }
    viewModel { TodayViewModel(get(), get(), get()) }
    viewModel { UpcomingViewModel(get(), get(), get()) }
    viewModelOf(::BrowseViewModel)
    viewModelOf(::AboutViewModel)
    viewModelOf(::QuickAddViewModel)
    viewModel { params -> TaskDetailViewModel(params.get(), get(), get(), get()) }
}

// Own palette (spec §1.3: no Todoist branding). Refined in docs/UX_SPEC.md.
private val LightColors = lightColorScheme(
    primary = Color(0xFF2F5D8C),
    onPrimary = Color.White,
    secondary = Color(0xFF5B7A52),
    background = Color(0xFFFBFAF7),
    surface = Color(0xFFFBFAF7),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CC2EA),
    onPrimary = Color(0xFF0B2741),
    secondary = Color(0xFFB4CFA8),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

/**
 * The whole app; [features] come from the platform entry point (debug or release build), and
 * [openTask] from a notification or a deep link (Android intent, Ф1a PR 7 reminders).
 */
@Composable
fun App(features: FeatureFlags = FeatureFlags.Release, openTask: TaskLink? = null) {
    AppTheme {
        var aboutOpen by rememberSaveable { mutableStateOf(false) }
        val quickAdd = koinViewModel<QuickAddViewModel>()
        // "Open" in Quick Add shows the new task over the current list, like a deep link.
        var link by remember { mutableStateOf(openTask) }
        LaunchedEffect(openTask) { if (openTask != null) link = openTask }
        AppNavigation(
            features = features,
            onOpenAbout = { aboutOpen = true },
            inbox = { slot ->
                InboxScreen(
                    viewModel = koinViewModel<InboxViewModel>(),
                    onOpenAbout = { aboutOpen = true },
                    onAddTask = slot.addTask,
                    onOpenTask = slot.openTask,
                    selectedTaskId = slot.selectedTaskId,
                )
            },
            todayList = { slot ->
                TodayScreen(koinViewModel<TodayViewModel>(), slot.addTask, slot.openTask, selectedTaskId = slot.selectedTaskId)
            },
            upcomingList = { slot ->
                UpcomingScreen(koinViewModel<UpcomingViewModel>(), slot.addTask, slot.openTask, selectedTaskId = slot.selectedTaskId)
            },
            browse = { slot ->
                val browse = koinViewModel<BrowseViewModel>()
                val projects by browse.projects.collectAsStateWithLifecycle()
                BrowseScreen(onOpenAbout = slot.openAbout, projects = projects, onOpenProject = slot.openProject)
            },
            project = { projectId, slot, onBack ->
                ProjectTasksScreen(
                    viewModel = koinViewModel<ProjectViewModel>(key = "project-$projectId") { parametersOf(projectId) },
                    modifier = Modifier.testTag("screen-project"),
                    onAddTask = slot.addTask,
                    onOpenTask = slot.openTask,
                    selectedTaskId = slot.selectedTaskId,
                    onBack = onBack,
                )
            },
            taskDetail = { taskId, onClose ->
                TaskDetailScreen(viewModel = koinViewModel<TaskDetailViewModel> { parametersOf(taskId) }, onClose = onClose)
            },
            onAddTask = quickAdd::open,
            today = { Clock.System.todayIn(TimeZones.systemDefault()) },
            openTask = link,
        )
        QuickAddHost(quickAdd, onOpenTask = { id -> link = TaskLink(id, nonce = (link?.nonce ?: 0) + 1) })
        if (aboutOpen) AboutDialog(viewModel = koinViewModel<AboutViewModel>(), onDismiss = { aboutOpen = false })
    }
}
