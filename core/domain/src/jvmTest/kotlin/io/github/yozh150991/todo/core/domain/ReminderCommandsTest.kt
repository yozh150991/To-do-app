package io.github.yozh150991.todo.core.domain

import io.github.yozh150991.todo.core.model.OperationType
import io.github.yozh150991.todo.core.model.ReminderScope
import io.github.yozh150991.todo.core.model.ReminderType
import io.github.yozh150991.todo.core.model.ValidationCode
import io.github.yozh150991.todo.core.model.ValidationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Reminder commands of plan Ф1a §5: what Ф1a accepts, and what each writes to the outbox. */
class ReminderCommandsTest {

    private val f = RepoFixture()

    @AfterTest
    fun tearDown() = f.close()

    @Test
    fun automaticReminderIsOnePerTask() = runBlocking<Unit> {
        val task = f.repo.createTask("Дзвінок")
        val auto = f.repo.reminders.create(task.id, ReminderType.RELATIVE, offsetMinutes = 0, isAuto = true)
        assertEquals(ReminderScope.ONE_SHOT, auto.scope)
        val op = f.opsFor(auto.id).single()
        assertEquals(OperationType.REMINDER_CREATE.wire, op.opType)
        assertEquals(task.id, op.patch()["task_id"]?.jsonPrimitive?.content)
        assertTrue(op.patch()["is_auto"]!!.jsonPrimitive.boolean)
        assertEquals(0, f.store.activities.forTask(auto.id).size, "reminders have no task activity")

        f.rejects(ValidationCode.OTHER) { f.repo.reminders.create(task.id, ReminderType.RELATIVE, offsetMinutes = 10, isAuto = true) }
        val manual = f.repo.reminders.create(task.id, ReminderType.ABSOLUTE, remindAt = Instant.parse("2026-11-22T08:00:00Z"))
        assertEquals(listOf(auto.id, manual.id), f.repo.reminders.forTask(task.id).map { it.id })
    }

    @Test
    fun invalidRemindersWriteNothing() = runBlocking<Unit> {
        val task = f.repo.createTask("Task")
        f.rejects(ValidationCode.OTHER) { f.repo.reminders.create(task.id, ReminderType.RELATIVE) }
        f.rejects(ValidationCode.OTHER) { f.repo.reminders.create(task.id, ReminderType.RELATIVE, offsetMinutes = -5) }
        f.rejects(ValidationCode.OTHER) { f.repo.reminders.create(task.id, ReminderType.ABSOLUTE) }
        f.rejects(ValidationCode.OTHER) { f.repo.reminders.create(task.id, ReminderType.INDEPENDENT_RECURRING) }
        f.repo.delete(task.id)
        f.rejects(ValidationCode.NOT_FOUND) { f.repo.reminders.create(task.id, ReminderType.RELATIVE, offsetMinutes = 0) }
    }

    @Test
    fun updateAndDelete() = runBlocking<Unit> {
        val task = f.repo.createTask("Task")
        val reminder = f.repo.reminders.create(task.id, ReminderType.RELATIVE, offsetMinutes = 0, isAuto = true)

        assertTrue(f.repo.reminders.update(reminder.id, ReminderEdit(offsetMinutes = FieldEdit.Set(15))))
        assertFalse(f.repo.reminders.update(reminder.id, ReminderEdit(offsetMinutes = FieldEdit.Set(15))))
        val op = f.opsFor(reminder.id).last()
        assertEquals(OperationType.REMINDER_UPDATE.wire, op.opType)
        assertEquals(setOf("v", "offset_min"), op.patch().keys)
        assertEquals(15, op.patch()["offset_min"]?.jsonPrimitive?.int)
        assertFailsWith<ValidationException> {
            f.repo.reminders.update(reminder.id, ReminderEdit(offsetMinutes = FieldEdit.Set(null)))
        }

        assertTrue(f.repo.reminders.delete(reminder.id))
        assertFalse(f.repo.reminders.delete(reminder.id))
        assertEquals(emptyList(), f.repo.reminders.forTask(task.id))
        assertEquals(OperationType.REMINDER_DELETE.wire, f.opsFor(reminder.id).last().opType)
        f.rejects(ValidationCode.NOT_FOUND) { f.repo.reminders.update(reminder.id, ReminderEdit(enabled = FieldEdit.Set(false))) }
        // After deleting the automatic reminder the task may get a new one.
        f.repo.reminders.create(task.id, ReminderType.RELATIVE, offsetMinutes = 5, isAuto = true)
    }
}
