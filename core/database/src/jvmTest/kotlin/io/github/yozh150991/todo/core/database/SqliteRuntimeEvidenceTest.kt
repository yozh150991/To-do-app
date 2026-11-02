package io.github.yozh150991.todo.core.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ADR-001 gate on JVM/Desktop: bundled SQLite version, compile options, FTS5 with Ukrainian,
 * Polish and English text, and `VACUUM INTO` availability (SQLite >= 3.27).
 * The same checks run on Android emulator API 26 in a later Ф0 iteration.
 */
class SqliteRuntimeEvidenceTest {

    private fun SQLiteConnection.texts(sql: String): List<String> {
        val stmt = prepare(sql)
        try {
            val out = mutableListOf<String>()
            while (stmt.step()) out += stmt.getText(0)
            return out
        } finally {
            stmt.close()
        }
    }

    private fun SQLiteConnection.matchCount(query: String): Int {
        val stmt = prepare("SELECT COUNT(*) FROM docs WHERE docs MATCH ?")
        try {
            stmt.bindText(1, query)
            stmt.step()
            return stmt.getLong(0).toInt()
        } finally {
            stmt.close()
        }
    }

    @Test
    fun bundledSqliteHasFts5AndVacuumInto() {
        val file = File(newTempDir("sqlite-env"), "env.db")
        val connection = BundledSQLiteDriver().open(file.absolutePath)
        try {
            val version = connection.texts("SELECT sqlite_version()").single()
            val options = connection.texts("PRAGMA compile_options")
            val (major, minor) = version.split('.').map(String::toInt)
            val vacuumIntoSupported = major > 3 || (major == 3 && minor >= 27)

            connection.execSQL(
                "CREATE VIRTUAL TABLE docs USING fts5(content, tokenize = 'unicode61 remove_diacritics 2')",
            )
            listOf(
                "Купити молоко та хліб",
                "Zapłacić rachunek za prąd",
                "Buy milk on the way home",
            ).forEach { text ->
                val insert = connection.prepare("INSERT INTO docs(content) VALUES (?)")
                try {
                    insert.bindText(1, text)
                    insert.step()
                } finally {
                    insert.close()
                }
            }

            val checks = linkedMapOf(
                "uk exact 'молоко'" to connection.matchCount("молоко"),
                "uk case-insensitive 'МОЛОКО'" to connection.matchCount("МОЛОКО"),
                "uk prefix 'хлі*'" to connection.matchCount("хлі*"),
                "pl exact 'prąd'" to connection.matchCount("prąd"),
                "pl without diacritics 'prad'" to connection.matchCount("prad"),
                "pl prefix 'rachun*'" to connection.matchCount("rachun*"),
                "en exact 'milk'" to connection.matchCount("milk"),
                "en prefix 'hom*'" to connection.matchCount("hom*"),
            )

            val vacuumTarget = File(file.parentFile, "vacuum-into-copy.db")
            if (vacuumIntoSupported) {
                connection.execSQL("VACUUM INTO '${vacuumTarget.absolutePath}'")
            }

            writeEvidence(
                "sqlite-jvm.txt",
                buildList {
                    add("platform: JVM ${System.getProperty("java.version")} ${System.getProperty("os.name")} ${System.getProperty("os.arch")}")
                    add("sqlite_version: $version")
                    add("ENABLE_FTS5: ${options.any { it == "ENABLE_FTS5" }}")
                    add("VACUUM INTO supported (>= 3.27): $vacuumIntoSupported; copy created: ${vacuumTarget.exists()}")
                    add("compile_options: ${options.joinToString(", ")}")
                    checks.forEach { (name, count) -> add("fts5 $name -> $count") }
                },
            )

            assertTrue("ENABLE_FTS5" in options, "Bundled SQLite must be compiled with FTS5 (ADR-001)")
            assertTrue(vacuumIntoSupported, "Bundled SQLite $version is older than 3.27; VACUUM INTO unavailable")
            assertTrue(vacuumTarget.length() > 0, "VACUUM INTO produced no file")
            checks.forEach { (name, count) -> assertEquals(1, count, "FTS5 check failed: $name") }
        } finally {
            connection.close()
        }
    }
}
