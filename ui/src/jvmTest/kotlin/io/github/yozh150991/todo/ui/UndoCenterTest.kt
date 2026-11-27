package io.github.yozh150991.todo.ui

import io.github.yozh150991.todo.core.model.ValidationException
import io.github.yozh150991.todo.ui.tasklist.UndoCenter
import io.github.yozh150991.todo.ui.tasklist.UndoText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/** The one pending Undo of the app (UX_SPEC §4: Undo 6 s, the newest change wins). */
@OptIn(ExperimentalCoroutinesApi::class)
class UndoCenterTest {
    @Test
    fun theNewestChangeReplacesTheOneBefore() = runTest {
        val center = UndoCenter(backgroundScope)
        center.post(UndoText.Completed("first")) {}
        val first = center.pending.value!!
        center.post(UndoText.Binned("second")) {}
        assertEquals(UndoText.Binned("second"), center.pending.value?.text)
        // The old snackbar going away does not drop the new message.
        center.dismiss(first)
        assertEquals(UndoText.Binned("second"), center.pending.value?.text)
    }

    @Test
    fun undoRunsOnceAndClearsTheMessage() = runTest {
        val center = UndoCenter(this)
        var undone = 0
        center.post(UndoText.Completed("task")) { undone++ }
        val message = center.pending.value!!
        center.undo(message)
        center.undo(message)
        advanceUntilIdle()
        assertNull(center.pending.value)
        assertEquals(1, undone, "a second tap does nothing")
    }

    /** A screen that shows the message later gets only what is left of the six seconds. */
    @Test
    fun theWindowRunsFromThePost() = runTest {
        val time = TestTimeSource()
        val center = UndoCenter(backgroundScope, time)
        center.post(UndoText.Updated("task")) {}
        val message = center.pending.value!!
        assertEquals(6.seconds, center.remaining(message))
        time += 4.seconds
        assertEquals(2.seconds, center.remaining(message))
        time += 3.seconds
        assertEquals(Duration.ZERO, center.remaining(message))
    }

    /** Undo is a new command and can fail (the old place is gone); it says so instead of crashing. */
    @Test
    fun aFailedUndoIsReported() = runTest {
        val center = UndoCenter(this)
        center.post(UndoText.Binned("task")) { throw ValidationException("the parent is in the bin") }
        center.undo(center.pending.value!!)
        advanceUntilIdle()
        assertEquals(UndoText.UndoFailed("the parent is in the bin"), center.pending.value?.text)
    }
}
