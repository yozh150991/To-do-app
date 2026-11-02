package io.github.yozh150991.todo.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.yozh150991.todo.core.database.LocalStore
import io.github.yozh150991.todo.core.database.createAppDatabase
import io.github.yozh150991.todo.core.database.defaultDesktopDatabaseFile
import io.github.yozh150991.todo.core.domain.TaskRepository
import io.github.yozh150991.todo.ui.App
import io.github.yozh150991.todo.ui.uiModule
import org.koin.core.context.startKoin
import org.koin.dsl.module

fun main() {
    val store = LocalStore(createAppDatabase(defaultDesktopDatabaseFile()))
    startKoin {
        modules(
            module {
                single { store }
                single { TaskRepository(get()) }
            },
            uiModule,
        )
    }
    application {
        Window(
            onCloseRequest = {
                store.close()
                exitApplication()
            },
            title = "To-do",
        ) {
            App()
        }
    }
}
