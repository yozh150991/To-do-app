package io.github.yozh150991.todo.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.yozh150991.todo.ui.resources.Res
import io.github.yozh150991.todo.ui.resources.about
import io.github.yozh150991.todo.ui.resources.today_placeholder
import io.github.yozh150991.todo.ui.resources.upcoming_placeholder
import org.jetbrains.compose.resources.stringResource

/** Today and Upcoming until their lists arrive in Ф1a; visible only in development builds. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaceholderScreen(tab: Tab) {
    val text = when (tab) {
        Tab.Today -> Res.string.today_placeholder
        Tab.Upcoming -> Res.string.upcoming_placeholder
        else -> return
    }
    Scaffold(
        modifier = Modifier.testTag("screen-${tab.route}"),
        topBar = { TopAppBar(title = { Text(stringResource(tab.label)) }) },
    ) { padding ->
        Text(
            text = stringResource(text),
            modifier = Modifier.padding(padding).padding(16.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Browse (UX_SPEC §2.1): projects, labels, trash, backup and settings land here phase by phase;
 * for now it holds "About".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowseScreen(onOpenAbout: () -> Unit) {
    Scaffold(
        modifier = Modifier.testTag("screen-${Tab.Browse.route}"),
        topBar = { TopAppBar(title = { Text(stringResource(Tab.Browse.label)) }) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            ListItem(
                headlineContent = { Text(stringResource(Res.string.about)) },
                modifier = Modifier.clickable(onClick = onOpenAbout).testTag("browse-about"),
            )
            HorizontalDivider()
        }
    }
}
