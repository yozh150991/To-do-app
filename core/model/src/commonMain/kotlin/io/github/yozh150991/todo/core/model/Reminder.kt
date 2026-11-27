package io.github.yozh150991.todo.core.model

import kotlin.time.Instant

/** spec §4.2 `Reminder.type`. Ф1a uses [RELATIVE] only (the automatic reminder at the due time). */
enum class ReminderType(val wire: String) {
    ABSOLUTE("absolute"),
    RELATIVE("relative"),
    DEADLINE("deadline"),
    INDEPENDENT_RECURRING("independent_recurring"),
    ;

    companion object {
        fun fromWire(value: String): ReminderType =
            entries.firstOrNull { it.wire == value } ?: error("Unknown reminder type: $value")
    }
}

/** spec §4.2 `Reminder.scope` (docs/RECURRENCE_CONTRACT.md §7.1). Ф1a uses [ONE_SHOT] only. */
enum class ReminderScope(val wire: String) {
    ONE_SHOT("one_shot"),
    OCCURRENCE_BOUND("occurrence_bound"),
    TASK_UNTIL_COMPLETED("task_until_completed"),
    ;

    companion object {
        fun fromWire(value: String): ReminderScope =
            entries.firstOrNull { it.wire == value } ?: error("Unknown reminder scope: $value")
    }
}

/**
 * spec §4.2 `Reminder`, synced. `remindAt` is an absolute moment (for [ReminderType.ABSOLUTE]);
 * `offsetMinutes` counts back from the task's due time (for [ReminderType.RELATIVE]; 0 = at the
 * due time). `repeatRule` is independent of `Task.rrule` and stays unused before Ф3.
 */
data class Reminder(
    val id: String,
    val taskId: String,
    val type: ReminderType,
    val scope: ReminderScope = ReminderScope.ONE_SHOT,
    val occurrenceId: String? = null,
    val remindAt: Instant? = null,
    val offsetMinutes: Int? = null,
    val enabled: Boolean = true,
    val isAuto: Boolean = false,
    val repeatRule: String? = null,
    val stopOnTaskComplete: Boolean = true,
    val revision: Long? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
) {
    init {
        when (type) {
            ReminderType.ABSOLUTE -> if (remindAt == null) throw ValidationException("An absolute reminder needs remind_at")
            ReminderType.RELATIVE, ReminderType.DEADLINE ->
                if (offsetMinutes == null || offsetMinutes < 0) {
                    throw ValidationException("A ${type.wire} reminder needs offset_min >= 0, got $offsetMinutes")
                }
            ReminderType.INDEPENDENT_RECURRING -> Unit
        }
    }
}

/**
 * spec §4.2 `ReminderDelivery`: what this device planned and did with one reminder. Local only,
 * never synced; its [id] gives the platform alarm a stable request key.
 */
data class ReminderDelivery(
    val id: String,
    val reminderId: String,
    val occurrenceId: String? = null,
    val deviceId: String,
    val plannedAt: Instant,
    val firedAt: Instant? = null,
    val snoozedUntil: Instant? = null,
    val dismissedAt: Instant? = null,
)

/**
 * spec §4.2 `DeferredTodoistNode` (FIX-02, docs/MIGRATION_TODOIST.md §9.0): a Todoist task left out
 * of the Ф1a import because it is a recurring root or one of its descendants. Metadata only until Ф2
 * imports the whole subgraph at once; there is no local task and no invented `parent_id`.
 */
data class DeferredTodoistNode(
    val sourceId: String,
    val sourceParentId: String?,
    val sourceProjectId: String?,
    val sourceSectionId: String?,
    val rootRecurringSourceId: String,
    val depth: Int,
    val deferredReason: String,
    val sourceHash: String,
    val importRunId: String,
) {
    init {
        if (depth < 0) throw ValidationException("depth must be >= 0, got $depth")
        if ((depth == 0) != (sourceId == rootRecurringSourceId)) {
            throw ValidationException("depth 0 is exactly the recurring root itself")
        }
    }

    companion object {
        const val REASON_RECURRING_ROOT = "recurring_root"
        const val REASON_DESCENDANT_OF_RECURRING = "descendant_of_recurring"
    }
}
