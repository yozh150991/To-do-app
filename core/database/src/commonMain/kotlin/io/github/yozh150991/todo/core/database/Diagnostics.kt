package io.github.yozh150991.todo.core.database

import androidx.room3.Dao
import androidx.room3.RawQuery
import androidx.room3.RoomRawQuery

/**
 * Read-only SQLite facts for the "About" dialog and for device evidence (ADR-001 gate: SQLite version
 * and FTS5 on a real phone). Raw queries: PRAGMAs are not allowed in @Query, and the same statements
 * run unchanged on Android, the JVM and the browser worker.
 */
@Dao
interface DiagnosticsDao {
    @RawQuery
    suspend fun text(query: RoomRawQuery): String?

    @RawQuery
    suspend fun number(query: RoomRawQuery): Long?
}

data class DatabaseDiagnostics(
    val sqliteVersion: String,
    val fts5: Boolean,
    val journalMode: String,
    val schemaVersion: Int,
    /** `PRAGMA quick_check`: "ok" for a healthy file. */
    val quickCheck: String,
    val tasks: Int,
    val pendingOperations: Int,
)

suspend fun LocalStore.diagnostics(): DatabaseDiagnostics {
    val dao = database.diagnosticsDao()
    return DatabaseDiagnostics(
        sqliteVersion = dao.text(RoomRawQuery("SELECT sqlite_version()")).orEmpty(),
        fts5 = dao.number(RoomRawQuery("SELECT sqlite_compileoption_used('ENABLE_FTS5')")) == 1L,
        journalMode = dao.text(RoomRawQuery("PRAGMA journal_mode")).orEmpty(),
        schemaVersion = (dao.number(RoomRawQuery("PRAGMA user_version")) ?: 0L).toInt(),
        quickCheck = dao.text(RoomRawQuery("PRAGMA quick_check")).orEmpty(),
        tasks = tasks.count(),
        pendingOperations = outbox.pendingCount(),
    )
}
