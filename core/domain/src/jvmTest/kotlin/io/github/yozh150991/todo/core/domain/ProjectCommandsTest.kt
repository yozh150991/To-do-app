package io.github.yozh150991.todo.core.domain

import io.github.yozh150991.todo.core.model.OperationType
import io.github.yozh150991.todo.core.model.ProjectTaskFate
import io.github.yozh150991.todo.core.model.ValidationCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Project commands of plan Ф1a §5 / PR 5: Inbox rules, nesting, archive, delete with a fate, Undo. */
class ProjectCommandsTest {

    private val fixtures = mutableListOf<RepoFixture>()

    private fun fixture(f: RepoFixture = RepoFixture()) = f.also { fixtures += it }

    @AfterTest
    fun tearDown() = fixtures.forEach { runCatching { it.close() } }

    @Test
    fun createNestsAndAppends() = runBlocking<Unit> {
        val f = fixture()
        val a = f.repo.projects.create("  Робота  ", color = " ")
        val b = f.repo.projects.create("Особисте")
        val child = f.repo.projects.create("Звіти", parentId = a.id)
        assertEquals("Робота", a.name)
        assertNull(a.color, "a blank colour is no colour")
        assertTrue(a.childOrder < b.childOrder)
        assertEquals(a.id, child.parentId)
        assertEquals(listOf(child.id), f.repo.projects.children(a.id).map { it.id })
        assertEquals(OperationType.PROJECT_CREATE.wire, f.opsFor(a.id).single().opType)
        f.rejects(ValidationCode.BLANK_NAME) { f.repo.projects.create("   ") }
        f.rejects(ValidationCode.NOT_FOUND) { f.repo.projects.create("x", parentId = "missing") }
    }

    /** spec §5.2: the system Inbox is not renamed and not deleted; nor moved, archived or nested under. */
    @Test
    fun inboxIsProtected() = runBlocking<Unit> {
        val f = fixture()
        val inbox = f.repo.workspace().inboxId
        val other = f.repo.projects.create("Інший")
        f.rejects(ValidationCode.INBOX_PROTECTED) { f.repo.projects.update(inbox, ProjectEdit(name = FieldEdit.Set("Вхідні"))) }
        f.rejects(ValidationCode.INBOX_PROTECTED) { f.repo.projects.move(inbox, other.id) }
        f.rejects(ValidationCode.INBOX_PROTECTED) { f.repo.projects.archive(inbox) }
        f.rejects(ValidationCode.INBOX_PROTECTED) { f.repo.projects.delete(inbox, ProjectTaskFate.DELETE_WITH_PROJECT) }
        f.rejects(ValidationCode.INBOX_PROTECTED) { f.repo.projects.create("Під Inbox", parentId = inbox) }
        f.rejects(ValidationCode.INBOX_PROTECTED) { f.repo.projects.move(other.id, inbox) }
        assertTrue(f.repo.projects.update(inbox, ProjectEdit(color = FieldEdit.Set("blue"))), "colour is allowed")
    }

    @Test
    fun updateRecordsOnlyChangedFields() = runBlocking<Unit> {
        val f = fixture()
        val p = f.repo.projects.create("Name")
        assertFalse(f.repo.projects.update(p.id, ProjectEdit(name = FieldEdit.Set(" Name "))))
        assertTrue(f.repo.projects.update(p.id, ProjectEdit(name = FieldEdit.Set("New"), isFavorite = FieldEdit.Set(true))))
        val op = f.opsFor(p.id).last()
        assertEquals(OperationType.PROJECT_UPDATE.wire, op.opType)
        assertEquals(setOf("v", "name", "is_favorite"), op.patch().keys)
        assertEquals("New", f.repo.projects.get(p.id)?.name)
    }

    @Test
    fun moveRejectsCycles() = runBlocking<Unit> {
        val f = fixture()
        val a = f.repo.projects.create("a")
        val b = f.repo.projects.create("b", parentId = a.id)
        val c = f.repo.projects.create("c", parentId = b.id)
        f.rejects(ValidationCode.HIERARCHY) { f.repo.projects.move(a.id, c.id) }
        f.rejects(ValidationCode.HIERARCHY) { f.repo.projects.move(a.id, a.id) }
        assertTrue(f.repo.projects.move(c.id, null))
        assertNull(f.project(c.id).parentId)
        assertFalse(f.repo.projects.move(c.id, null))
        assertEquals(setOf("v", "parent_id", "child_order"), f.opsFor(c.id).last().patch().keys)
    }

    @Test
    fun archiveTakesActiveSubprojectsAndUnarchiveOnlyThatGroup() = runBlocking<Unit> {
        val f = fixture()
        val parent = f.repo.projects.create("Parent")
        val earlier = f.repo.projects.create("Archived earlier", parentId = parent.id)
        val together = f.repo.projects.create("Together", parentId = parent.id)
        assertTrue(f.repo.projects.archive(earlier.id))
        assertTrue(f.repo.projects.archive(parent.id))
        assertFalse(f.repo.projects.archive(parent.id))
        assertEquals(f.project(parent.id).archivedAt, f.project(together.id).archivedAt)
        assertEquals(emptyList(), f.repo.projects.observeActiveNow().filter { it.id in setOf(parent.id, together.id) })

        f.rejects(ValidationCode.ARCHIVED_PROJECT) { f.repo.projects.unarchive(together.id) }
        assertTrue(f.repo.projects.unarchive(parent.id))
        assertNull(f.project(together.id).archivedAt)
        assertNotNull(f.project(earlier.id).archivedAt, "archived by another command")
        assertEquals(2, f.ops().count { it.opType == OperationType.PROJECT_UNARCHIVE.wire })
    }

    @Test
    fun deleteMovingTasksToInboxAndUndo() = runBlocking<Unit> {
        val f = fixture()
        val inbox = f.repo.workspace().inboxId
        f.repo.createTask("Already in Inbox")
        val project = f.repo.projects.create("Проєкт")
        val sub = f.repo.projects.create("Підпроєкт", parentId = project.id)
        val t1 = f.repo.createTask("t1", projectId = project.id)
        val t1a = f.repo.createTask("t1a", parentId = t1.id)
        val t2 = f.repo.createTask("t2", projectId = project.id)
        val s1 = f.repo.createTask("s1", projectId = sub.id)
        val tasks = listOf(t1, t1a, t2, s1)
        val before = tasks.map { f.task(it.id).placement() }
        val seq = f.ops().size

        val deletion = assertNotNull(f.repo.projects.delete(project.id, ProjectTaskFate.MOVE_TO_INBOX))
        assertEquals(listOf(project.id, sub.id), deletion.projectIds)
        assertEquals(4, deletion.movedTaskCount)
        assertEquals(3, deletion.moves.size, "one move per subtree: t1 (with t1a), t2, s1")
        assertTrue(tasks.all { f.task(it.id).projectId == inbox })
        assertEquals(t1.id, f.task(t1a.id).parentId, "subtrees stay whole")
        val inboxTop = f.store.tasks.topLevelInProject(inbox).map { it.content }
        assertEquals(listOf("Already in Inbox", "t1", "t2", "s1"), inboxTop, "appended in order")
        val ops = f.ops().drop(seq)
        assertEquals(
            List(4) { OperationType.TASK_MOVE.wire } + List(2) { OperationType.PROJECT_DELETE.wire },
            ops.map { it.opType },
            "task operations first, then one project.delete per project",
        )
        assertEquals(ProjectTaskFate.MOVE_TO_INBOX.wire, ops.last().patch()["task_fate"]?.jsonPrimitive?.content)
        assertNull(f.repo.projects.delete(project.id, ProjectTaskFate.MOVE_TO_INBOX))

        assertTrue(f.repo.projects.undoDelete(deletion))
        assertNull(f.project(project.id).deletedAt)
        assertNull(f.project(sub.id).deletedAt)
        assertEquals(before, tasks.map { f.task(it.id).placement() })
    }

    @Test
    fun deleteWithProjectAndRestore() = runBlocking<Unit> {
        val f = fixture()
        val project = f.repo.projects.create("Проєкт")
        val sub = f.repo.projects.create("Підпроєкт", parentId = project.id)
        val parent = f.repo.createTask("parent", projectId = project.id)
        val child = f.repo.createTask("child", parentId = parent.id)
        val subTask = f.repo.createTask("sub task", projectId = sub.id)
        val binnedEarlier = f.repo.createTask("binned earlier", projectId = project.id)
        f.repo.delete(binnedEarlier.id)

        val deletion = assertNotNull(f.repo.projects.delete(project.id, ProjectTaskFate.DELETE_WITH_PROJECT))
        assertEquals(3, deletion.deletedTaskCount)
        assertTrue(listOf(parent, child, subTask).all { f.task(it.id).deletedAt == f.project(project.id).deletedAt })
        f.rejects(ValidationCode.NOT_FOUND) { f.repo.projects.restore(sub.id) }
        f.rejects(ValidationCode.NOT_FOUND) { f.repo.createTask("into a binned project", projectId = project.id) }

        assertTrue(f.repo.projects.restore(project.id))
        assertNull(f.project(sub.id).deletedAt)
        assertTrue(listOf(parent, child, subTask).all { f.task(it.id).deletedAt == null })
        assertNotNull(f.task(binnedEarlier.id).deletedAt, "binned by another command")
        assertFalse(f.repo.projects.restore(project.id))
    }

    @Test
    fun outboxFailureRollsBackTheWholeDelete() = runBlocking<Unit> {
        val ids = ArmableOperationIds()
        val f = fixture(RepoFixture(operationIds = ids))
        val project = f.repo.projects.create("Проєкт")
        f.repo.createTask("a", projectId = project.id)
        f.repo.createTask("b", projectId = project.id)
        val before = f.snapshot()
        ids.arm()
        assertTrue(runCatching { f.repo.projects.delete(project.id, ProjectTaskFate.MOVE_TO_INBOX) }.isFailure)
        assertEquals(before, f.snapshot())
    }
}

private suspend fun ProjectRepository.observeActiveNow() = observeActive().first()
