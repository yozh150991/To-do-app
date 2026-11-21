package io.github.yozh150991.todo.core.domain

import io.github.yozh150991.todo.core.model.Priority
import io.github.yozh150991.todo.core.model.ProjectTaskFate
import io.github.yozh150991.todo.core.model.TaskRules
import io.github.yozh150991.todo.core.model.ValidationException
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Property-based check (spec §12.1, plan Ф1a PR 2): any sequence of task and project commands keeps
 * the tree and the outbox consistent, and a rejected command changes nothing at all.
 *
 * Each step is a pair of numbers: which command, and a seed that picks its targets among the rows
 * that exist at that moment, so every generated sequence is meaningful.
 */
class CommandSequencePropertyTest {

    @Test
    fun randomCommandSequencesKeepInvariants(): Unit = runBlocking {
        checkAll(
            60,
            Arb.list(Arb.int(0 until COMMANDS), 1..40),
            Arb.list(Arb.int(0..1_000_000), 40..40),
        ) { commands, seeds ->
            val f = RepoFixture()
            try {
                f.repo.workspace() // the Inbox exists before the first command, as in the app
                val moves = mutableListOf<TaskMove>()
                val deletions = mutableListOf<ProjectDeletion>()
                commands.forEachIndexed { step, command ->
                    val seed = seeds.getOrElse(step) { step * 7919 }
                    val before = f.snapshot()
                    val result = runCatching { f.execute(command, seed, moves, deletions) }
                    val error = result.exceptionOrNull()
                    if (error != null && error !is ValidationException) {
                        fail("step $step command $command threw $error", error)
                    }
                    if (error != null) assertEquals(before, f.snapshot(), "rejected command $command changed the database")
                    f.checkInvariants("after step $step (command $command)")
                }
            } finally {
                f.close()
            }
        }
    }

    private suspend fun RepoFixture.execute(
        command: Int,
        seed: Int,
        moves: MutableList<TaskMove>,
        deletions: MutableList<ProjectDeletion>,
    ) {
        val tasks = store.tasks.all()
        val projects = store.projects.all()
        fun <T> List<T>.pick(salt: Int = 0): T? = if (isEmpty()) null else this[(seed / (salt + 1)) % size]
        val task = tasks.pick()
        val otherTask = tasks.pick(7)
        val project = projects.pick(3)
        when (command) {
            0 -> repo.createTask("t$seed", projectId = project?.id)
            1 -> task?.let { repo.createTask("s$seed", parentId = it.id) }
            2 -> task?.let { repo.moveTask(it.id, parentId = otherTask?.id) }?.let { moves += it }
            3 -> task?.let { repo.moveTask(it.id, projectId = project?.id) }?.let { moves += it }
            4 -> task?.let { repo.delete(it.id) }
            5 -> task?.let { repo.restore(it.id) }
            6 -> task?.let { if (it.completedAt == null) repo.complete(it.id) else repo.uncomplete(it.id) }
            7 -> task?.let { repo.updateTask(it.id, TaskEdit(priority = FieldEdit.Set(Priority.entries[seed % 4]))) }
            8 -> repo.projects.create("p$seed", parentId = project?.takeIf { seed % 2 == 0 }?.id)
            9 -> project?.let { if (it.archivedAt == null) repo.projects.archive(it.id) else repo.projects.unarchive(it.id) }
            10 -> project?.let {
                repo.projects.delete(it.id, ProjectTaskFate.entries[seed % 2])?.let { deletions += it }
            }
            11 -> project?.let { repo.projects.restore(it.id) }
            12 -> moves.removeLastOrNull()?.let { repo.undoMove(it) }
            13 -> deletions.removeLastOrNull()?.let { repo.projects.undoDelete(it) }
            14 -> project?.let { repo.projects.move(it.id, projects.pick(11)?.id?.takeIf { seed % 3 != 0 }) }
            else -> error("unknown command $command")
        }
    }

    private suspend fun RepoFixture.checkInvariants(where: String) {
        val tasks = store.tasks.all().associateBy { it.id }
        val projects = store.projects.all().associateBy { it.id }
        assertEquals(1, projects.values.count { it.isInbox }, "$where: exactly one Inbox")
        val inbox = projects.values.single { it.isInbox }
        assertTrue(inbox.deletedAt == null && inbox.archivedAt == null && inbox.parentId == null, "$where: Inbox untouched")

        for (task in tasks.values) {
            val project = projects.getValue(task.projectId)
            if (task.deletedAt == null) {
                assertTrue(project.deletedAt == null, "$where: live task ${task.content} in a binned project")
            }
            task.parentId?.let { parentId ->
                val parent = tasks.getValue(parentId)
                assertEquals(parent.projectId, task.projectId, "$where: ${task.content} not in its parent's project")
                if (task.deletedAt == null) {
                    assertTrue(parent.deletedAt == null, "$where: live ${task.content} under binned ${parent.content}")
                }
            }
            var depth = 0
            var cursor = task
            while (cursor.parentId != null) {
                cursor = tasks.getValue(cursor.parentId!!)
                depth++
                assertTrue(depth <= tasks.size, "$where: cycle above ${task.content}")
            }
            if (task.deletedAt == null) assertTrue(depth <= TaskRules.MAX_DEPTH, "$where: ${task.content} at depth $depth")
        }
        for (project in projects.values) {
            var depth = 0
            var cursor = project
            while (cursor.parentId != null) {
                cursor = projects.getValue(cursor.parentId!!)
                depth++
                assertTrue(depth <= projects.size, "$where: project cycle above ${project.name}")
            }
            project.parentId?.let { parentId ->
                val parent = projects.getValue(parentId)
                if (project.deletedAt == null) assertTrue(parent.deletedAt == null, "$where: live ${project.name} under binned ${parent.name}")
                assertTrue(!parent.isInbox, "$where: ${project.name} nested under the Inbox")
            }
        }

        val ops = store.outbox.all()
        assertEquals((1L..ops.size.toLong()).toList(), ops.map { it.localSeq }, "$where: local_seq has gaps")
        val created = ops.filter { it.opType == "task.create" }.map { it.entityId }.toSet()
        assertEquals(tasks.keys, created, "$where: every task has its create operation and nothing else creates one")
    }

    private companion object {
        const val COMMANDS = 15
    }
}
