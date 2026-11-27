import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    android {
        namespace = "io.github.yozh150991.todo.ui"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        // Compose Multiplatform resources (strings) are packaged as Android resources too.
        androidResources.enable = true
    }
    jvm()
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    compilerOptions {
        optIn.addAll("kotlin.time.ExperimentalTime", "kotlin.uuid.ExperimentalUuidApi")
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)
            api(libs.compose.material3)
            implementation(libs.compose.components.resources)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
            implementation(libs.jetbrains.navigation.compose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            // Screenshot tests of the shared UI on the desktop renderer (spec §12.1: Roborazzi).
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.ui.test)
            implementation(libs.roborazzi.compose.desktop)
            // viewModelScope needs Dispatchers.Main, which on the JVM comes from Swing.
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// Shared localization (spec §13.2): composeResources/values (English default), values-uk, values-pl.
compose.resources {
    publicResClass = false
    packageOfResClass = "io.github.yozh150991.todo.ui.resources"
}

// Roborazzi records every screenshot; the tests compare it with the committed baseline in
// ui/screenshots/ (ui/src/jvmTest/.../Baselines.kt). New screenshots go base64-encoded to
// build/reports/new-baselines/, which CI prints into the job log to be committed.
tasks.withType<Test>().configureEach {
    // Full exception messages in the CI log: the test reports are only in the artifacts.
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    systemProperty("roborazzi.test.record", "true")
    systemProperty("roborazzi.output.dir", layout.buildDirectory.dir("outputs/roborazzi").get().asFile.absolutePath)
    systemProperty("todo.baselineDir", layout.projectDirectory.dir("screenshots").asFile.absolutePath)
    systemProperty("todo.newBaselineDir", layout.buildDirectory.dir("reports/new-baselines").get().asFile.absolutePath)
    systemProperty("todo.reportDir", layout.buildDirectory.dir("reports/runtime-evidence").get().asFile.absolutePath)
    inputs.dir(layout.projectDirectory.dir("screenshots")).optional().withPropertyName("screenshotBaselines")
}
