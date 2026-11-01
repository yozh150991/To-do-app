package io.github.yozh150991.todo.core.testing

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import io.github.yozh150991.todo.core.database.AppDatabase

/** Platform hooks a scenario needs; every scenario gets a fresh, empty [dir]. */
class DatabaseTestEnv(
    val dir: String,
    val platform: String,
    /** Raw Room builder for an absolute path (no upgrade protection). */
    val build: (String) -> AppDatabase,
    val log: (String) -> Unit,
    /** File size in bytes, 0 when missing. */
    val fileSize: (String) -> Long,
    val writeText: (String, String) -> Unit,
) {
    fun path(name: String): String = "$dir/$name"
}

/** Low-level SQL helpers on a plain driver connection (outside Room). */
object Sql {
    fun open(path: String): SQLiteConnection = BundledSQLiteDriver().open(path)

    inline fun <T> with(path: String, block: (SQLiteConnection) -> T): T {
        val connection = open(path)
        try {
            return block(connection)
        } finally {
            connection.close()
        }
    }

    fun text(connection: SQLiteConnection, sql: String): String {
        val stmt = connection.prepare(sql)
        try {
            check(stmt.step()) { "No rows for: $sql" }
            return stmt.getText(0)
        } finally {
            stmt.close()
        }
    }

    fun texts(connection: SQLiteConnection, sql: String): List<String> {
        val stmt = connection.prepare(sql)
        try {
            val out = mutableListOf<String>()
            while (stmt.step()) out += stmt.getText(0)
            return out
        } finally {
            stmt.close()
        }
    }

    fun long(connection: SQLiteConnection, sql: String): Long = text(connection, sql).toLong()

    fun exec(connection: SQLiteConnection, sql: String) = connection.execSQL(sql)
}

/** Builds a genuine schema v1 database file (as shipped before the v2 upgrade) and seeds it. */
object V1Database {
    const val WORKSPACE_ID = "ws-1"
    const val INBOX_ID = "inbox"
    const val SEEDED_LOCAL_SEQ = 6L

    val expectedCounts = mapOf(
        "local_workspace" to 1L,
        "project" to 3L,
        "task" to 5L,
        "task_activity" to 2L,
        "outbox_operation" to 6L,
        "external_identity" to 1L,
    )

    fun create(path: String, journalWal: Boolean = false) {
        Sql.with(path) { connection ->
            if (journalWal) Sql.texts(connection, "PRAGMA journal_mode = WAL")
            SchemaV1Fixture.statements.forEach { Sql.exec(connection, it) }
            Sql.exec(connection, "PRAGMA user_version = ${SchemaV1Fixture.VERSION}")
            seed(connection)
        }
    }

    fun seed(connection: SQLiteConnection) {
        val statements = buildList {
            add(
                "INSERT INTO local_workspace (workspace_id, account_scope, inbox_id, device_id, claim_status, " +
                    "bound_user_id, claim_operation_id, backup_checkpoint, last_local_seq, created_at, singleton) " +
                    "VALUES ('$WORKSPACE_ID', 'local:$WORKSPACE_ID', '$INBOX_ID', 'device-1', 'local', NULL, NULL, NULL, " +
                    "$SEEDED_LOCAL_SEQ, 1000, 1)",
            )
            listOf(Triple(INBOX_ID, "Inbox", 1), Triple("work", "Робота", 0), Triple("home", "Dom i ogród", 0))
                .forEachIndexed { i, (id, name, inbox) ->
                    add(
                        "INSERT INTO project (id, name, description, color, parent_id, child_order, is_inbox, inbox_marker, " +
                            "view_mode, is_favorite, archived_at, revision, created_at, updated_at, deleted_at) VALUES " +
                            "('$id', '$name', '', NULL, NULL, 'a$i', $inbox, ${if (inbox == 1) "1" else "NULL"}, 'list', 0, " +
                            "NULL, NULL, 1000, 1000, NULL)",
                    )
                }
            // id, project, parent, order, content, due date, due time, zone, completed_at, deleted_at
            val tasks = listOf(
                listOf("t1", INBOX_ID, null, "a0", "Купити молоко", null, null, null, null, null),
                listOf("t2", INBOX_ID, null, "a1", "Zapłacić rachunek", null, null, null, "2000", null),
                listOf("t3", "work", null, "a0", "Prepare report", "2026-11-02", "09:30", "Europe/Warsaw", null, null),
                listOf("t4", "work", "t3", "a0", "Collect numbers", null, null, null, null, null),
                listOf("t5", "home", null, "a0", "Deleted task", null, null, null, null, "3000"),
            )
            tasks.forEach { row ->
                val text = row.take(8).joinToString(", ") { v -> if (v == null) "NULL" else "'$v'" }
                val instants = row.drop(8).joinToString(", ") { v -> v ?: "NULL" }
                add(
                    "INSERT INTO task (id, project_id, parent_id, child_order, content, due_date, due_time, due_tz, " +
                        "completed_at, deleted_at, description, priority, recur_from_completion, is_completable, " +
                        "created_at, updated_at) VALUES ($text, $instants, '', 4, 0, 1, 1000, 1000)",
                )
            }
            (1..SEEDED_LOCAL_SEQ.toInt()).forEach { seq ->
                add(
                    "INSERT INTO outbox_operation (operation_id, local_seq, workspace_id, entity, entity_id, op_type, " +
                        "field_patch, base_revision, device_id, created_at, status, attempts, last_error) VALUES " +
                        "('op-$seq', $seq, '$WORKSPACE_ID', 'task', 't1', 'task.update', '{}', NULL, 'device-1', 1000, " +
                        "'pending', 0, NULL)",
                )
            }
            add(
                "INSERT INTO task_activity (id, task_id, event_type, field_changes, created_at, device_id, operation_id) " +
                    "VALUES ('a1', 't1', 'created', '{}', 1000, 'device-1', 'op-1'), " +
                    "('a2', 't2', 'completed', '{}', 2000, 'device-1', 'op-2')",
            )
            add(
                "INSERT INTO external_identity (source, entity_type, source_id, local_id, source_hash, import_version, " +
                    "last_seen_at) VALUES ('todoist', 'task', '6Xf2', 't3', 'hash', 1, 1000)",
            )
        }
        statements.forEach { Sql.exec(connection, it) }
    }
}
