package io.github.yozh150991.todo.core.database

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Raw Room builder for [file] without upgrade protection. Production code must use [openAppDatabase];
 * this is for tests and for [DatabaseOpener] itself.
 */
fun createAppDatabase(context: Context, file: File): AppDatabase {
    file.absoluteFile.parentFile?.mkdirs()
    return Room.databaseBuilder<AppDatabase>(context = context.applicationContext, name = file.absolutePath)
        .buildConfigured(BundledSQLiteDriver(), RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING, Dispatchers.IO)
}

/** Opens the app database in the private database directory with snapshot-protected upgrades (FIX-01). */
suspend fun openAppDatabase(
    context: Context,
    fileName: String = AppDatabase.DEFAULT_FILE_NAME,
    log: (String) -> Unit = {},
): OpenResult = withContext(Dispatchers.IO) {
    val appContext = context.applicationContext
    val file = appContext.getDatabasePath(fileName)
    file.absoluteFile.parentFile?.mkdirs()
    DatabaseOpener(
        path = file.absolutePath,
        build = { path -> createAppDatabase(appContext, File(path)) },
        log = log,
    ).open()
}
