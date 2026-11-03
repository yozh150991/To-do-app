package io.github.yozh150991.todo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import io.github.yozh150991.todo.ui.about.AboutDialog
import io.github.yozh150991.todo.ui.about.AboutViewModel
import io.github.yozh150991.todo.ui.inbox.InboxScreen
import io.github.yozh150991.todo.ui.inbox.InboxViewModel
import io.github.yozh150991.todo.ui.navigation.AppNavigation
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Shared UI module wiring; platform entry points add their own bindings: the database
 * (LocalStoreProvider), TaskRepository and AppInfo.
 */
val uiModule = module {
    viewModelOf(::InboxViewModel)
    viewModelOf(::AboutViewModel)
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

/** The whole app; [features] come from the platform entry point (debug or release build). */
@Composable
fun App(features: FeatureFlags = FeatureFlags.Release) {
    AppTheme {
        var aboutOpen by rememberSaveable { mutableStateOf(false) }
        AppNavigation(
            features = features,
            onOpenAbout = { aboutOpen = true },
            inbox = { InboxScreen(viewModel = koinViewModel<InboxViewModel>(), onOpenAbout = { aboutOpen = true }) },
        )
        if (aboutOpen) AboutDialog(viewModel = koinViewModel<AboutViewModel>(), onDismiss = { aboutOpen = false })
    }
}
