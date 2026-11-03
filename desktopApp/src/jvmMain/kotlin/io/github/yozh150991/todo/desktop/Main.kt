package io.github.yozh150991.todo.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.yozh150991.todo.core.database.LocalStore
import io.github.yozh150991.todo.core.database.LocalStoreProvider
import io.github.yozh150991.todo.core.database.defaultDesktopDatabaseFile
import io.github.yozh150991.todo.core.database.openAppDatabase
import io.github.yozh150991.todo.core.domain.TaskRepository
import io.github.yozh150991.todo.ui.App
import io.github.yozh150991.todo.ui.FeatureFlags
import io.github.yozh150991.todo.ui.about.AppInfo
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
                single { desktopAppInfo() }
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
            App(FeatureFlags.of(System.getProperty("todo.features") == "all"))
        }
    }
}

/** Packaged builds get the version from jpackage; `./gradlew :desktopApp:run` shows "dev". */
private fun desktopAppInfo() = AppInfo(
    version = System.getProperty("jpackage.app-version") ?: "dev",
    platform = "${System.getProperty("os.name")} ${System.getProperty("os.version")} " +
        "(${System.getProperty("os.arch")}), JVM ${System.getProperty("java.version")}",
)
