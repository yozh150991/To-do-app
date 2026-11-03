plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvm()

    compilerOptions {
        optIn.addAll("kotlin.time.ExperimentalTime", "kotlin.uuid.ExperimentalUuidApi")
    }

    sourceSets {
        jvmMain.dependencies {
            implementation(projects.ui)
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.koin.core)
        }
    }
}

// Desktop is a Ф0 proof of concept only; the product (tray, hotkey, installers) is phase Ф8.
compose.desktop {
    application {
        mainClass = "io.github.yozh150991.todo.desktop.MainKt"
    }
}

// `./gradlew :desktopApp:run` is a development run: show unfinished features (FeatureFlags).
// Packaged builds (jpackage) start without the property.
tasks.withType<JavaExec>().configureEach {
    systemProperty("todo.features", "all")
}
