import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "io.github.yozh150991.todo.core.model"
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

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.serialization.json)
        }
        wasmJsMain.dependencies {
            // IANA time zone rules for kotlinx-datetime on the Web (TimeZoneRules.wasmJs.kt).
            // 2.23.0 is the newest release whose peer range still accepts @js-joda/core 3.2.0,
            // the version kotlinx-datetime 0.8.0 depends on (2.24+ require core >= 5.7).
            implementation(npm("@js-joda/timezone", "2.23.0"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
