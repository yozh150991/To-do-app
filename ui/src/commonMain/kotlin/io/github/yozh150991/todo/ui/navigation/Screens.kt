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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yozh150991.todo.core.domain.TaskRepository
import io.github.yozh150991.todo.core.model.Project
import io.github.yozh150991.todo.ui.resources.no_projects
import io.github.yozh150991.todo.ui.resources.projects_header
import io.github.yozh150991.todo.ui.tasklist.ListHeader
import io.github.yozh150991.todo.ui.tasklist.projectTree
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.yozh150991.todo.ui.quickadd.AddTaskButton
import io.github.yozh150991.todo.ui.resources.Res
import io.github.yozh150991.todo.ui.resources.about
import io.github.yozh150991.todo.ui.resources.today_placeholder
import io.github.yozh150991.todo.ui.resources.upcoming_placeholder
import org.jetbrains.compose.resources.stringResource

/**
 * Today and Upcoming until their lists arrive in Ф1a PR 3; visible only in development builds.
 * The "+" button already opens Quick Add with the screen's context.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaceholderScreen(tab: Tab, onAdd: () -> Unit) {
    val text = when (tab) {
        Tab.Today -> Res.string.today_placeholder
        Tab.Upcoming -> Res.string.upcoming_placeholder
        else -> return
    }
    Scaffold(
        modifier = Modifier.testTag("screen-${tab.route}"),
        topBar = { TopAppBar(title = { Text(stringResource(tab.label)) }) },
        floatingActionButton = { AddTaskButton(onClick = onAdd) },
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
 * Browse (UX_SPEC §2.1, §3.2): projects as a tree (Ф1a), then — phase by phase — labels, filters,
 * the bin, backup and settings; "About" at the end.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    onOpenAbout: () -> Unit,
    projects: List<Pair<Project, Int>> = emptyList(),
    onOpenProject: (String) -> Unit = {},
) {
    Scaffold(
        modifier = Modifier.testTag("screen-${Tab.Browse.route}"),
        topBar = { TopAppBar(title = { Text(stringResource(Tab.Browse.label)) }) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item(key = "projects") { ListHeader(stringResource(Res.string.projects_header)) }
            if (projects.isEmpty()) {
                item(key = "no-projects") {
                    Text(
                        stringResource(Res.string.no_projects),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            items(projects, key = { it.first.id }) { (project, depth) ->
                ListItem(
                    headlineContent = { Text(project.name) },
                    modifier = Modifier
                        .clickable { onOpenProject(project.id) }
                        .padding(start = 24.dp * depth)
                        .testTag("project-${project.id}"),
                )
            }
            item(key = "divider") { HorizontalDivider() }
            item(key = "about") {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.about)) },
                    modifier = Modifier.clickable(onClick = onOpenAbout).testTag("browse-about"),
                )
            }
        }
    }
}

/** Active projects other than the Inbox, in tree order, for [BrowseScreen]. */
class BrowseViewModel(repository: TaskRepository) : ViewModel() {
    val projects: StateFlow<List<Pair<Project, Int>>> = repository.projects.observeActive()
        .map { all -> projectTree(all.filterNot { it.isInbox }) }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
