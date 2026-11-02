package io.github.yozh150991.todo.core.database

import androidx.room3.*
import io.github.yozh150991.todo.core.database.entity.ExternalIdentityEntity
import io.github.yozh150991.todo.core.database.entity.LocalWorkspaceEntity
import io.github.yozh150991.todo.core.database.entity.ProjectEntity
import io.github.yozh150991.todo.core.database.entity.TaskEntity
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull

/** Schema v1 invariants enforced by SQLite itself, on a real file database with the bundled driver. */
class SchemaConstraintsTest {

    private val dir = newTempDir("schema")
    private val file = File(dir, "todo.db")
    private var db = createAppDatabase(file)

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private fun project(id: String, inbox: Boolean = false) = ProjectEntity(
        id = id, name = if (inbox) "Inbox" else id, description = "", color = null, parentId = null,
        childOrder = "a0", isInbox = inbox, inboxMarker = if (inbox) 1 else null, viewMode = "list",
        isFavorite = false, archivedAt = null, revision = null, createdAt = 1, updatedAt = 1, deletedAt = null,
    )

    private fun task(id: String, projectId: String, parentId: String? = null) = TaskEntity(
        id = id, projectId = projectId, sectionId = null, parentId = parentId, childOrder = "a0", dayOrder = null,
        content = "Задача $id", description = "", sourceUrl = null, priority = 4, dueDate = "2026-11-02",
        dueTime = null, dueTz = null, deadline = null, rrule = null, recurFromCompletion = false,
        recurrenceEndReason = null, recurrenceDtstartDate = null, recurrenceDtstartTime = null,
        recurrenceZoneMode = null, recurrenceZoneId = null, recurrenceIntervalUnit = null,
        recurrenceIntervalN = null, recurrencePolicyVersion = null, durationMin = null, completedAt = null,
        isCompletable = true, revision = null, createdAt = 1, updatedAt = 1, deletedAt = null,
    )

    private fun workspace(id: String) = LocalWorkspaceEntity(
        workspaceId = id, accountScope = "local:$id", inboxId = "inbox", deviceId = "device",
        claimStatus = "local", boundUserId = null, claimOperationId = null, backupCheckpoint = null,
        lastLocalSeq = 0, createdAt = 1,
    )

    @Test
    fun dataSurvivesCloseAndReopen() = runBlocking<Unit> {
        db.projectDao().insert(project("inbox", inbox = true))
        db.taskDao().insert(task("t1", "inbox"))
        db.close()

        db = createAppDatabase(file)
        val reopened = assertNotNull(db.taskDao().getById("t1"))
        assertEquals("Задача t1", reopened.content)
        assertEquals("2026-11-02", reopened.dueDate)
    }

    @Test
    fun journalModeIsWal() = runBlocking<Unit> {
        db.projectDao().count() // forces the database to open
        val mode = db.useReaderConnection { connection ->
            connection.usePrepared("PRAGMA journal_mode") { stmt ->
                stmt.step()
                stmt.getText(0)
            }
        }
        writeEvidence("room-jvm-journal.txt", listOf("room journal_mode: $mode"))
        assertEquals("wal", mode.lowercase())
    }

    @Test
    fun foreignKeysAreEnforced() = runBlocking<Unit> {
        db.projectDao().insert(project("inbox", inbox = true))
        assertFails("Task with an unknown project must be rejected") {
            db.taskDao().insert(task("orphan", projectId = "missing"))
        }
        assertFails("Subtask with an unknown parent must be rejected") {
            db.taskDao().insert(task("child", projectId = "inbox", parentId = "missing-parent"))
        }
        assertEquals(0, db.taskDao().count())
    }

    @Test
    fun onlyOneInboxAndOneWorkspace() = runBlocking<Unit> {
        db.projectDao().insert(project("inbox", inbox = true))
        assertFails { db.projectDao().insert(project("inbox-2", inbox = true)) }
        db.projectDao().insert(project("work"))
        db.projectDao().insert(project("home"))
        assertEquals(3, db.projectDao().count())

        db.workspaceDao().insert(workspace("w1"))
        assertFails { db.workspaceDao().insert(workspace("w2")) }
    }

    @Test
    fun externalIdentityIsUniquePerSourceId() = runBlocking<Unit> {
        val identity = ExternalIdentityEntity(
            source = "todoist", entityType = "task", sourceId = "6Xf2", localId = "local-1",
            sourceHash = "h1", importVersion = 1, lastSeenAt = 1,
        )
        db.externalIdentityDao().insert(identity)
        assertFails { db.externalIdentityDao().insert(identity.copy(localId = "local-2")) }
        assertEquals("local-1", db.externalIdentityDao().find("todoist", "task", "6Xf2")?.localId)
    }
}
