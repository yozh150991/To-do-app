package io.github.yozh150991.todo.core.domain

import io.github.yozh150991.todo.core.database.entity.SectionEntity
import io.github.yozh150991.todo.core.model.ActivityEvent
import io.github.yozh150991.todo.core.model.Due
import io.github.yozh150991.todo.core.model.OperationPayload
import io.github.yozh150991.todo.core.model.OperationType
import io.github.yozh150991.todo.core.model.Priority
import io.github.yozh150991.todo.core.model.TaskRules
import io.github.yozh150991.todo.core.model.TimeZones
import io.github.yozh150991.todo.core.model.ValidationCode
import io.github.yozh150991.todo.core.model.ValidationException
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Task commands of plan Ф1a §5: fields, sub-tasks, moves, the bin, and what each writes. */
class TaskCommandsTest {

    private val fixtures = mutableListOf<RepoFixture>()

    private fun fixture(f: RepoFixture = RepoFixture()) = f.also { fixtures += it }

    @AfterTest
    fun tearDown() = fixtures.forEach { runCatching { it.close() } }

    @Test
    fun createWritesVersionedSnapshotWithAllFields() = runBlocking<Unit> {
        val f = fixture()
        val task = f.repo.createTask(
            "Звіт",
            priority = Priority.P2,
            due = Due(LocalDate(2026, 11, 23), LocalTime(9, 0), "Europe/Warsaw"),
            description = "Квартальний",
            deadline = LocalDate(2026, 11, 30),
            durationDays = 2,
        )
        val op = f.opsFor(task.id).single()
        assertEquals(OperationType.TASK_CREATE.wire, op.opType)
        val patch = op.patch()
        assertEquals(OperationPayload.VERSION, patch[OperationPayload.KEY]?.jsonPrimitive?.int)
        assertEquals(OperationPayload.VERSION, OperationPayload.versionOf(patch))
        assertEquals("Квартальний", patch["description"]?.jsonPrimitive?.content)
        assertEquals("2026-11-30", patch["deadline"]?.jsonPrimitive?.content)
        assertEquals(2, patch["duration_days"]?.jsonPrimitive?.int)
        assertEquals(JsonNull, patch["duration_min"])
        assertEquals("09:00", patch["due_time"]?.jsonPrimitive?.content)
        assertEquals(ActivityEvent.CREATED.wire, f.store.activities.forTask(task.id).single().eventType)
        assertEquals(task, f.repo.getTask(task.id))
    }

    @Test
    fun bootstrapPayloadIsVersionedToo() = runBlocking<Unit> {
        val f = fixture()
        f.repo.workspace()
        val bootstrap = f.ops().single()
        assertEquals(OperationType.PROJECT_CREATE.wire, bootstrap.opType)
        assertEquals(OperationPayload.VERSION, bootstrap.patch()[OperationPayload.KEY]?.jsonPrimitive?.int)
    }

    @Test
    fun subtasksJoinTheParentsProjectAndAppend() = runBlocking<Unit> {
        val f = fixture()
        val project = f.repo.projects.create("Дім")
        val parent = f.repo.createTask("Ремонт", projectId = project.id)
        val first = f.repo.createTask("Фарба", parentId = parent.id)
        val second = f.repo.createTask("Пензлі", parentId = parent.id)

        assertEquals(project.id, first.projectId)
        assertEquals(parent.id, second.parentId)
        assertTrue(first.childOrder < second.childOrder)
        assertEquals(listOf("Фарба", "Пензлі"), f.repo.subtasks(parent.id).map { it.content })
        val inbox = f.repo.workspace().inboxId
        f.rejects(ValidationCode.HIERARCHY) { f.repo.createTask("Не туди", projectId = inbox, parentId = parent.id) }
    }

    /** spec §12.2 boundaries 0/1/4/5. */
    @Test
    fun subtasksStopAtMaxDepth() = runBlocking<Unit> {
        val f = fixture()
        var parent = f.repo.createTask("level 0")
        repeat(TaskRules.MAX_DEPTH) { level -> parent = f.repo.createTask("level ${level + 1}", parentId = parent.id) }
        f.rejects(ValidationCode.HIERARCHY) { f.repo.createTask("level 5", parentId = parent.id) }
    }

    @Test
    fun updateRecordsOnlyChangedFieldsWithTheirEvent() = runBlocking<Unit> {
        val f = fixture()
        val task = f.repo.createTask("Task", deadline = LocalDate(2026, 12, 1))

        assertTrue(f.repo.updateTask(task.id, TaskEdit(priority = FieldEdit.Set(Priority.P1))))
        var op = f.opsFor(task.id).last()
        assertEquals(OperationType.TASK_UPDATE.wire, op.opType)
        assertEquals(setOf("v", "priority"), op.patch().keys)
        assertEquals(1, op.patch()["priority"]?.jsonPrimitive?.int)

        assertTrue(f.repo.updateTask(task.id, TaskEdit(due = FieldEdit.Set(Due(LocalDate(2026, 11, 25))))))
        op = f.opsFor(task.id).last()
        assertEquals(setOf("v", "due_date", "due_time", "due_tz"), op.patch().keys, "due columns travel together")

        assertTrue(f.repo.updateTask(task.id, TaskEdit(deadline = FieldEdit.Set(null), description = FieldEdit.Set("x"))))
        op = f.opsFor(task.id).last()
        assertEquals(JsonNull, op.patch()["deadline"])

        val events = f.store.activities.forTask(task.id).map { it.eventType }
        assertEquals(
            listOf(ActivityEvent.CREATED, ActivityEvent.PRIORITY_CHANGED, ActivityEvent.DUE_CHANGED, ActivityEvent.UPDATED).map { it.wire },
            events,
        )
        val opsBefore = f.ops().size
        assertFalse(f.repo.updateTask(task.id, TaskEdit(priority = FieldEdit.Set(Priority.P1), content = FieldEdit.Set(" Task "))))
        assertEquals(opsBefore, f.ops().size, "a no-op edit records nothing")
        assertEquals(Priority.P1, f.repo.getTask(task.id)?.priority)
        assertNull(f.repo.getTask(task.id)?.deadline)
    }

    /** ADR-005: minutes or days, never both. */
    @Test
    fun durationInMinutesOrDays() = runBlocking<Unit> {
        val f = fixture()
        val task = f.repo.createTask("Відпустка", durationDays = 5)
        f.rejects(ValidationCode.OTHER) { f.repo.updateTask(task.id, TaskEdit(durationMinutes = FieldEdit.Set(30))) }
        assertTrue(f.repo.updateTask(task.id, TaskEdit(durationMinutes = FieldEdit.Set(30), durationDays = FieldEdit.Set(null))))
        assertEquals(30, f.repo.getTask(task.id)?.durationMinutes)
        assertNull(f.repo.getTask(task.id)?.durationDays)
        assertEquals(ActivityEvent.DURATION_CHANGED.wire, f.store.activities.forTask(task.id).last().eventType)
        assertFailsWith<ValidationException> { f.repo.createTask("Обидва", durationMinutes = 10, durationDays = 1) }
    }

    /** TIME-08: a typed one-off time in the DST gap is an error with choices, never a silent shift. */
    @Test
    fun explicitTimeInDstGapIsRejected() = runBlocking<Unit> {
        val gapDay = LocalDate(2026, 3, 29)
        val f = fixture(RepoFixture(zone = TimeZones.of("Europe/Warsaw")))
        val task = f.repo.createTask("Будильник")
        f.rejects(ValidationCode.NONEXISTENT_LOCAL_TIME) {
            f.repo.createTask("Fixed", due = Due(gapDay, LocalTime(2, 30), "Europe/Warsaw"))
        }
        f.rejects(ValidationCode.NONEXISTENT_LOCAL_TIME) {
            f.repo.updateTask(task.id, TaskEdit(due = FieldEdit.Set(Due(gapDay, LocalTime(2, 30)))))
        }
        // The same floating time exists in a zone without that gap.
        val utc = fixture(RepoFixture(zone = TimeZones.of("UTC")))
        assertNotNull(utc.repo.createTask("Floating", due = Due(gapDay, LocalTime(2, 30))))
    }

    @Test
    fun moveToAnotherProjectTakesTheWholeSubtreeAndUndoPutsItBack() = runBlocking<Unit> {
        val f = fixture()
        val home = f.repo.projects.create("Дім")
        val work = f.repo.projects.create("Робота")
        val root = f.repo.createTask("Root", projectId = home.id)
        val child = f.repo.createTask("Child", parentId = root.id)
        val grandchild = f.repo.createTask("Grandchild", parentId = child.id)
        // An imported section (the Ф1a UI has none): the subtree sits in it.
        f.store.sections.insert(SectionEntity("s1", home.id, "Секція", "", "a0", false, null, 0, 0, null))
        listOf(root, child, grandchild).forEach { f.store.tasks.update(f.task(it.id).copy(sectionId = "s1")) }
        val before = listOf(root, child, grandchild).map { f.task(it.id).placement() }
        val seqBefore = f.ops().size

        val move = assertNotNull(f.repo.moveTask(root.id, projectId = work.id))
        assertEquals(3, move.previous.size)
        for (id in listOf(root.id, child.id, grandchild.id)) {
            val row = f.task(id)
            assertEquals(work.id, row.projectId)
            assertNull(row.sectionId, "the section belongs to the old project")
        }
        assertEquals(child.id, f.task(grandchild.id).parentId)
        val moveOps = f.ops().drop(seqBefore)
        assertEquals(List(3) { OperationType.TASK_MOVE.wire }, moveOps.map { it.opType })
        assertEquals((seqBefore + 1L..seqBefore + 3L).toList(), moveOps.map { it.localSeq })
        assertEquals(ActivityEvent.MOVED.wire, f.store.activities.forTask(grandchild.id).last().eventType)

        assertTrue(f.repo.undoMove(move))
        assertEquals(before, listOf(root, child, grandchild).map { f.task(it.id).placement() })
        assertNull(f.repo.moveTask(root.id, projectId = home.id), "already there: nothing to do")
    }

    @Test
    fun moveRejectsCyclesDepthAndArchivedProjects() = runBlocking<Unit> {
        val f = fixture()
        val a = f.repo.createTask("a")
        val b = f.repo.createTask("b", parentId = a.id)
        val c = f.repo.createTask("c", parentId = b.id)
        f.rejects(ValidationCode.HIERARCHY) { f.repo.moveTask(a.id, parentId = c.id) }
        f.rejects(ValidationCode.HIERARCHY) { f.repo.moveTask(a.id, parentId = a.id) }

        // a-b-c has height 2: under z (depth 2) c would land at depth 5, under y (depth 1) at 4.
        val x = f.repo.createTask("x")
        val y = f.repo.createTask("y", parentId = x.id)
        val z = f.repo.createTask("z", parentId = y.id)
        f.rejects(ValidationCode.HIERARCHY) { f.repo.moveTask(a.id, parentId = z.id) }
        assertNotNull(f.repo.moveTask(a.id, parentId = y.id), "the deepest row at depth 4 is allowed")
        assertEquals(TaskRules.MAX_DEPTH, f.store.depthOf(f.task(c.id)))

        val archived = f.repo.projects.create("Архів")
        f.repo.projects.archive(archived.id)
        f.rejects(ValidationCode.ARCHIVED_PROJECT) { f.repo.moveTask(x.id, projectId = archived.id) }
        f.rejects(ValidationCode.ARCHIVED_PROJECT) { f.repo.createTask("new", projectId = archived.id) }
    }

    @Test
    fun promotingASubtaskKeepsItsProjectAndSection() = runBlocking<Unit> {
        val f = fixture()
        val parent = f.repo.createTask("Parent")
        val child = f.repo.createTask("Child", parentId = parent.id)
        val move = assertNotNull(f.repo.moveTask(child.id))
        assertNull(f.task(child.id).parentId)
        assertEquals(parent.projectId, f.task(child.id).projectId)
        assertTrue(f.task(child.id).childOrder > f.task(parent.id).childOrder, "goes after the last top-level task")
        assertEquals(1, move.previous.size)
    }

    @Test
    fun deleteBinsTheSubtreeAndRestoreBringsBackOnlyThatGroup() = runBlocking<Unit> {
        val f = fixture()
        val parent = f.repo.createTask("Parent")
        val kept = f.repo.createTask("Kept", parentId = parent.id)
        val earlier = f.repo.createTask("Binned earlier", parentId = parent.id)
        assertTrue(f.repo.delete(earlier.id))
        assertTrue(f.repo.delete(parent.id))
        assertNotNull(f.task(kept.id).deletedAt)
        assertEquals(f.task(parent.id).deletedAt, f.task(kept.id).deletedAt, "one command, one moment")
        assertFalse(f.repo.delete(parent.id))

        f.rejects(ValidationCode.HIERARCHY) { f.repo.restore(kept.id) }
        assertTrue(f.repo.restore(parent.id))
        assertNull(f.task(kept.id).deletedAt)
        assertNotNull(f.task(earlier.id).deletedAt, "binned by another command, stays in the bin")
        assertTrue(f.repo.restore(earlier.id))
        val restores = f.ops().filter { it.opType == OperationType.TASK_RESTORE.wire }
        assertEquals(3, restores.size)
    }

    @Test
    fun binnedSubtasksMoveWithTheirParent() = runBlocking<Unit> {
        val f = fixture()
        val other = f.repo.projects.create("Інший")
        val parent = f.repo.createTask("Parent")
        val binned = f.repo.createTask("Binned", parentId = parent.id)
        f.repo.delete(binned.id)
        f.repo.moveTask(parent.id, projectId = other.id)
        assertEquals(other.id, f.task(binned.id).projectId, "a later restore must not find it in another project")
        assertTrue(f.repo.restore(binned.id))
    }

    @Test
    fun outboxFailureInAMultiRowCommandRollsBackEveryRow() = runBlocking<Unit> {
        val ids = ArmableOperationIds()
        val f = fixture(RepoFixture(operationIds = ids))
        val other = f.repo.projects.create("Інший")
        val parent = f.repo.createTask("Parent")
        f.repo.createTask("Child", parentId = parent.id)
        val before = f.snapshot()
        ids.arm() // the second task.move reuses the first operation ID
        assertTrue(runCatching { f.repo.moveTask(parent.id, projectId = other.id) }.isFailure)
        assertEquals(before, f.snapshot(), "the first row's move must be rolled back too")
    }
}
