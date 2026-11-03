package io.github.yozh150991.todo.web

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeViewport
import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import io.github.yozh150991.todo.core.database.LocalStore
import io.github.yozh150991.todo.core.database.LocalStoreProvider
import io.github.yozh150991.todo.core.database.createWebAppDatabase
import io.github.yozh150991.todo.core.domain.TaskRepository
import io.github.yozh150991.todo.ui.App
import io.github.yozh150991.todo.ui.FeatureFlags
import io.github.yozh150991.todo.ui.about.AppInfo
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

/** Compose for Web draws through WebGL 2 (Skiko); without it the page would stay blank. */
internal fun webGl2Available(): Boolean = js("!!document.createElement('canvas').getContext('webgl2')")

internal fun browserLanguage(): String = js("(navigator.language || 'en').slice(0, 2).toLowerCase()")

/**
 * Plain HTML, shown when Compose cannot start, so it cannot use the Compose resources of the UI;
 * the same three languages (uk/pl, English otherwise).
 */
private fun webGlUnavailableMessage(): String = when (browserLanguage()) {
    "uk" -> "Застосунку потрібен WebGL 2, а цей браузер його зараз не дає. Увімкніть апаратне " +
        "прискорення (або WebGL) у налаштуваннях браузера й перезавантажте сторінку."
    "pl" -> "Aplikacja potrzebuje WebGL 2, a ta przeglądarka teraz go nie udostępnia. Włącz " +
        "akcelerację sprzętową (lub WebGL) w ustawieniach przeglądarki i odśwież stronę."
    else -> "This app needs WebGL 2, which this browser does not provide right now. Turn on hardware " +
        "acceleration (or WebGL) in the browser settings and reload the page."
}

private fun showPlainMessage(text: String) {
    val message = document.createElement("p")
    message.setAttribute("id", "startup-error")
    message.setAttribute("style", "margin: 24px; font: 16px/1.5 system-ui, sans-serif; max-width: 40em")
    message.textContent = text
    document.body?.appendChild(message)
}

/** Query string as a map; values are not URL-decoded (only simple ASCII values are used). */
internal fun queryParameters(): Map<String, String> =
    window.location.search.removePrefix("?")
        .split("&")
        .filter { it.isNotEmpty() }
        .associate { part -> part.substringBefore("=") to part.substringAfter("=", "") }

/**
 * Compose for Web learns the viewport size in a LaunchedEffect, so a frame can be measured before
 * that with unbounded constraints (Firefox does it). Scaffold and TopAppBar reject those
 * ("Size(2147483647 x 64) is out of range"), so the app is composed once the size is known.
 */
@Composable
private fun SizedViewport(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (constraints.hasBoundedWidth && constraints.hasBoundedHeight) content()
    }
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val parameters = queryParameters()
    val selfTestPhase = parameters["selftest"]
    if (selfTestPhase != null) {
        startSelfTest(selfTestPhase, parameters)
        return
    }

    if (!webGl2Available()) {
        showPlainMessage(webGlUnavailableMessage())
        return
    }

    val stores = LocalStoreProvider { LocalStore(createWebAppDatabase(createWebDriver())) }
    startKoin {
        modules(
            module {
                single { stores }
                single { TaskRepository(get<LocalStoreProvider>()) }
                // The Web build is a Ф0 spike without a release version yet (Web release: Ф7).
                single { AppInfo(version = "dev (web)", platform = window.navigator.userAgent) }
            },
            uiModule,
        )
    }
    ComposeViewport(document.body!!) {
        // `?features=all` shows unfinished features (FeatureFlags); the e2e tests run without it.
        SizedViewport { App(FeatureFlags.of(parameters["features"] == "all")) }
    }
}
