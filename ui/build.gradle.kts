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
        }
    }
}

// Shared localization (spec §13.2): composeResources/values (English default), values-uk, values-pl.
compose.resources {
    publicResClass = false
    packageOfResClass = "io.github.yozh150991.todo.ui.resources"
}

// Roborazzi writes screenshots only in record mode; CI keeps them as evidence (no baseline yet).
tasks.withType<Test>().configureEach {
    systemProperty("roborazzi.test.record", "true")
    systemProperty("roborazzi.output.dir", layout.buildDirectory.dir("outputs/roborazzi").get().asFile.absolutePath)
}
