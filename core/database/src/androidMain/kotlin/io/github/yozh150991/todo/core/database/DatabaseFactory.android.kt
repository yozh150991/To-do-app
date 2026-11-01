package io.github.yozh150991.todo.core.database

import android.content.Context
import androidx.room3.Room
import kotlinx.coroutines.Dispatchers

/** Opens (or creates) the workspace database in the app's private database directory. */
fun createAppDatabase(context: Context, fileName: String = AppDatabase.DEFAULT_FILE_NAME): AppDatabase {
    val appContext = context.applicationContext
    val path = appContext.getDatabasePath(fileName).absolutePath
    return Room.databaseBuilder<AppDatabase>(context = appContext, name = path)
        .buildConfigured(Dispatchers.IO)
}
