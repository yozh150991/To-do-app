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
    /** ADR-005: a span of whole days from [due] on; never together with [durationMinutes]. */
    val durationDays: Int? = null,
    val isCompletable: Boolean = true,
    val completedAt: Instant? = null,
    val recurrenceEndReason: RecurrenceEndReason? = null,
    /** Last server-confirmed revision; `null` while the task has never been acknowledged (locally created). */
    val revision: Long? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
) {
    init {
        TaskRules.checkDuration(durationMinutes, durationDays)
    }

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

/**
 * What happens to the tasks of a deleted project (spec §5.2 «зрозумілий вибір долі задач»).
 * The wire name goes into the `project.delete` payload.
 */
enum class ProjectTaskFate(val wire: String) {
    /** Every open subtree goes to the end of the Inbox, keeping its order and hierarchy. */
    MOVE_TO_INBOX("move_to_inbox"),

    /** The tasks go to the bin together with the project; restoring the project brings them back. */
    DELETE_WITH_PROJECT("delete_with_project"),
    ;

    companion object {
        fun fromWire(value: String): ProjectTaskFate =
            entries.firstOrNull { it.wire == value } ?: error("Unknown task fate: $value")
    }
}

/** Why input was rejected. The UI turns the code into a localized message; the text is for logs. */
enum class ValidationCode {
    BLANK_CONTENT,
    BLANK_NAME,

    /** An explicitly typed one-off time falls into a DST gap (spec §4.0.1 п. 4, TIME-08). */
    NONEXISTENT_LOCAL_TIME,

    /** Deeper than [TaskRules.MAX_DEPTH], a cycle, or a parent in another project. */
    HIERARCHY,

    /** The system Inbox cannot be renamed, moved, archived or deleted (spec §5.2). */
    INBOX_PROTECTED,

    /** No new or moved-in tasks in an archived project. */
    ARCHIVED_PROJECT,

    /** The entity does not exist or is in the bin. */
    NOT_FOUND,
    OTHER,
}

class ValidationException(
    message: String,
    val code: ValidationCode = ValidationCode.OTHER,
) : IllegalArgumentException(message)

object ProjectRules {
    /** Projects need a name after trim, like tasks need content. */
    fun normalizeName(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) throw ValidationException("Project name must not be blank", ValidationCode.BLANK_NAME)
        return trimmed
    }
}

object TaskRules {
    /**
     * Deepest allowed level: a top-level task has depth 0 and sub-tasks go four levels below it
     * (spec §1.2 «до 4 рівнів», boundaries 0/1/4/5 in §12.2), the same as Todoist's four indent
     * levels, so every imported tree fits.
     */
    const val MAX_DEPTH = 4

    /**
     * spec §4.1 `duration_min` and ADR-005 `duration_days`: positive when set, and a task has
     * minutes, days or neither — never both.
     */
    fun checkDuration(minutes: Int?, days: Int?) {
        if (minutes != null && minutes < 1) throw ValidationException("duration_min must be positive, got $minutes")
        if (days != null && days < 1) throw ValidationException("duration_days must be at least 1, got $days")
        if (minutes != null && days != null) {
            throw ValidationException("A task has a duration in minutes or in days, not both (ADR-005)")
        }
    }

    /** spec §4.1: content is mandatory after trim. */
    fun normalizeContent(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) throw ValidationException("Task content must not be blank", ValidationCode.BLANK_CONTENT)
        return trimmed
    }
}
