package io.github.yozh150991.todo.core.database

import androidx.room3.Room
import kotlinx.coroutines.Dispatchers
import java.io.File

/** Opens (or creates) a workspace database file; parent directories are created if missing. */
fun createAppDatabase(file: File): AppDatabase {
    file.absoluteFile.parentFile?.mkdirs()
    return Room.databaseBuilder<AppDatabase>(name = file.absolutePath)
        .buildConfigured(Dispatchers.IO)
}

/** Default desktop location: `~/.todo-app/todo.db` (overridable with -Dtodo.dataDir). */
fun defaultDesktopDatabaseFile(): File {
    val base = System.getProperty("todo.dataDir")?.let(::File)
        ?: File(System.getProperty("user.home"), ".todo-app")
    return File(base, AppDatabase.DEFAULT_FILE_NAME)
}
