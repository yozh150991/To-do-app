package io.github.yozh150991.todo.web

import androidx.room3.*
import androidx.sqlite.execSQL
import io.github.yozh150991.todo.core.database.AppDatabase
import io.github.yozh150991.todo.core.database.LocalStore
import io.github.yozh150991.todo.core.database.createWebAppDatabase
import io.github.yozh150991.todo.core.domain.TaskRepository
import io.github.yozh150991.todo.core.model.IdGenerator
import io.github.yozh150991.todo.core.testing.SchemaV1Fixture
import io.github.yozh150991.todo.core.testing.V1Database
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.js.Promise

/*
 * Ф0 Web spike self-test (spec §3.3, §3.3.1; ADR-004), driven by webApp/e2e (Playwright).
 *
 *   index.html?selftest=write                 → creates the workspace and data in OPFS
 *   index.html?selftest=verify&workspace=<id> → after a reload / in a new tab: data is still there
 *   index.html?selftest=inspect               → the Inbox of the app's own todo.db,
 *                                               used after the e2e test typed a task into the real UI
 *   index.html?selftest=clear                 → deletes the database pool from OPFS, as clearing
 *                                               site data does; the next start must be clean
 *   index.html?selftest=migrate               → a genuine schema v1 file, opened by the v2 AppDatabase
 *
 * The result is written as JSON into <pre id="selftest" data-status="pass|fail">.
 * write/verify use a separate database file, away from the app's own todo.db.
 */

private const val SELF_TEST_DB = "selftest.db"
private const val SNAPSHOT_FILE = "/selftest-snapshot.db"
private const val MIGRATE_DB = "migrate.db"
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

/** The worker's SAHPool directory (webApp/worker/worker.js); NotFoundError means already empty. */
private fun removeDatabasePool(): Promise<JsAny?> =
    js("""navigator.storage.getDirectory()
        .then(function (root) { return root.removeEntry('.todo-db', { recursive: true }); })
        .catch(function (e) { if (e.name !== 'NotFoundError') throw e; })""")

private suspend fun runSelfTest(phase: String, expectedWorkspace: String?): JsonObject = buildJsonObject {
    val checks = Checks()
    if (phase == "migrate") {
        put("phase", phase)
        migratePhase(checks)
        putJsonArray("passed") { checks.passed.forEach { add(JsonPrimitive(it)) } }
        putJsonArray("failed") { checks.failed.forEach { add(JsonPrimitive(it)) } }
        put("status", if (checks.failed.isEmpty()) "pass" else "fail")
        return@buildJsonObject
    }
    if (phase == "clear") {
        // Before any worker exists: nothing holds the OPFS access handles.
        removeDatabasePool().await<JsAny?>()
        put("phase", phase)
        put("status", "pass")
        return@buildJsonObject
    }
    val fileName = if (phase == "inspect") AppDatabase.DEFAULT_FILE_NAME else SELF_TEST_DB
    val database = createWebAppDatabase(createWebDriver(), fileName)
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
        "inspect" -> inspectPhase(store, repository)
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
    putSnapshot(database, store, checks)
}

/**
 * FIX-01 on the Web (ADR-004): VACUUM INTO a second file of the same OPFS pool, then check the copy
 * through ATTACH: integrity_check and the same number of tasks.
 */
private suspend fun JsonObjectBuilder.putSnapshot(database: AppDatabase, store: LocalStore, checks: Checks) {
    val tasks = store.tasks.count().toLong()
    val (integrity, copied) = database.useWriterConnection { transactor ->
        transactor.usePrepared("VACUUM INTO '$SNAPSHOT_FILE'") { it.step() }
        transactor.usePrepared("ATTACH DATABASE '$SNAPSHOT_FILE' AS snapshot") { it.step() }
        try {
            val integrity = transactor.usePrepared("PRAGMA snapshot.integrity_check") { it.step(); it.getText(0) }
            val copied = transactor.usePrepared("SELECT COUNT(*) FROM snapshot.task") { it.step(); it.getLong(0) }
            integrity to copied
        } finally {
            transactor.usePrepared("DETACH DATABASE snapshot") { it.step() }
        }
    }
    putJsonObject("vacuum_into") {
        put("integrity_check", integrity)
        put("tasks", copied)
    }
    checks.check("VACUUM INTO snapshot passes integrity_check", integrity == "ok", integrity)
    checks.check("snapshot has every task", copied == tasks, "$copied vs $tasks")
}

/**
 * Room AutoMigration 1 → 2 through the Web worker, mirroring DatabaseScenarios.migrationFromV1PreservesData
 * on Android/JVM: the same v1 DDL and seed (core/testing) go straight through the driver, then the v2
 * AppDatabase opens the file. The FIX-01 snapshot around it on the Web is a Ф7 item (ADR-004 п. 7).
 */
private suspend fun JsonObjectBuilder.migratePhase(checks: Checks) {
    // One driver = one worker = one owner of the OPFS pool, for the v1 setup and for Room.
    val driver = createWebDriver()
    val connection = driver.open(MIGRATE_DB)
    try {
        val fresh = connection.prepare("PRAGMA user_version").use { it.step(); it.getLong(0) } == 0L
        checks.check("fresh v1 file", fresh)
        if (!fresh) return
        SchemaV1Fixture.statements.forEach { connection.execSQL(it) }
        connection.execSQL("PRAGMA user_version = ${SchemaV1Fixture.VERSION}")
        V1Database.seedStatements.forEach { connection.execSQL(it) }
    } finally {
        connection.close()
    }

    val database = createWebAppDatabase(driver, MIGRATE_DB)
    val version = database.textQuery("PRAGMA user_version")
    put("user_version", version)
    checks.check("schema migrated to v${AppDatabase.SCHEMA_VERSION}", version == "${AppDatabase.SCHEMA_VERSION}", version)
    val counts = V1Database.expectedCounts.mapValues { (table, _) -> database.textQuery("SELECT COUNT(*) FROM $table").toLong() }
    putJsonObject("counts") { counts.forEach { (table, n) -> put(table, n) } }
    checks.check("rows preserved", counts == V1Database.expectedCounts, counts.toString())
    checks.check("integrity_check", database.textQuery("PRAGMA integrity_check") == "ok")
    checks.check("foreign_key_check", database.textQuery("SELECT COUNT(*) FROM pragma_foreign_key_check()") == "0")
    val newTables = database.textQuery(
        "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name IN ('section', 'label', 'task_label')",
    )
    checks.check("v2 tables created", newTables == "3", newTables)
    val sectionFk = database.textQuery(
        "SELECT COUNT(*) FROM pragma_foreign_key_list('task') WHERE \"table\" = 'section' AND \"from\" = 'section_id'",
    )
    checks.check("task.section_id references section", sectionFk == "1", sectionFk)

    val store = LocalStore(database)
    val t3 = store.tasks.getById("t3")
    checks.check(
        "v1 task fields kept",
        t3 != null && t3.dueDate == "2026-11-02" && t3.dueTz == "Europe/Warsaw",
        t3.toString(),
    )
    if (t3 != null) {
        val orphan = runCatching { store.tasks.update(t3.copy(sectionId = "missing-section")) }
        checks.check("section_id is enforced", orphan.isFailure)
    }
    val repository = TaskRepository(store)
    checks.check("same workspace", repository.workspace().workspaceId == V1Database.WORKSPACE_ID)
    repository.createTask("Після міграції")
    val lastSeq = store.outbox.all().maxOf { it.localSeq }
    checks.check("local_seq continues", lastSeq == V1Database.SEEDED_LOCAL_SEQ + 1, "$lastSeq")
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

private suspend fun JsonObjectBuilder.inspectPhase(store: LocalStore, repository: TaskRepository) {
    val inboxId = repository.workspace().inboxId
    putJsonArray("open") { store.tasks.observeOpenInProject(inboxId).first().forEach { add(JsonPrimitive(it.content)) } }
    putJsonArray("completed") {
        store.tasks.observeCompletedInProject(inboxId).first().forEach { add(JsonPrimitive(it.content)) }
    }
    put("outbox", store.outbox.all().size)
}

private suspend fun JsonObjectBuilder.putFts(database: AppDatabase, checks: Checks) {
    val counts = database.ftsCounts()
    putJsonObject("fts5") { counts.forEach { (query, count) -> put(query, count) } }
    counts.forEach { (query, count) -> checks.check("fts5 '$query'", count == 1L, "$count matches") }
}
