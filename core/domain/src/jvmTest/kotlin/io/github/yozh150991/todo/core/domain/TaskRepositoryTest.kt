package io.github.yozh150991.todo.core.domain

import app.cash.turbine.test
import io.github.yozh150991.todo.core.database.LocalStore
import io.github.yozh150991.todo.core.database.createAppDatabase
import io.github.yozh150991.todo.core.model.IdGenerator
import io.github.yozh150991.todo.core.model.OperationType
import io.github.yozh150991.todo.core.model.OutboxStatus
import io.github.yozh150991.todo.core.model.Priority
import io.github.yozh150991.todo.core.model.RandomUuidGenerator
import io.github.yozh150991.todo.core.model.ValidationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskRepositoryTest {

    private val file = File(Files.createTempDirectory("repo").toFile(), "todo.db")
    private val opened = mutableListOf<LocalStore>()

    private fun openStore(): LocalStore = LocalStore(createAppDatabase(file)).also { opened += it }

    private fun repository(
        store: LocalStore = openStore(),
        operationIds: IdGenerator = RandomUuidGenerator,
    ) = TaskRepository(store = store, operationIds = operationIds)

    @AfterTest
    fun tearDown() {
        opened.forEach { runCatching { it.close() } }
    }

    @Test
    fun bootstrapCreatesOneWorkspaceWithInboxEvenUnderConcurrency() = runBlocking<Unit> {
        val store = openStore()
        val repo = repository(store)
        val results = (1..20).map { async(Dispatchers.Default) { repo.workspace() } }.awaitAll()

        assertEquals(1, results.map { it.workspaceId }.toSet().size)
        assertEquals(1, store.projects.count())
        val inbox = assertNotNull(store.projects.getInbox())
        assertEquals(results.first().inboxId, inbox.id)
        assertEquals(results.first().accountScope, "local:${results.first().workspaceId}")

        val ops = store.outbox.all()
        assertEquals(listOf(OperationType.PROJECT_CREATE.wire), ops.map { it.opType })
        assertEquals(1L, ops.single().localSeq)
    }

    @Test
    fun createTaskWritesEntityActivityAndOutboxTogether() = runBlocking<Unit> {
        val store = openStore()
        val repo = repository(store)
        val task = repo.createTask("  Купити молоко  ", priority = Priority.P1)

        assertEquals("Купити молоко", task.content)
        assertEquals(Priority.P1, store.tasks.getById(task.id)?.let { Priority.fromLevel(it.priority) })
        assertEquals(1, store.activities.forTask(task.id).size)

        val op = store.outbox.forEntity("task", task.id).single()
        assertEquals(OperationType.TASK_CREATE.wire, op.opType)
        assertEquals(OutboxStatus.PENDING.wire, op.status)
        assertNull(op.baseRevision, "A locally created task has no server base revision")
        val patch = repo.pendingOperations().first { it.entityId == task.id }.fieldPatch
        assertEquals("Купити молоко", patch["content"]?.jsonPrimitive?.content)
        assertEquals(2L, op.localSeq, "Seq 1 belongs to the Inbox bootstrap")
    }

    @Test
    fun blankContentWritesNothing() = runBlocking<Unit> {
        val store = openStore()
        val repo = repository(store)
        repo.workspace()
        val before = store.outbox.count()

        assertFailsWith<ValidationException> { repo.createTask("   ") }
        assertEquals(0, store.tasks.count())
        assertEquals(before, store.outbox.count())
    }

    @Test
    fun unknownProjectWritesNothing() = runBlocking<Unit> {
        val store = openStore()
        val repo = repository(store)
        repo.workspace()
        val before = store.outbox.count()

        assertFailsWith<ValidationException> { repo.createTask("Task", projectId = "missing") }
        assertEquals(0, store.tasks.count())
        assertEquals(before, store.outbox.count())
    }

    /** Ф0 gate: "Транзакція Entity + Outbox проходить rollback/crash тест". */
    @Test
    fun outboxFailureRollsBackEntityAndActivity() = runBlocking<Unit> {
        val store = openStore()
        // Every operation gets the same ID, so the second outbox insert violates the primary key
        // after the task row and the activity row were already written in that transaction.
        val repo = repository(store, operationIds = IdGenerator { "duplicate-operation-id" })
        repo.workspace() // consumes the ID for the Inbox operation

        val seqBefore = store.workspaces.lastLocalSeq(repo.workspace().workspaceId)
        val result = runCatching { repo.createTask("Must not survive") }

        assertTrue(result.isFailure, "Insert with a duplicate operation_id must fail")
        assertEquals(0, store.tasks.count(), "Task row must be rolled back")
        assertEquals(0, store.activities.count(), "Activity row must be rolled back")
        assertEquals(1, store.outbox.count(), "Only the Inbox bootstrap operation remains")
        assertEquals(seqBefore, store.workspaces.lastLocalSeq(repo.workspace().workspaceId), "local_seq must not advance")
    }

    @Test
    fun exceptionInsideWriteRollsBackEverything() = runBlocking<Unit> {
        val store = openStore()
        val repo = repository(store)
        val task = repo.createTask("Survivor")
        val opsBefore = store.outbox.count()

        val failure = runCatching {
            store.write {
                val row = assertNotNull(tasks.getById(task.id))
                tasks.update(row.copy(content = "Half-written"))
                error("Simulated crash between entity update and outbox insert")
            }
        }

        assertTrue(failure.isFailure)
        assertEquals("Survivor", store.tasks.getById(task.id)?.content)
        assertEquals(opsBefore, store.outbox.count())
    }

    @Test
    fun completeIsIdempotent() = runBlocking<Unit> {
        val store = openStore()
        val repo = repository(store)
        val task = repo.createTask("Pay rent")

        assertTrue(repo.complete(task.id))
        assertFalse(repo.complete(task.id), "Second complete must be a no-op")
        assertNotNull(repo.getTask(task.id)?.completedAt)

        val completeOps = store.outbox.forEntity("task", task.id).filter { it.opType == OperationType.TASK_COMPLETE.wire }
        assertEquals(1, completeOps.size)

        assertTrue(repo.uncomplete(task.id))
        assertFalse(repo.uncomplete(task.id))
        assertNull(repo.getTask(task.id)?.completedAt)
    }

    @Test
    fun renameRecordsOnlyRealChanges() = runBlocking<Unit> {
        val store = openStore()
        val repo = repository(store)
        val task = repo.createTask("Draft")

        assertFalse(repo.updateContent(task.id, "  Draft "))
        assertTrue(repo.updateContent(task.id, "Final"))
        assertEquals("Final", repo.getTask(task.id)?.content)
        assertEquals(
            listOf(OperationType.TASK_CREATE.wire, OperationType.TASK_UPDATE.wire),
            store.outbox.forEntity("task", task.id).map { it.opType },
        )
    }

    @Test
    fun deleteAndRestoreAreSoft() = runBlocking<Unit> {
        val store = openStore()
        val repo = repository(store)
        val task = repo.createTask("Temporary")

        assertTrue(repo.delete(task.id))
        assertNotNull(store.tasks.getById(task.id)?.deletedAt, "Row stays as a tombstone")
        assertFailsWith<ValidationException> { repo.complete(task.id) }
        assertTrue(repo.restore(task.id))
        assertNull(repo.getTask(task.id)?.deletedAt)
    }

    @Test
    fun localSeqIsContiguousUnderConcurrentWrites() = runBlocking<Unit> {
        val store = openStore()
        val repo = repository(store)
        repo.workspace()

        (1..100).map { i -> async(Dispatchers.Default) { repo.createTask("Task $i") } }.awaitAll()

        val seqs = store.outbox.all().map { it.localSeq }
        assertEquals((1L..101L).toList(), seqs, "Bootstrap op + 100 creates must have seq 1..101 without gaps")
        assertEquals(101L, store.workspaces.lastLocalSeq(repo.workspace().workspaceId))
    }

    @Test
    fun stateSurvivesReopenAndSequenceContinues() = runBlocking<Unit> {
        val first = openStore()
        val repo1 = repository(first)
        val created = repo1.createTask("Persisted")
        val workspaceId = repo1.workspace().workspaceId
        first.close()

        val second = openStore()
        val repo2 = repository(second)
        assertEquals(workspaceId, repo2.workspace().workspaceId, "Reopen must not create a new workspace")
        assertEquals("Persisted", repo2.getTask(created.id)?.content)

        repo2.createTask("After reopen")
        assertEquals((1L..3L).toList(), second.outbox.all().map { it.localSeq })
        assertEquals(1, second.projects.count(), "Still exactly one Inbox")
    }

    @Test
    fun inboxFlowEmitsNewTasksInOrder() = runBlocking<Unit> {
        val repo = repository()
        repo.observeInbox().test {
            assertEquals(emptyList(), awaitItem())
            repo.createTask("First")
            var items = awaitItem()
            while (items.size < 1) items = awaitItem()
            repo.createTask("Second")
            items = awaitItem()
            while (items.size < 2) items = awaitItem()
            assertEquals(listOf("First", "Second"), items.map { it.content })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
