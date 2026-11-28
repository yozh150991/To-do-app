package io.github.yozh150991.todo.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

/** Domain rules of the schema v3 additions: duration in days (ADR-005), reminders, deferred Todoist nodes. */
class SchemaV3ModelTest {
    private val now = Instant.fromEpochMilliseconds(1_000)

    private fun task(minutes: Int? = null, days: Int? = null) = Task(
        id = "t", projectId = "p", childOrder = "a0", content = "x",
        durationMinutes = minutes, durationDays = days, createdAt = now, updatedAt = now,
    )

    @Test
    fun durationIsMinutesOrDaysOrNeither() {
        assertEquals(30, task(minutes = 30).durationMinutes)
        assertEquals(3, task(days = 3).durationDays)
        task()
        assertFailsWith<ValidationException> { task(minutes = 30, days = 1) }
        assertFailsWith<ValidationException> { task(days = 0) }
        assertFailsWith<ValidationException> { task(minutes = 0) }
    }

    @Test
    fun reminderNeedsItsAnchor() {
        Reminder(id = "r", taskId = "t", type = ReminderType.RELATIVE, offsetMinutes = 0, isAuto = true, createdAt = now, updatedAt = now)
        Reminder(id = "r", taskId = "t", type = ReminderType.ABSOLUTE, remindAt = now, createdAt = now, updatedAt = now)
        assertFailsWith<ValidationException> {
            Reminder(id = "r", taskId = "t", type = ReminderType.ABSOLUTE, createdAt = now, updatedAt = now)
        }
        assertFailsWith<ValidationException> {
            Reminder(id = "r", taskId = "t", type = ReminderType.RELATIVE, offsetMinutes = -5, createdAt = now, updatedAt = now)
        }
    }

    @Test
    fun wireNamesRoundTrip() {
        ReminderType.entries.forEach { assertEquals(it, ReminderType.fromWire(it.wire)) }
        ReminderScope.entries.forEach { assertEquals(it, ReminderScope.fromWire(it.wire)) }
        assertEquals(EntityType.REMINDER, EntityType.fromWire("reminder"))
    }

    @Test
    fun deferredRootIsDepthZero() {
        DeferredTodoistNode("a", null, "p", null, "a", 0, DeferredTodoistNode.REASON_RECURRING_ROOT, "h", "run")
        DeferredTodoistNode("b", "a", "p", null, "a", 1, DeferredTodoistNode.REASON_DESCENDANT_OF_RECURRING, "h", "run")
        assertFailsWith<ValidationException> {
            DeferredTodoistNode("b", "a", "p", null, "a", 0, DeferredTodoistNode.REASON_RECURRING_ROOT, "h", "run")
        }
        assertFailsWith<ValidationException> {
            DeferredTodoistNode("a", null, "p", null, "a", 1, DeferredTodoistNode.REASON_RECURRING_ROOT, "h", "run")
        }
    }
}
