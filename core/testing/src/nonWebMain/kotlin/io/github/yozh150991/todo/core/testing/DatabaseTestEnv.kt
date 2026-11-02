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
fun V1Database.create(path: String, journalWal: Boolean = false) {
    Sql.with(path) { connection ->
        if (journalWal) Sql.texts(connection, "PRAGMA journal_mode = WAL")
        SchemaV1Fixture.statements.forEach { Sql.exec(connection, it) }
        Sql.exec(connection, "PRAGMA user_version = ${SchemaV1Fixture.VERSION}")
        seedStatements.forEach { Sql.exec(connection, it) }
    }
}
