package io.github.yozh150991.todo.core.database

import androidx.room3.*
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Clock

/** State of a database file as seen by SQLite itself. */
data class DatabaseInspection(
    val userVersion: Int,
    val integrity: String,
    val foreignKeyViolations: Int,
    /** Row count per user table (Room/SQLite bookkeeping tables excluded). */
    val tableCounts: Map<String, Long>,
)

/** Decides whether a migrated database is semantically the same data as before the upgrade. */
fun interface MigrationVerifier {
    /** Throws to reject the migration; the opener then restores the snapshot. */
    fun verify(before: DatabaseInspection, after: DatabaseInspection, targetVersion: Int)

    companion object {
        /**
         * Default rule: integrity "ok", no FK violations, the target schema version, and every table
         * that existed before keeps exactly its row count. A future migration that intentionally
         * drops or splits a table must ship its own verifier rule together with that migration.
         */
        val Strict = MigrationVerifier { before, after, targetVersion ->
            check(after.userVersion == targetVersion) { "Schema version ${after.userVersion}, expected $targetVersion" }
            check(after.integrity == "ok") { "integrity_check after migration: ${after.integrity}" }
            check(after.foreignKeyViolations == 0) { "${after.foreignKeyViolations} foreign key violations after migration" }
            before.tableCounts.forEach { (table, count) ->
                val migrated = after.tableCounts[table]
                check(migrated == count) { "Table $table had $count rows before migration, $migrated after" }
            }
        }
    }
}

/** Points where tests can simulate a process crash during an upgrade (nothing is cleaned up). */
enum class UpgradeFault { AFTER_SNAPSHOT, AFTER_MARKER, AFTER_MIGRATION }

class SimulatedCrashException(val fault: UpgradeFault) : RuntimeException("Simulated crash at $fault")

class UnsupportedDowngradeException(val onDisk: Int, val supported: Int) :
    IllegalStateException("Database schema v$onDisk is newer than this app (v$supported); refusing to open it")

class InsufficientStorageException(val requiredBytes: Long, val availableBytes: Long) :
    IllegalStateException("Not enough free space for a safe upgrade: need $requiredBytes bytes, have $availableBytes")

class SnapshotVerificationException(message: String) : IllegalStateException(message)

class MigrationFailedException(val fromVersion: Int, val toVersion: Int, val snapshotPath: String, cause: Throwable) :
    IllegalStateException("Upgrade v$fromVersion → v$toVersion failed; database restored from snapshot", cause)

data class OpenResult(
    val database: AppDatabase,
    /** Schema version found on disk when an upgrade ran, null otherwise. */
    val upgradedFrom: Int?,
    val snapshotPath: String?,
    /** True when an interrupted earlier upgrade was rolled back from its snapshot first. */
    val recoveredInterruptedUpgrade: Boolean,
)

/**
 * Opens the workspace database with the FIX-01 / ADR-001 upgrade protocol:
 *
 * 1. If an earlier upgrade left its marker, restore that upgrade's snapshot first.
 * 2. Refuse to open a schema newer than this app (no destructive downgrade).
 * 3. Before any schema upgrade: check free space, `VACUUM INTO` a temporary copy (consistent even with
 *    un-checkpointed WAL frames), verify the copy (`integrity_check`, row counts, user_version), hash it and
 *    publish it atomically. A copy that fails verification aborts the upgrade before Room touches the file.
 * 4. Write an "upgrade in progress" marker, let Room migrate, then verify the result.
 * 5. On any failure restore the snapshot; on success remove the marker and keep only the newest snapshot.
 *
 * Exactly one upgrade may run at a time; callers serialise access (see `LocalStoreProvider`), and no
 * other component may open the file until [open] returns.
 */
class DatabaseOpener(
    private val path: String,
    private val build: (String) -> AppDatabase,
    private val targetVersion: Int = AppDatabase.SCHEMA_VERSION,
    private val driver: SQLiteDriver = BundledSQLiteDriver(),
    private val freeSpace: (String) -> Long = PlatformFiles::usableBytes,
    private val verifier: MigrationVerifier = MigrationVerifier.Strict,
    private val log: (String) -> Unit = {},
    /** Tests only: simulate a crash at the given step. */
    private val fault: UpgradeFault? = null,
) {
    private val markerPath = "$path.upgrade-in-progress.json"

    suspend fun open(): OpenResult {
        cleanupTemporaryFiles()
        val recovered = recoverInterruptedUpgrade()

        if (!PlatformFiles.exists(path)) {
            return OpenResult(openRoom(), upgradedFrom = null, snapshotPath = null, recoveredInterruptedUpgrade = recovered)
        }
        val onDisk = withConnection(path) { it.userVersion() }
        if (onDisk > targetVersion) throw UnsupportedDowngradeException(onDisk, targetVersion)
        if (onDisk == 0 || onDisk == targetVersion) {
            return OpenResult(openRoom(), upgradedFrom = null, snapshotPath = null, recoveredInterruptedUpgrade = recovered)
        }

        log("upgrade v$onDisk -> v$targetVersion: creating snapshot")
        val snapshot = createSnapshot(onDisk)
        if (fault == UpgradeFault.AFTER_SNAPSHOT) throw SimulatedCrashException(UpgradeFault.AFTER_SNAPSHOT)
        writeMarker(snapshot)
        if (fault == UpgradeFault.AFTER_MARKER) throw SimulatedCrashException(UpgradeFault.AFTER_MARKER)

        val database = build(path)
        try {
            database.workspaceDao().get() // first access runs Room's migrations
            if (fault == UpgradeFault.AFTER_MIGRATION) {
                database.close()
                throw SimulatedCrashException(UpgradeFault.AFTER_MIGRATION)
            }
            val after = database.inspect()
            verifier.verify(snapshot.inspection, after, targetVersion)
        } catch (crash: SimulatedCrashException) {
            throw crash
        } catch (failure: Throwable) {
            runCatching { database.close() }
            restore(snapshot.path, snapshot.sha256, onDisk)
            PlatformFiles.delete(markerPath)
            log("upgrade v$onDisk -> v$targetVersion failed and was rolled back: ${failure.message}")
            if (failure is CancellationException) throw failure
            throw MigrationFailedException(onDisk, targetVersion, snapshot.path, failure)
        }
        PlatformFiles.delete(markerPath)
        pruneSnapshots(keep = snapshot.path)
        log("upgrade v$onDisk -> v$targetVersion verified; snapshot kept at ${snapshot.path}")
        return OpenResult(database, upgradedFrom = onDisk, snapshotPath = snapshot.path, recoveredInterruptedUpgrade = recovered)
    }

    // --- steps -------------------------------------------------------------------------------

    private suspend fun openRoom(): AppDatabase {
        val database = build(path)
        try {
            database.workspaceDao().get()
        } catch (e: Throwable) {
            runCatching { database.close() }
            throw e
        }
        return database
    }

    private class Snapshot(val path: String, val sha256: String, val inspection: DatabaseInspection)

    private fun createSnapshot(fromVersion: Int): Snapshot {
        val snapshotPath = "$path.pre-v$fromVersion-to-v$targetVersion.snapshot"
        val tmp = "$snapshotPath.tmp"
        val dataBytes = PlatformFiles.size(path) + PlatformFiles.size("$path-wal")
        // Room of the snapshot itself plus room for SQLite to rebuild tables during the migration.
        val required = dataBytes * 2 + SAFETY_MARGIN_BYTES
        val available = freeSpace(path)
        if (available < required) throw InsufficientStorageException(required, available)

        PlatformFiles.delete(tmp)
        val source = withConnection(path) { connection ->
            val inspection = connection.inspect()
            if (inspection.integrity != "ok") {
                throw SnapshotVerificationException("Source database failed integrity_check: ${inspection.integrity}")
            }
            connection.execSQL("VACUUM INTO '${tmp.replace("'", "''")}'")
            inspection
        }
        val copy = withConnection(tmp) { it.inspect() }
        if (copy.integrity != "ok" || copy.tableCounts != source.tableCounts || copy.userVersion != source.userVersion) {
            PlatformFiles.delete(tmp)
            throw SnapshotVerificationException("Snapshot does not match the source: source=$source copy=$copy")
        }
        val sha256 = PlatformFiles.sha256(tmp)
        PlatformFiles.moveAtomic(tmp, snapshotPath)
        PlatformFiles.writeText(
            "$snapshotPath.json",
            buildJsonObject {
                put("from_version", fromVersion)
                put("to_version", targetVersion)
                put("created_at", Clock.System.now().toString())
                put("sha256", sha256)
                put("size_bytes", PlatformFiles.size(snapshotPath))
                putJsonObject("table_counts") { source.tableCounts.forEach { (t, c) -> put(t, c) } }
            }.toString(),
        )
        return Snapshot(snapshotPath, sha256, source)
    }

    private fun writeMarker(snapshot: Snapshot) {
        val tmp = "$markerPath.tmp"
        PlatformFiles.writeText(
            tmp,
            buildJsonObject {
                put("snapshot_path", snapshot.path)
                put("sha256", snapshot.sha256)
                put("from_version", snapshot.inspection.userVersion)
                put("to_version", targetVersion)
            }.toString(),
        )
        PlatformFiles.moveAtomic(tmp, markerPath)
    }

    /** A marker means the previous run died between publishing the snapshot and verifying the upgrade. */
    private fun recoverInterruptedUpgrade(): Boolean {
        if (!PlatformFiles.exists(markerPath)) return false
        val marker = Json.parseToJsonElement(PlatformFiles.readText(markerPath)).jsonObject
        val snapshotPath = marker.getValue("snapshot_path").jsonPrimitive.content
        val sha256 = marker.getValue("sha256").jsonPrimitive.content
        val fromVersion = marker.getValue("from_version").jsonPrimitive.int
        log("found interrupted upgrade marker; restoring $snapshotPath")
        restore(snapshotPath, sha256, fromVersion)
        PlatformFiles.delete(markerPath)
        return true
    }

    private fun restore(snapshotPath: String, expectedSha256: String, expectedVersion: Int) {
        check(PlatformFiles.exists(snapshotPath)) { "Snapshot $snapshotPath is missing; database left untouched" }
        val actualSha = PlatformFiles.sha256(snapshotPath)
        check(actualSha == expectedSha256) { "Snapshot $snapshotPath checksum mismatch; database left untouched" }
        val tmp = "$path.restore.tmp"
        PlatformFiles.copy(snapshotPath, tmp)
        // Stale WAL/SHM of the migrated file would be replayed onto the restored file; remove them first.
        // A crash from here on keeps the marker, so the next start repeats this restore.
        PlatformFiles.delete("$path-wal")
        PlatformFiles.delete("$path-shm")
        PlatformFiles.moveAtomic(tmp, path)
        val restored = withConnection(path) { it.inspect() }
        check(restored.integrity == "ok" && restored.userVersion == expectedVersion) {
            "Restored database is not valid: $restored"
        }
    }

    private fun cleanupTemporaryFiles() {
        val prefix = "$path."
        PlatformFiles.siblings(path)
            .filter { it.startsWith(prefix) && it.endsWith(".tmp") }
            .forEach(PlatformFiles::delete)
    }

    private fun pruneSnapshots(keep: String) {
        val prefix = "$path.pre-v"
        PlatformFiles.siblings(path)
            .filter { it.startsWith(prefix) && !it.startsWith(keep) }
            .forEach(PlatformFiles::delete)
    }

    private inline fun <T> withConnection(file: String, block: (SQLiteConnection) -> T): T {
        val connection = driver.open(file)
        try {
            return block(connection)
        } finally {
            connection.close()
        }
    }

    companion object {
        const val SAFETY_MARGIN_BYTES = 8L * 1024 * 1024

        /** Reads the snapshot manifest written next to a snapshot (for diagnostics and tests). */
        fun readManifestTableCounts(snapshotPath: String): Map<String, Long> =
            Json.parseToJsonElement(PlatformFiles.readText("$snapshotPath.json")).jsonObject
                .getValue("table_counts").jsonObject
                .mapValues { it.value.jsonPrimitive.long }
    }
}

// --- inspection helpers (driver connection and Room connection) ----------------------------------

private const val USER_TABLES_SQL =
    "SELECT name FROM sqlite_master WHERE type = 'table' " +
        "AND name NOT LIKE 'sqlite_%' AND name NOT IN ('room_master_table', 'android_metadata') ORDER BY name"

private fun SQLiteConnection.single(sql: String): String {
    val stmt = prepare(sql)
    try {
        stmt.step()
        return stmt.getText(0)
    } finally {
        stmt.close()
    }
}

private fun SQLiteConnection.column(sql: String): List<String> {
    val stmt = prepare(sql)
    try {
        val out = mutableListOf<String>()
        while (stmt.step()) out += stmt.getText(0)
        return out
    } finally {
        stmt.close()
    }
}

private fun SQLiteConnection.rowCount(sql: String): Int {
    val stmt = prepare(sql)
    try {
        var n = 0
        while (stmt.step()) n++
        return n
    } finally {
        stmt.close()
    }
}

internal fun SQLiteConnection.userVersion(): Int = single("PRAGMA user_version").toInt()

internal fun SQLiteConnection.inspect(): DatabaseInspection {
    val tables = column(USER_TABLES_SQL)
    return DatabaseInspection(
        userVersion = userVersion(),
        integrity = column("PRAGMA integrity_check").joinToString("; "),
        foreignKeyViolations = rowCount("PRAGMA foreign_key_check"),
        tableCounts = tables.associateWith { table -> single("SELECT COUNT(*) FROM `$table`").toLong() },
    )
}

/** The same inspection through Room's own connection pool (used after Room migrated the file). */
suspend fun AppDatabase.inspect(): DatabaseInspection = useReaderConnection { connection ->
    val tables = connection.usePrepared(USER_TABLES_SQL) { stmt ->
        buildList { while (stmt.step()) add(stmt.getText(0)) }
    }
    DatabaseInspection(
        userVersion = connection.usePrepared("PRAGMA user_version") { stmt -> stmt.step(); stmt.getLong(0).toInt() },
        integrity = connection.usePrepared("PRAGMA integrity_check") { stmt ->
            buildList { while (stmt.step()) add(stmt.getText(0)) }.joinToString("; ")
        },
        foreignKeyViolations = connection.usePrepared("PRAGMA foreign_key_check") { stmt ->
            var n = 0
            while (stmt.step()) n++
            n
        },
        tableCounts = tables.associateWith { table ->
            connection.usePrepared("SELECT COUNT(*) FROM `$table`") { stmt -> stmt.step(); stmt.getLong(0) }
        },
    )
}
