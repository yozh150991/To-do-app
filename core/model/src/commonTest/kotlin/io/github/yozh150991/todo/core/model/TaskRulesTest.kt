package io.github.yozh150991.todo.core.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TaskRulesTest {

    @Test
    fun contentIsTrimmed() {
        assertEquals("Купити молоко", TaskRules.normalizeContent("  Купити молоко \n"))
    }

    @Test
    fun blankContentIsRejected() {
        assertFailsWith<ValidationException> { TaskRules.normalizeContent("   \t\n") }
        assertFailsWith<ValidationException> { TaskRules.normalizeContent("") }
    }

    @Test
    fun priorityLevelsMapBothWays() {
        Priority.entries.forEach { assertEquals(it, Priority.fromLevel(it.level)) }
        assertEquals(1, Priority.P1.level)
        assertFailsWith<ValidationException> { Priority.fromLevel(0) }
        assertFailsWith<ValidationException> { Priority.fromLevel(5) }
    }

    @Test
    fun allDayDueCannotCarryZone() {
        assertFailsWith<ValidationException> {
            Due(date = LocalDate(2026, 11, 2), time = null, zoneId = "Europe/Warsaw")
        }
        // A fixed-zone time and a floating time are both valid.
        Due(date = LocalDate(2026, 11, 2), time = LocalTime(9, 0), zoneId = "Europe/Warsaw")
        Due(date = LocalDate(2026, 11, 2), time = LocalTime(9, 0), zoneId = null)
    }

    @Test
    fun wireNamesRoundTrip() {
        OperationType.entries.forEach { assertEquals(it, OperationType.fromWire(it.wire)) }
        OutboxStatus.entries.forEach { assertEquals(it, OutboxStatus.fromWire(it.wire)) }
        ClaimStatus.entries.forEach { assertEquals(it, ClaimStatus.fromWire(it.wire)) }
        ActivityEvent.entries.forEach { assertEquals(it, ActivityEvent.fromWire(it.wire)) }
        EntityType.entries.forEach { assertEquals(it, EntityType.fromWire(it.wire)) }
        RecurrenceEndReason.entries.forEach { assertEquals(it, RecurrenceEndReason.fromWire(it.wire)) }
    }
}
