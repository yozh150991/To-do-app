// Shared database scenarios. The same suspend functions run on the JVM (jvmTest here) and on
// Android emulators (androidApp/src/androidTest), so both platforms prove the same Ф0 gates.
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

    compilerOptions {
        optIn.addAll("kotlin.time.ExperimentalTime", "kotlin.uuid.ExperimentalUuidApi")
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            api(projects.core.database)
            api(kotlin("test"))
            implementation(libs.sqlite.bundled)
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

tasks.withType<Test>().configureEach {
    systemProperty("todo.reportDir", layout.buildDirectory.dir("reports/runtime-evidence").get().asFile.absolutePath)
}
