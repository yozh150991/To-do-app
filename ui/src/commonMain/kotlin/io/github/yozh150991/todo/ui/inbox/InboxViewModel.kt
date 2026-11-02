package io.github.yozh150991.todo.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yozh150991.todo.core.domain.TaskRepository
import io.github.yozh150991.todo.core.model.Task
import io.github.yozh150991.todo.core.model.ValidationException
import io.github.yozh150991.todo.ui.Strings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class InboxUiState(
    val tasks: List<Task> = emptyList(),
    val pendingSyncOperations: Int = 0,
    val loading: Boolean = true,
)

/** Ф0 proof screen: create, list and complete tasks in the Inbox through the repository only. */
class InboxViewModel(private val repository: TaskRepository) : ViewModel() {

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val state: StateFlow<InboxUiState> = combine(
        repository.observeInbox(),
        repository.observePendingOperationCount(),
    ) { tasks, pending -> InboxUiState(tasks = tasks, pendingSyncOperations = pending, loading = false) }
        // A database that cannot be opened (e.g. a rolled-back upgrade) is shown, never a crash.
        .catch { failure ->
            _error.value = Strings.databaseUnavailable(failure.message)
            emit(InboxUiState(loading = false))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InboxUiState())

    fun addTask(content: String) {
        viewModelScope.launch {
            try {
                repository.createTask(content)
                _error.value = null
            } catch (e: ValidationException) {
                _error.value = e.message
            } catch (e: IllegalStateException) {
                _error.value = Strings.databaseUnavailable(e.message)
            }
        }
    }

    fun complete(taskId: String) {
        viewModelScope.launch {
            try {
                repository.complete(taskId)
            } catch (e: IllegalStateException) {
                _error.value = Strings.databaseUnavailable(e.message)
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }
}
