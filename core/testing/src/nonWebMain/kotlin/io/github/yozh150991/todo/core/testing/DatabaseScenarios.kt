package io.github.yozh150991.todo.core.testing

import io.github.yozh150991.todo.core.database.AppDatabase
import io.github.yozh150991.todo.core.database.DatabaseOpener
import io.github.yozh150991.todo.core.database.InsufficientStorageException
import io.github.yozh150991.todo.core.database.LocalStore
import io.github.yozh150991.todo.core.database.MigrationFailedException
import io.github.yozh150991.todo.core.database.MigrationVerifier
import io.github.yozh150991.todo.core.database.OpenResult
import io.github.yozh150991.todo.core.database.SimulatedCrashException
import io.github.yozh150991.todo.core.database.UnsupportedDowngradeException
import io.github.yozh150991.todo.core.database.UpgradeFault
import io.github.yozh150991.todo.core.database.entity.SectionEntity
import io.github.yozh150991.todo.core.database.diagnostics
import io.github.yozh150991.todo.core.database.inspect
import io.github.yozh150991.todo.core.domain.TaskRepository
import io.github.yozh150991.todo.core.model.IdGenerator
import io.github.yozh150991.todo.core.model.RandomUuidGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ф0 gate scenarios (spec §11 Ф0, §3.4 FIX-01, ADR-001), shared by JVM and Android device tests.
 * Each function gets a fresh [DatabaseTestEnv] and throws on failure.
 */
object DatabaseScenarios {

    private fun opener(
        env: DatabaseTestEnv,
        path: String,
        verifier: MigrationVerifier = MigrationVerifier.Strict,
        freeSpace: ((String) -> Long)? = null,
        fault: UpgradeFault? = null,
    ): DatabaseOpener = if (freeSpace == null) {
        DatabaseOpener(path = path, build = env.build, verifier = verifier, log = env.log, fault = fault)
    } else {
        DatabaseOpener(path = path, build = env.build, verifier = verifier, freeSpace = freeSpace, log = env.log, fault = fault)
    }

    private fun counts(path: String): Map<String, Long> = Sql.with(path) { c ->
        Sql.texts(
            c,
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
                "AND name NOT IN ('room_master_table', 'android_metadata') ORDER BY name",
        ).associateWith { Sql.long(c, "SELECT COUNT(*) FROM `$it`") }
    }

    private fun userVersion(path: String): Int = Sql.with(path) { Sql.long(it, "PRAGMA user_version").toInt() }

    // --- ADR-001 ------------------------------------------------------------------------------

    /** Bundled SQLite version, FTS5 with uk/pl/en text, and `VACUUM INTO`. */
    fun sqliteRuntimeEvidence(env: DatabaseTestEnv) {
        val path = env.path("env.db")
        val copy = env.path("vacuum-into-copy.db")
        Sql.with(path) { c ->
            val version = Sql.text(c, "SELECT sqlite_version()")
            val options = Sql.texts(c, "PRAGMA compile_options")
            val (major, minor) = version.split('.').map(String::toInt)
            val vacuumInto = major > 3 || (major == 3 && minor >= 27)

            Sql.exec(c, "CREATE VIRTUAL TABLE docs USING fts5(content, tokenize = 'unicode61 remove_diacritics 2')")
            Sql.exec(
                c,
                "INSERT INTO docs(content) VALUES ('Купити молоко та хліб'), ('Zapłacić rachunek za prąd'), " +
                    "('Buy milk on the way home')",
            )
            val checks = listOf(
                "uk exact" to "молоко", "uk case-insensitive" to "МОЛОКО", "uk prefix" to "хлі*",
                "pl exact" to "prąd", "pl without diacritics" to "prad", "pl prefix" to "rachun*",
                "en exact" to "milk", "en prefix" to "hom*",
            ).map { (name, query) ->
                Triple(name, query, Sql.long(c, "SELECT COUNT(*) FROM docs WHERE docs MATCH '$query'"))
            }
            if (vacuumInto) Sql.exec(c, "VACUUM INTO '$copy'")

            env.log("[${env.platform}] sqlite_version: $version")
            env.log("[${env.platform}] ENABLE_FTS5: ${"ENABLE_FTS5" in options}")
            env.log("[${env.platform}] VACUUM INTO: supported=$vacuumInto copyBytes=${env.fileSize(copy)}")
            checks.forEach { (name, query, n) -> env.log("[${env.platform}] fts5 $name '$query' -> $n") }

            assertTrue("ENABLE_FTS5" in options, "Bundled SQLite must have FTS5 (ADR-001)")
            assertTrue(vacuumInto, "SQLite $version has no VACUUM INTO")
            assertTrue(env.fileSize(copy) > 0, "VACUUM INTO produced no file")
            checks.forEach { (name, query, n) -> assertEquals(1L, n, "FTS5 $name '$query'") }
        }
    }

    // --- Room CRUD, outbox, restart --------------------------------------------------------------

    /** The "About" dialog's facts through Room itself (the path the app uses on every platform). */
    suspend fun diagnosticsThroughRoom(env: DatabaseTestEnv) {
        val database = env.build(env.path("diagnostics.db"))
        val store = LocalStore(database)
        TaskRepository(store).createTask("Діагностика")
        val d = store.diagnostics()
        assertTrue(d.sqliteVersion.startsWith("3."), d.sqliteVersion)
        assertTrue(d.fts5, "FTS5 must be compiled in (ADR-001)")
        assertEquals("wal", d.journalMode)
        assertEquals(AppDatabase.SCHEMA_VERSION, d.schemaVersion)
        assertEquals("ok", d.quickCheck)
        assertEquals(1, d.tasks)
        assertEquals(2, d.pendingOperations, "Inbox project.create + task.create")
        env.log("[${env.platform}] diagnostics: $d")
        database.close()
    }

    suspend fun persistsAcrossReopen(env: DatabaseTestEnv) {
        val path = env.path("todo.db")
        val first = opener(env, path).open()
        assertNull(first.upgradedFrom, "A new file needs no upgrade")
        val repo = TaskRepository(LocalStore(first.database))
        val milk = repo.createTask("Купити молоко")
        repo.createTask("Zapłacić rachunek")
        repo.complete(milk.id)
        val workspaceId = repo.workspace().workspaceId
        first.database.close()

        val second = opener(env, path).open()
        val store = LocalStore(second.database)
        val reopened = TaskRepository(store)
        assertEquals(workspaceId, reopened.workspace().workspaceId)
        assertNotNull(reopened.getTask(milk.id)?.completedAt)
        assertEquals(2, store.tasks.count())
        assertEquals(1, store.projects.count(), "Exactly one Inbox after reopen")
        reopened.createTask("Після перезапуску")
        assertEquals((1L..5L).toList(), store.outbox.all().map { it.localSeq })
        env.log("[${env.platform}] persist+reopen: ok, outbox seq 1..5")
        second.database.close()
    }

    suspend fun outboxFailureRollsBack(env: DatabaseTestEnv) {
        val result = opener(env, env.path("todo.db")).open()
        val store = LocalStore(result.database)
        val repo = TaskRepository(store, operationIds = IdGenerator { "same-operation-id" })
        repo.workspace()
        val failure = runCatching { repo.createTask("Must roll back") }
        assertTrue(failure.isFailure)
        assertEquals(0, store.tasks.count())
        assertEquals(0, store.activities.count())
        assertEquals(1, store.outbox.count())
        env.log("[${env.platform}] entity+outbox rollback: ok")
        result.database.close()
    }

    suspend fun concurrentWritesKeepSequence(env: DatabaseTestEnv, writes: Int) {
        val result = opener(env, env.path("todo.db")).open()
        val store = LocalStore(result.database)
        val repo = TaskRepository(store, ids = RandomUuidGenerator)
        repo.workspace()
        coroutineScope {
            (1..writes).map { i -> async(Dispatchers.Default) { repo.createTask("Task $i") } }.awaitAll()
        }
        assertEquals((1L..(writes + 1L)).toList(), store.outbox.all().map { it.localSeq })
        env.log("[${env.platform}] $writes concurrent writes: local_seq contiguous")
        result.database.close()
    }

    // --- FIX-01: snapshot-protected upgrade --------------------------------------------------------

    suspend fun migrationFromV1PreservesData(env: DatabaseTestEnv) {
        val path = env.path("todo.db")
        V1Database.create(path)
        val before = counts(path)
        assertEquals(V1Database.expectedCounts, before)

        val result = opener(env, path).open()
        assertEquals(1, result.upgradedFrom)
        val snapshot = assertNotNull(result.snapshotPath)
        assertTrue(env.fileSize(snapshot) > 0, "Snapshot file must exist")
        assertEquals(before, DatabaseOpener.readManifestTableCounts(snapshot))
        assertEquals(1, userVersion(snapshot), "Snapshot keeps the old schema version")

        val after = result.database.inspect()
        assertEquals(AppDatabase.SCHEMA_VERSION, after.userVersion)
        assertEquals("ok", after.integrity)
        before.forEach { (table, n) -> assertEquals(n, after.tableCounts[table], "rows in $table") }
        assertEquals(0L, after.tableCounts["section"])

        val store = LocalStore(result.database)
        val t3 = assertNotNull(store.tasks.getById("t3"))
        assertEquals("2026-11-02", t3.dueDate)
        assertEquals("Europe/Warsaw", t3.dueTz)
        assertEquals("t3", store.tasks.getById("t4")?.parentId)

        // New in v2: section_id is a real foreign key.
        val orphan = runCatching { store.tasks.update(t3.copy(sectionId = "missing-section")) }
        assertTrue(orphan.isFailure, "section_id must reference an existing section after v2")
        store.sections.insert(
            SectionEntity("s1", "work", "Звіти", "", "a0", false, null, 1000, 1000, null),
        )
        store.tasks.update(t3.copy(sectionId = "s1"))
        assertEquals("s1", store.tasks.getById("t3")?.sectionId)

        // The outbox counter continues after the migrated sequence.
        val repo = TaskRepository(store)
        repo.createTask("Після міграції")
        assertEquals(V1Database.SEEDED_LOCAL_SEQ + 1, store.outbox.all().maxOf { it.localSeq })
        env.log("[${env.platform}] v1->v2 migration: counts preserved $before, snapshot verified")
        result.database.close()
    }

    suspend fun walFramesAreCapturedBySnapshot(env: DatabaseTestEnv) {
        val path = env.path("todo.db")
        V1Database.create(path, journalWal = true)
        // Simulate a crash with committed-but-uncheckpointed WAL frames: a writer stays open.
        val writer = Sql.open(path)
        try {
            Sql.texts(writer, "PRAGMA wal_autocheckpoint = 0")
            Sql.exec(
                writer,
                "INSERT INTO task (id, project_id, child_order, content, description, priority, recur_from_completion, " +
                    "is_completable, created_at, updated_at) VALUES ('wal-1', 'inbox', 'a5', 'Only in WAL', '', 4, 0, 1, 1, 1)",
            )
            val walBytes = env.fileSize("$path-wal")
            env.log("[${env.platform}] WAL bytes before upgrade: $walBytes")
            assertTrue(walBytes > 0, "The test needs frames in the WAL file")

            val result = opener(env, path).open()
            val snapshot = assertNotNull(result.snapshotPath)
            assertEquals(6L, DatabaseOpener.readManifestTableCounts(snapshot)["task"], "Snapshot must include WAL rows")
            assertNotNull(LocalStore(result.database).tasks.getById("wal-1"), "Migrated DB must include WAL rows")
            result.database.close()
        } finally {
            writer.close()
        }
        env.log("[${env.platform}] WAL frames captured by VACUUM INTO snapshot: ok")
    }

    suspend fun failedVerificationRestoresSnapshot(env: DatabaseTestEnv) {
        val path = env.path("todo.db")
        V1Database.create(path)
        val before = counts(path)
        val failure = assertFailsWith<MigrationFailedException> {
            opener(env, path, verifier = MigrationVerifier { _, _, _ -> error("semantic check rejected") }).open()
        }
        assertEquals(1, failure.fromVersion)
        assertEquals(1, userVersion(path), "File must be back at v1")
        assertEquals(before, counts(path), "Data must equal the pre-upgrade state")
        assertEquals(0L, env.fileSize("$path.upgrade-in-progress.json"), "Marker removed after rollback")

        val retry = opener(env, path).open()
        assertEquals(1, retry.upgradedFrom, "A later start upgrades normally")
        retry.database.close()
        env.log("[${env.platform}] failed verification -> restored v1, retry ok")
    }

    suspend fun interruptedUpgradeIsRecovered(env: DatabaseTestEnv) {
        for (fault in UpgradeFault.entries) {
            val path = env.path("crash-${fault.name.lowercase()}.db")
            V1Database.create(path)
            val before = counts(path)
            assertFailsWith<SimulatedCrashException> { opener(env, path, fault = fault).open() }

            val result: OpenResult = opener(env, path).open()
            val expectRecovery = fault != UpgradeFault.AFTER_SNAPSHOT
            assertEquals(expectRecovery, result.recoveredInterruptedUpgrade, "recovery flag after $fault")
            assertEquals(1, result.upgradedFrom, "after $fault the upgrade reruns from v1")
            val after = result.database.inspect()
            before.forEach { (table, n) -> assertEquals(n, after.tableCounts[table], "rows in $table after $fault") }
            result.database.close()
            env.log("[${env.platform}] crash $fault -> recovered=${result.recoveredInterruptedUpgrade}, data intact")
        }
    }

    suspend fun insufficientSpaceLeavesDatabaseUntouched(env: DatabaseTestEnv) {
        val path = env.path("todo.db")
        V1Database.create(path)
        val before = counts(path)
        assertFailsWith<InsufficientStorageException> {
            opener(env, path, freeSpace = { 1024L }).open()
        }
        assertEquals(1, userVersion(path))
        assertEquals(before, counts(path))
        assertEquals(0L, env.fileSize("$path.pre-v1-to-v${AppDatabase.SCHEMA_VERSION}.snapshot"), "No snapshot written")
        env.log("[${env.platform}] disk full -> upgrade refused, v1 untouched")
    }

    suspend fun downgradeIsRefused(env: DatabaseTestEnv) {
        val path = env.path("todo.db")
        opener(env, path).open().database.close()
        Sql.with(path) { Sql.exec(it, "PRAGMA user_version = 99") }
        val failure = assertFailsWith<UnsupportedDowngradeException> { opener(env, path).open() }
        assertEquals(99, failure.onDisk)
        assertEquals(99, userVersion(path), "File untouched")
        env.log("[${env.platform}] newer schema on disk -> refused without changes")
    }

    suspend fun leftoverTemporaryFilesAreRemoved(env: DatabaseTestEnv) {
        val path = env.path("todo.db")
        val leftover = "$path.pre-v1-to-v2.snapshot.tmp"
        env.writeText(leftover, "partial snapshot from a crashed run")
        opener(env, path).open().database.close()
        assertEquals(0L, env.fileSize(leftover))
        assertFalse(env.fileSize(path) == 0L)
    }
}
