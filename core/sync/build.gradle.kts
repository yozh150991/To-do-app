import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// Future home of the sync engine (Ф5a). In Ф0 it holds only the supabase-kt module probe
// (spec §3.3.1 "Supabase client module matrix"): Auth, PostgREST/RPC and Realtime on every target.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "io.github.yozh150991.todo.core.sync"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    jvm()
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    compilerOptions {
        optIn.addAll("kotlin.time.ExperimentalTime", "kotlin.uuid.ExperimentalUuidApi")
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        // Ktor engine: OkHttp on Android and the JVM (HTTP + WebSockets for Realtime), fetch on the Web.
        val nonWebMain by creating {
            dependsOn(commonMain.get())
        }
        androidMain.get().dependsOn(nonWebMain)
        jvmMain.get().dependsOn(nonWebMain)

        commonMain.dependencies {
            implementation(libs.supabase.auth)
            implementation(libs.supabase.postgrest)
            implementation(libs.supabase.realtime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        nonWebMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

tasks.withType<Test>().configureEach {
    // The probe test runs only where a Supabase stack is up (CI job "Supabase modules");
    // SUPABASE_URL / SUPABASE_ANON_KEY come from the environment, otherwise it is skipped.
    systemProperty("todo.reportDir", layout.buildDirectory.dir("reports/runtime-evidence").get().asFile.absolutePath)
}
