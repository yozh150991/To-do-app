package io.github.yozh150991.todo.core.domain

import io.github.yozh150991.todo.core.database.entity.ReminderEntity
import io.github.yozh150991.todo.core.database.toDomain
import io.github.yozh150991.todo.core.database.toEntity
import io.github.yozh150991.todo.core.model.OperationType
import io.github.yozh150991.todo.core.model.Reminder
import io.github.yozh150991.todo.core.model.ReminderScope
import io.github.yozh150991.todo.core.model.ReminderType
import io.github.yozh150991.todo.core.model.ValidationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

/**
 * Reminder definitions (spec §4.2 `Reminder`, synced; plan Ф1a §5 `reminder.create/update/delete`).
 * Reached as [TaskRepository.reminders]. Planned alarms (`reminder_delivery`) are local and belong
 * to the platform scheduler (plan Ф1a PR 7), not to this class.
 *
 * Before the engines exist the commands accept only what they can honour: one-shot reminders,
 * no own repeat rule (Ф3), no occurrence binding (Ф2), and at most one automatic reminder per task.
 */
class ReminderRepository internal constructor(private val mutations: Mutations) {

    /** Reminders of a task that are not deleted, oldest first. */
    suspend fun forTask(taskId: String): List<Reminder> =
        mutations.store().reminders.forTask(taskId).map(ReminderEntity::toDomain)

    suspend fun get(reminderId: String): Reminder? = mutations.store().reminders.getById(reminderId)?.toDomain()

    /**
     * Adds a reminder to a task. [ReminderType.RELATIVE] and [ReminderType.DEADLINE] need
     * [offsetMinutes] (minutes before the due time, or before the deadline's reminder hour);
     * [ReminderType.ABSOLUTE] needs [remindAt]. [isAuto] marks the automatic reminder that follows
     * the settings (spec §5.6).
     */
    suspend fun create(
        taskId: String,
        type: ReminderType,
        offsetMinutes: Int? = null,
        remindAt: Instant? = null,
        isAuto: Boolean = false,
    ): Reminder {
        if (type == ReminderType.INDEPENDENT_RECURRING) {
            throw ValidationException("Reminders with their own repeat rule come with phase Ф3")
        }
        return mutations.write {
            requireLiveTask(taskId)
            if (isAuto && store.reminders.forTask(taskId).any { it.isAuto }) {
                throw ValidationException("A task has at most one automatic reminder")
            }
            val reminder = Reminder(
                id = mutations.ids.newId(),
                taskId = taskId,
                type = type,
                scope = ReminderScope.ONE_SHOT,
                remindAt = remindAt,
                offsetMinutes = offsetMinutes,
                isAuto = isAuto,
                createdAt = now,
                updatedAt = now,
            )
            store.reminders.insert(reminder.toEntity())
            record(OperationType.REMINDER_CREATE, reminder.id, reminder.snapshot(), null)
            reminder
        }
    }

    /** Moment, offset or on/off. False when nothing changes. */
    suspend fun update(reminderId: String, edit: ReminderEdit): Boolean = mutations.write {
        val row = store.reminders.getById(reminderId) ?: notFound("Reminder $reminderId")
        if (row.deletedAt != null) notFound("Reminder $reminderId")
        val current = row.toDomain()
        val next = current.copy(
            remindAt = edit.remindAt.applyTo(current.remindAt),
            offsetMinutes = edit.offsetMinutes.applyTo(current.offsetMinutes),
            enabled = edit.enabled.applyTo(current.enabled),
        ) // Reminder's init checks the fields its type needs.
        val changes = FieldChanges()
        changes.diff("remind_at", current.remindAt?.toString(), next.remindAt?.toString()) { jsonOf(it) }
        changes.diff("offset_min", current.offsetMinutes, next.offsetMinutes) { jsonOf(it) }
        changes.diff("enabled", current.enabled, next.enabled) { jsonOf(it) }
        if (changes.isEmpty) return@write false
        store.reminders.update(next.copy(updatedAt = now).toEntity())
        record(OperationType.REMINDER_UPDATE, reminderId, changes.patch(), row.revision)
        true
    }

    /** Soft delete. False when already deleted. */
    suspend fun delete(reminderId: String): Boolean = mutations.write {
        val row = store.reminders.getById(reminderId) ?: notFound("Reminder $reminderId")
        if (row.deletedAt != null) return@write false
        store.reminders.update(row.copy(deletedAt = nowMillis, updatedAt = nowMillis))
        record(OperationType.REMINDER_DELETE, reminderId, buildJsonObject { put("deleted_at", now.toString()) }, row.revision)
        true
    }

    private fun Reminder.snapshot(): JsonObject = buildJsonObject {
        put("id", id)
        put("task_id", taskId)
        put("type", type.wire)
        put("scope", scope.wire)
        put("occurrence_id", occurrenceId)
        put("remind_at", remindAt?.toString())
        put("offset_min", offsetMinutes)
        put("enabled", enabled)
        put("is_auto", isAuto)
        put("repeat_rule", repeatRule)
        put("stop_on_task_complete", stopOnTaskComplete)
        put("created_at", createdAt.toString())
    }
}
