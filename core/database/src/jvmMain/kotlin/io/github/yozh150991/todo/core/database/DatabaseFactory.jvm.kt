package io.github.yozh150991.todo.core.database

import androidx.room3.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Raw Room builder for [file] without upgrade protection. Production code must use [openAppDatabase];
 * this is for tests and for [DatabaseOpener] itself.
 */
fun createAppDatabase(file: File): AppDatabase {
    file.absoluteFile.parentFile?.mkdirs()
    return Room.databaseBuilder<AppDatabase>(name = file.absolutePath)
        .buildConfigured(Dispatchers.IO)
}

/** Opens [file] with snapshot-protected upgrades (FIX-01). */
suspend fun openAppDatabase(file: File, log: (String) -> Unit = {}): OpenResult = withContext(Dispatchers.IO) {
    file.absoluteFile.parentFile?.mkdirs()
    DatabaseOpener(
        path = file.absolutePath,
        build = { path -> createAppDatabase(File(path)) },
        log = log,
    ).open()
}

/** Default desktop location: `~/.todo-app/todo.db` (overridable with -Dtodo.dataDir). */
fun defaultDesktopDatabaseFile(): File {
    val base = System.getProperty("todo.dataDir")?.let(::File)
        ?: File(System.getProperty("user.home"), ".todo-app")
    return File(base, AppDatabase.DEFAULT_FILE_NAME)
}
