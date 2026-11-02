package io.github.yozh150991.todo.core.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

/** p1 is the highest priority (spec §4.1: `priority` int 1..4, `1` = p1). */
enum class Priority(val level: Int) {
    P1(1),
    P2(2),
    P3(3),
    P4(4),
    ;

    companion object {
        val DEFAULT = P4

        fun fromLevel(level: Int): Priority =
            entries.firstOrNull { it.level == level }
                ?: throw ValidationException("Priority must be 1..4, got $level")
    }
}

/**
 * Planned date of a task (spec §4 "Часова модель").
 * `time == null` means an all-day task; `zoneId == null` with a time means floating local time.
 * A calendar date is never converted to a UTC midnight instant.
 */
data class Due(
    val date: LocalDate,
    val time: LocalTime? = null,
    val zoneId: String? = null,
) {
    init {
        if (zoneId != null && time == null) {
            throw ValidationException("A time zone requires a time; all-day dates have no zone")
        }
    }
}

/** spec §4.1 `recurrence_end_reason` (FIX-05: defined exactly once). */
enum class RecurrenceEndReason(val wire: String) {
    EXHAUSTED_COMPLETED("exhausted_completed"),
    EXHAUSTED_SKIPPED("exhausted_skipped"),
    MANUALLY_STOPPED("manually_stopped"),
    ;

    companion object {
        fun fromWire(value: String?): RecurrenceEndReason? =
            value?.let { v -> entries.firstOrNull { it.wire == v } ?: error("Unknown recurrence end reason: $v") }
    }
}

/**
 * Domain view of a task. Recurrence fields exist in the schema from v1 but have no
 * behaviour before phase Ф2 (spec §5.5); they are carried through untouched.
 */
data class Task(
    val id: String,
    val projectId: String,
    val sectionId: String? = null,
    val parentId: String? = null,
    val childOrder: String,
    val content: String,
    val description: String = "",
    val sourceUrl: String? = null,
    val priority: Priority = Priority.DEFAULT,
    val due: Due? = null,
    val deadline: LocalDate? = null,
    val durationMinutes: Int? = null,
    val isCompletable: Boolean = true,
    val completedAt: Instant? = null,
    val recurrenceEndReason: RecurrenceEndReason? = null,
    /** Last server-confirmed revision; `null` while the task has never been acknowledged (locally created). */
    val revision: Long? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
) {
    val isCompleted: Boolean get() = completedAt != null
    val isDeleted: Boolean get() = deletedAt != null
}

data class Project(
    val id: String,
    val name: String,
    val description: String = "",
    val color: String? = null,
    val parentId: String? = null,
    val childOrder: String,
    val isInbox: Boolean,
    val isFavorite: Boolean = false,
    val archivedAt: Instant? = null,
    val revision: Long? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
)

class ValidationException(message: String) : IllegalArgumentException(message)

object TaskRules {
    /** spec §4.1: content is mandatory after trim. */
    fun normalizeContent(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) throw ValidationException("Task content must not be blank")
        return trimmed
    }
}
