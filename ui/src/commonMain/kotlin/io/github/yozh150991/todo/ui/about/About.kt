package io.github.yozh150991.todo.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.yozh150991.todo.core.database.DatabaseDiagnostics
import io.github.yozh150991.todo.core.domain.TaskRepository
import io.github.yozh150991.todo.ui.resources.Res
import io.github.yozh150991.todo.ui.resources.about
import io.github.yozh150991.todo.ui.resources.about_counts
import io.github.yozh150991.todo.ui.resources.about_database
import io.github.yozh150991.todo.ui.resources.about_failed
import io.github.yozh150991.todo.ui.resources.about_loading
import io.github.yozh150991.todo.ui.resources.about_platform
import io.github.yozh150991.todo.ui.resources.about_sqlite
import io.github.yozh150991.todo.ui.resources.about_version
import io.github.yozh150991.todo.ui.resources.close
import io.github.yozh150991.todo.ui.resources.no
import io.github.yozh150991.todo.ui.resources.yes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** What the platform entry point knows about the build and the device (Android, Desktop, Web). */
data class AppInfo(val version: String, val platform: String)

sealed interface AboutState {
    data object Loading : AboutState
    data class Ready(val info: AppInfo, val database: DatabaseDiagnostics) : AboutState
    data class Failed(val info: AppInfo, val reason: String?) : AboutState
}

/**
 * "About": version, device and the database's runtime facts (SQLite version, FTS5, journal, schema,
 * quick_check). Doubles as device evidence for the Ф0 gate (ADR-001): a screenshot from a real phone.
 */
class AboutViewModel(private val repository: TaskRepository, private val info: AppInfo) : ViewModel() {
    private val _state = MutableStateFlow<AboutState>(AboutState.Loading)
    val state: StateFlow<AboutState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = AboutState.Loading
            _state.value = try {
                AboutState.Ready(info, repository.diagnostics())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AboutState.Failed(info, e.message)
            }
        }
    }
}

@Composable
fun AboutDialog(viewModel: AboutViewModel, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.load() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) } },
        title = { Text(stringResource(Res.string.about)) },
        text = {
            // Selectable, so the details can be copied into a bug report.
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    when (val s = state) {
                        AboutState.Loading -> Text(stringResource(Res.string.about_loading))
                        is AboutState.Ready -> {
                            Header(s.info)
                            val fts5 = stringResource(if (s.database.fts5) Res.string.yes else Res.string.no)
                            Line(stringResource(Res.string.about_sqlite, s.database.sqliteVersion, fts5))
                            Line(
                                stringResource(
                                    Res.string.about_database,
                                    s.database.schemaVersion,
                                    s.database.journalMode,
                                    s.database.quickCheck,
                                ),
                            )
                            Line(stringResource(Res.string.about_counts, s.database.tasks, s.database.pendingOperations))
                        }
                        is AboutState.Failed -> {
                            Header(s.info)
                            Text(
                                stringResource(Res.string.about_failed, s.reason.orEmpty()),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun Header(info: AppInfo) {
    Line(stringResource(Res.string.about_version, info.version))
    Line(stringResource(Res.string.about_platform, info.platform))
}

@Composable
private fun Line(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium)
