import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// Web proof of concept for phase Ф0 (spec §3.3, §3.3.1; ADR-004). The product web app is phase Ф7.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("webApp")
        browser {
            commonWebpackConfig {
                outputFileName = "webApp.js"
            }
        }
        binaries.executable()
    }

    compilerOptions {
        optIn.addAll(
            "kotlin.time.ExperimentalTime",
            "kotlin.uuid.ExperimentalUuidApi",
            "kotlin.js.ExperimentalWasmJsInterop",
        )
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(projects.ui)
            implementation(projects.core.domain)
            implementation(projects.core.database)
            // Plain-SQL fixtures (schema v1 + seed) for the migration self-test; Ф0 spike only.
            implementation(projects.core.testing)
            // supabase-kt module probe (?selftest=supabase); Ф0 spike only.
            implementation(projects.core.sync)
            implementation(libs.sqlite.web)
            implementation(libs.kotlinx.browser)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            // The SQLite WASM worker (OPFS SAHPool VFS) as a local npm package, bundled by webpack.
            implementation(npm("todo-sqlite-worker", layout.projectDirectory.dir("worker").asFile))
        }
    }
}
