import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
}

kotlin {
    android {
        namespace = "io.github.yozh150991.todo.core.database"
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
        // Room's generated `actual object` for the expect database constructor.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // nonWebMain (Android + Desktop): blocking SQLite API, bundled SQLite and the snapshot-protected
    // DatabaseOpener. androidx.sqlite exposes prepare/step as blocking there and as suspend on the Web,
    // so code that talks to SQLite directly cannot live in commonMain (ADR-004).
    applyDefaultHierarchyTemplate()

    sourceSets {
        val nonWebMain by creating {
            dependsOn(commonMain.get())
        }
        androidMain.get().dependsOn(nonWebMain)
        jvmMain.get().dependsOn(nonWebMain)

        commonMain.dependencies {
            api(projects.core.model)
            api(libs.room3.runtime)
            implementation(libs.kotlinx.coroutines.core)
        }
        nonWebMain.dependencies {
            api(libs.sqlite.bundled)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies {
    add("kspAndroid", libs.room3.compiler)
    add("kspJvm", libs.room3.compiler)
    add("kspWasmJs", libs.room3.compiler)
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

tasks.withType<Test>().configureEach {
    // Tests write SQLite runtime evidence (version, compile options, FTS5) for CI reports.
    systemProperty("todo.reportDir", layout.buildDirectory.dir("reports/runtime-evidence").get().asFile.absolutePath)
}
