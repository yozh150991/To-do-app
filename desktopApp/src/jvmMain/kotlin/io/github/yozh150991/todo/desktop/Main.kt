package io.github.yozh150991.todo.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.yozh150991.todo.core.database.LocalStore
import io.github.yozh150991.todo.core.database.LocalStoreProvider
import io.github.yozh150991.todo.core.database.defaultDesktopDatabaseFile
import io.github.yozh150991.todo.core.database.openAppDatabase
import io.github.yozh150991.todo.core.domain.TaskRepository
import io.github.yozh150991.todo.ui.App
import io.github.yozh150991.todo.ui.uiModule
import org.koin.core.context.startKoin
import org.koin.dsl.module

fun main() {
    val stores = LocalStoreProvider {
        LocalStore(openAppDatabase(defaultDesktopDatabaseFile(), log = { println(it) }).database)
    }
    startKoin {
        modules(
            module {
                single { stores }
                single { TaskRepository(get<LocalStoreProvider>()) }
            },
            uiModule,
        )
    }
    application {
        Window(
            onCloseRequest = {
                stores.closeIfOpen()
                exitApplication()
            },
            title = "To-do",
        ) {
            App()
        }
    }
}
