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
import io.github.yozh150991.todo.core.model.TimeZones
import io.github.yozh150991.todo.ui.navigation.AppNavigation
import io.github.yozh150991.todo.ui.navigation.TaskLink
import io.github.yozh150991.todo.ui.quickadd.QuickAddContext
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
    viewModelOf(::InboxViewModel)
    viewModelOf(::AboutViewModel)
    viewModelOf(::QuickAddViewModel)
    viewModel { params -> TaskDetailViewModel(params.get(), get()) }
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
                    onAddTask = { slot.addTask(QuickAddContext()) },
                    onOpenTask = slot.openTask,
                    selectedTaskId = slot.selectedTaskId,
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
