package io.github.yozh150991.todo.web

import androidx.room3.*
import io.github.yozh150991.todo.core.database.AppDatabase
import io.github.yozh150991.todo.core.database.LocalStore
import io.github.yozh150991.todo.core.database.createWebAppDatabase
import io.github.yozh150991.todo.core.domain.TaskRepository
import io.github.yozh150991.todo.core.model.IdGenerator
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/*
 * Ф0 Web spike self-test (spec §3.3, §3.3.1; ADR-004), driven by webApp/e2e (Playwright).
 *
 *   index.html?selftest=write                 → creates the workspace and data in OPFS
 *   index.html?selftest=verify&workspace=<id> → after a reload / in a new tab: data is still there
 *
 * The result is written as JSON into <pre id="selftest" data-status="pass|fail">.
 * A separate database file keeps the self-test away from the app's own todo.db.
 */

private const val SELF_TEST_DB = "selftest.db"
private val FTS_TEXTS = listOf("Купити молоко та хліб", "Zapłacić rachunek za prąd", "Buy milk on the way home")
private val FTS_QUERIES = listOf("молоко", "МОЛОКО", "хлі*", "prąd", "prad", "hom*")

private class Checks {
    val failed = mutableListOf<String>()
    val passed = mutableListOf<String>()

    fun check(name: String, ok: Boolean, details: String = "") {
        if (ok) passed += name else failed += if (details.isEmpty()) name else "$name: $details"
    }
}

internal fun startSelfTest(phase: String, expectedWorkspace: String?) {
    val output = document.getElementById("selftest") ?: return
    output.setAttribute("style", "display: block")
    output.textContent = "running $phase..."
    MainScope().launch {
        val json = try {
            runSelfTest(phase, expectedWorkspace)
        } catch (e: Throwable) {
            buildJsonObject {
                put("phase", phase)
                put("status", "fail")
                put("error", e.message ?: e.toString())
            }
        }
        output.textContent = json.toString()
        output.setAttribute("data-status", (json["status"] as? JsonPrimitive)?.content ?: "fail")
    }
}

private suspend fun AppDatabase.textQuery(sql: String): String =
    useReaderConnection { connection ->
        connection.usePrepared(sql) { statement ->
            statement.step()
            statement.getText(0)
        }
    }

private suspend fun AppDatabase.ftsCounts(): Map<String, Long> =
    useReaderConnection { connection ->
        FTS_QUERIES.associateWith { query ->
            connection.usePrepared("SELECT COUNT(*) FROM fts_probe WHERE fts_probe MATCH ?") { statement ->
                statement.bindText(1, query)
                statement.step()
                statement.getLong(0)
            }
        }
    }

private suspend fun runSelfTest(phase: String, expectedWorkspace: String?): JsonObject = buildJsonObject {
    val checks = Checks()
    val database = createWebAppDatabase(createWebDriver(), SELF_TEST_DB)
    val store = LocalStore(database)
    val repository = TaskRepository(store)

    val workspace = repository.workspace()
    put("phase", phase)
    put("workspace", workspace.workspaceId)
    put("sqlite", database.textQuery("SELECT sqlite_version()"))
    val journal = database.textQuery("PRAGMA journal_mode")
    put("journal_mode", journal)
    checks.check("journal_mode is a rollback journal", journal == "truncate" || journal == "delete", journal)

    when (phase) {
        "write" -> writePhase(store, repository, database, checks)
        "verify" -> verifyPhase(store, repository, database, checks, expectedWorkspace, workspace.workspaceId)
        else -> checks.check("known phase", false, phase)
    }

    putJsonArray("passed") { checks.passed.forEach { add(JsonPrimitive(it)) } }
    putJsonArray("failed") { checks.failed.forEach { add(JsonPrimitive(it)) } }
    put("status", if (checks.failed.isEmpty()) "pass" else "fail")
}

private suspend fun JsonObjectBuilder.writePhase(
    store: LocalStore,
    repository: TaskRepository,
    database: AppDatabase,
    checks: Checks,
) {
    checks.check("fresh database", store.tasks.count() == 0, "found ${store.tasks.count()} tasks")
    val first = repository.createTask(FTS_TEXTS[0])
    repository.createTask(FTS_TEXTS[1])
    repository.createTask(FTS_TEXTS[2])
    checks.check("complete", repository.complete(first.id))
    checks.check("complete is idempotent", !repository.complete(first.id))

    // Invariant §2.1.1 through the worker: a failing outbox insert rolls back the task and activity.
    val existingOperationId = store.outbox.all().first().operationId
    val failing = TaskRepository(store, operationIds = IdGenerator { existingOperationId })
    val tasksBefore = store.tasks.count()
    val activitiesBefore = store.activities.count()
    val rollback = runCatching { failing.createTask("Must roll back") }
    checks.check("duplicate operation id fails", rollback.isFailure)
    checks.check("task rolled back", store.tasks.count() == tasksBefore, "tasks ${store.tasks.count()} vs $tasksBefore")
    checks.check(
        "activity rolled back",
        store.activities.count() == activitiesBefore,
        "activities ${store.activities.count()} vs $activitiesBefore",
    )

    val seqs = store.outbox.all().map { it.localSeq }
    put("outbox_seq", seqs.joinToString(","))
    checks.check("outbox seq 1..5", seqs == (1L..5L).toList(), seqs.toString())
    put("tasks", store.tasks.count())

    database.useWriterConnection { transactor ->
        transactor.usePrepared(
            "CREATE VIRTUAL TABLE fts_probe USING fts5(content, tokenize = 'unicode61 remove_diacritics 2')",
        ) { it.step() }
        FTS_TEXTS.forEach { text ->
            transactor.usePrepared("INSERT INTO fts_probe(content) VALUES (?)") { statement ->
                statement.bindText(1, text)
                statement.step()
            }
        }
    }
    putFts(database, checks)
}

private suspend fun JsonObjectBuilder.verifyPhase(
    store: LocalStore,
    repository: TaskRepository,
    database: AppDatabase,
    checks: Checks,
    expectedWorkspace: String?,
    actualWorkspace: String,
) {
    checks.check("same workspace after reload", expectedWorkspace == actualWorkspace, "$expectedWorkspace vs $actualWorkspace")
    checks.check("one Inbox", store.projects.count() == 1, "${store.projects.count()} projects")
    // Verify may run more than once (reload, then a new tab); each run adds one task.
    val inboxId = repository.workspace().inboxId
    val open = store.tasks.observeOpenInProject(inboxId).first().map { it.content }
    val completed = store.tasks.observeCompletedInProject(inboxId).first().map { it.content }
    checks.check("open tasks persisted", open.containsAll(FTS_TEXTS.drop(1)), open.toString())
    checks.check("completed task persisted", completed == listOf(FTS_TEXTS[0]), completed.toString())
    val seqsBefore = store.outbox.all().map { it.localSeq }
    checks.check(
        "outbox seq contiguous and persisted",
        seqsBefore.size >= 5 && seqsBefore == (1L..seqsBefore.size.toLong()).toList(),
        seqsBefore.toString(),
    )
    repository.createTask("Після перезавантаження")
    val seqsAfter = store.outbox.all().map { it.localSeq }
    put("outbox_seq", seqsAfter.joinToString(","))
    checks.check("local_seq continues", seqsAfter.last() == seqsBefore.size + 1L, seqsAfter.toString())
    put("tasks", store.tasks.count())
    putFts(database, checks)
}

private suspend fun JsonObjectBuilder.putFts(database: AppDatabase, checks: Checks) {
    val counts = database.ftsCounts()
    putJsonObject("fts5") { counts.forEach { (query, count) -> put(query, count) } }
    counts.forEach { (query, count) -> checks.check("fts5 '$query'", count == 1L, "$count matches") }
}
