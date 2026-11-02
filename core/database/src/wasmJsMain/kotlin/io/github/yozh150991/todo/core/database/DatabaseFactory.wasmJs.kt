package io.github.yozh150991.todo.core.database

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteDriver
import kotlinx.coroutines.Dispatchers

/**
 * Opens the workspace database in the browser (ADR-004).
 *
 * [driver] is a `WebWorkerSQLiteDriver` whose worker stores files in OPFS through the SAHPool VFS.
 * That VFS has no shared memory, so WAL is not available and a rollback journal is used.
 * Upgrades run through Room migrations without the file snapshot of [DatabaseOpener]: OPFS has no
 * VACUUM INTO target outside the pool yet (TECH_COMPATIBILITY, gate of phase Ф7).
 */
fun createWebAppDatabase(driver: SQLiteDriver, fileName: String = AppDatabase.DEFAULT_FILE_NAME): AppDatabase =
    Room.databaseBuilder<AppDatabase>(name = fileName)
        .buildConfigured(driver, RoomDatabase.JournalMode.TRUNCATE, Dispatchers.Default)
