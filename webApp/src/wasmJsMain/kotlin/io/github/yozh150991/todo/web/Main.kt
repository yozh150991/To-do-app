package io.github.yozh150991.todo.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import io.github.yozh150991.todo.core.database.LocalStore
import io.github.yozh150991.todo.core.database.LocalStoreProvider
import io.github.yozh150991.todo.core.database.createWebAppDatabase
import io.github.yozh150991.todo.core.domain.TaskRepository
import io.github.yozh150991.todo.ui.App
import io.github.yozh150991.todo.ui.uiModule
import kotlinx.browser.document
import kotlinx.browser.window
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.w3c.dom.Worker

/** The SQLite WASM worker from webApp/worker, bundled by webpack as a separate chunk. */
internal fun createSqliteWorker(): Worker =
    js("""new Worker(new URL("todo-sqlite-worker/worker.js", import.meta.url))""")

internal fun createWebDriver(): WebWorkerSQLiteDriver = WebWorkerSQLiteDriver(createSqliteWorker())

/** Query string as a map; values are not URL-decoded (only simple ASCII values are used). */
internal fun queryParameters(): Map<String, String> =
    window.location.search.removePrefix("?")
        .split("&")
        .filter { it.isNotEmpty() }
        .associate { part -> part.substringBefore("=") to part.substringAfter("=", "") }

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val parameters = queryParameters()
    val selfTestPhase = parameters["selftest"]
    if (selfTestPhase != null) {
        startSelfTest(selfTestPhase, parameters["workspace"])
        return
    }

    val stores = LocalStoreProvider { LocalStore(createWebAppDatabase(createWebDriver())) }
    startKoin {
        modules(
            module {
                single { stores }
                single { TaskRepository(get<LocalStoreProvider>()) }
            },
            uiModule,
        )
    }
    ComposeViewport(document.body!!) {
        App()
    }
}
