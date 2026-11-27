package io.github.yozh150991.todo.ui.tasklist

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import io.github.yozh150991.todo.core.model.ValidationException
import io.github.yozh150991.todo.ui.resources.Res
import io.github.yozh150991.todo.ui.resources.inbox_title
import io.github.yozh150991.todo.ui.resources.snackbar_binned
import io.github.yozh150991.todo.ui.resources.snackbar_completed
import io.github.yozh150991.todo.ui.resources.snackbar_moved
import io.github.yozh150991.todo.ui.resources.snackbar_scheduled
import io.github.yozh150991.todo.ui.resources.snackbar_undo
import io.github.yozh150991.todo.ui.resources.snackbar_undo_failed
import io.github.yozh150991.todo.ui.resources.snackbar_updated
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.getString
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** What the snackbar says about a change (UX_SPEC §5.5): "Completed: Buy milk · Undo". */
sealed interface UndoText {
    data class Completed(val title: String) : UndoText

    data class Binned(val title: String) : UndoText

    /** A field changed in the details: name, description, priority, time, deadline. */
    data class Updated(val title: String) : UndoText

    /** The date changed (swipe left, the date field). */
    data class Scheduled(val title: String) : UndoText

    /** Moved to another project; [project] null is the Inbox. */
    data class Moved(val title: String, val project: String?) : UndoText

    /** Undo itself failed (the old place is gone, the database is unavailable): said once, no action. */
    data class UndoFailed(val reason: String?) : UndoText
}

/** One change that can be taken back. [undo] null: a message without the action. */
class UndoMessage internal constructor(
    val text: UndoText,
    internal val undo: (suspend () -> Unit)?,
    internal val postedAt: TimeMark,
)

/**
 * The single pending Undo of the app (UX_SPEC §4: Undo 6 s). The newest change replaces the one
 * before, whichever screen made it, so there is never more than one snackbar. A snackbar host
 * shows it — the list's, or on a phone the details' — and a host that comes later (the list after
 * the details closed) shows only what is left of the six seconds. Undo runs in [scope], not in a
 * screen's, so it finishes even when the screen closes meanwhile.
 */
class UndoCenter(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val _pending = MutableStateFlow<UndoMessage?>(null)
    val pending: StateFlow<UndoMessage?> = _pending.asStateFlow()

    /** A change the user can take back with [undo]. */
    fun post(text: UndoText, undo: suspend () -> Unit) {
        _pending.value = UndoMessage(text, undo, timeSource.markNow())
    }

    /** A message without the Undo action. */
    fun inform(text: UndoText) {
        _pending.value = UndoMessage(text, null, timeSource.markNow())
    }

    /** How much of the Undo window is left for [message]; zero once it is over. */
    fun remaining(message: UndoMessage): Duration = (WINDOW - message.postedAt.elapsedNow()).coerceAtLeast(Duration.ZERO)

    /** The snackbar went away without Undo. A newer message stays. */
    fun dismiss(message: UndoMessage) {
        _pending.compareAndSet(message, null)
    }

    /** Takes the change back; Undo is a new command (plan Ф1a §5), so it can fail and says so. */
    fun undo(message: UndoMessage) {
        // Once: a second tap, or a message already replaced by a newer change, does nothing.
        if (!_pending.compareAndSet(message, null)) return
        val action = message.undo ?: return
        scope.launch {
            try {
                action()
            } catch (e: ValidationException) {
                inform(UndoText.UndoFailed(e.message))
            } catch (e: IllegalStateException) {
                inform(UndoText.UndoFailed(e.message))
            }
        }
    }

    companion object {
        val WINDOW: Duration = 6.seconds
    }
}

/**
 * Whether the task details sit next to a list that shows the snackbars (expanded width, UX_SPEC
 * §2.2). On their own the details show them.
 */
val LocalDetailBesideList = compositionLocalOf { false }

suspend fun UndoText.message(): String = when (this) {
    is UndoText.Completed -> getString(Res.string.snackbar_completed, title)
    is UndoText.Binned -> getString(Res.string.snackbar_binned, title)
    is UndoText.Updated -> getString(Res.string.snackbar_updated, title)
    is UndoText.Scheduled -> getString(Res.string.snackbar_scheduled, title)
    is UndoText.Moved -> getString(Res.string.snackbar_moved, title, project ?: getString(Res.string.inbox_title))
    is UndoText.UndoFailed -> getString(Res.string.snackbar_undo_failed, reason.orEmpty()).trim()
}

/** Shows the pending Undo of [center] in [snackbar] for what is left of its six seconds. */
@Composable
fun UndoSnackbars(center: UndoCenter, snackbar: SnackbarHostState) {
    LaunchedEffect(center, snackbar) {
        center.pending.collectLatest { message ->
            if (message == null) return@collectLatest
            val left = center.remaining(message)
            if (left == Duration.ZERO) {
                center.dismiss(message)
                return@collectLatest
            }
            val text = message.text.message()
            val action = if (message.undo != null) getString(Res.string.snackbar_undo) else null
            val result = withTimeoutOrNull(left) {
                snackbar.showSnackbar(message = text, actionLabel = action, duration = SnackbarDuration.Indefinite)
            }
            if (result == SnackbarResult.ActionPerformed) center.undo(message) else center.dismiss(message)
        }
    }
}
