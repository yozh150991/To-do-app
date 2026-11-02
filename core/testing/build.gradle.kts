import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// Shared database scenarios. The same suspend functions run on the JVM (jvmTest here) and on
// Android emulators (androidApp/src/androidTest), so both platforms prove the same Ф0 gates.
// commonMain holds plain-SQL fixtures (schema v1 and its seed) that the Web self-test also uses;
// scenarios that drive SQLite through the blocking API live in nonWebMain (ADR-004).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    android {
        namespace = "io.github.yozh150991.todo.core.testing"
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
        val nonWebMain by creating {
            dependsOn(commonMain.get())
        }
        androidMain.get().dependsOn(nonWebMain)
        jvmMain.get().dependsOn(nonWebMain)

        commonMain.dependencies {
            api(projects.core.domain)
            api(projects.core.database)
            implementation(libs.kotlinx.coroutines.core)
        }
        nonWebMain.dependencies {
            api(kotlin("test"))
            implementation(libs.sqlite.bundled)
        }
        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

tasks.withType<Test>().configureEach {
    systemProperty("todo.reportDir", layout.buildDirectory.dir("reports/runtime-evidence").get().asFile.absolutePath)
}
