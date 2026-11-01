// AGP 9: built-in Kotlin compiles this module; KMP code lives in library modules.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// Release signing material comes only from the environment (CI secrets or a local shell);
// the keystore and its passwords are never in git (spec §3.4, §13.2, docs/RELEASE.md).
val releaseKeystorePath: String? = providers.environmentVariable("TODO_RELEASE_KEYSTORE").orNull
fun requiredEnv(name: String): String =
    providers.environmentVariable(name).orNull
        ?: error("$name must be set when TODO_RELEASE_KEYSTORE is set (see docs/RELEASE.md)")

// ADR-001: release APKs carry only the ABIs we install on; arm64-v8a covers current phones.
val releaseAbis: List<String> =
    ((findProperty("releaseAbis") as String?) ?: "arm64-v8a").split(',').map(String::trim).filter(String::isNotEmpty)

android {
    namespace = "io.github.yozh150991.todo.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.yozh150991.todo"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // CI passes -PversionCode/-PversionName from the build number / tag (spec §3.4).
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("versionName") as String?) ?: "0.0.1-dev"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = requiredEnv("TODO_RELEASE_KEYSTORE_PASSWORD")
                keyAlias = requiredEnv("TODO_RELEASE_KEY_ALIAS")
                keyPassword = requiredEnv("TODO_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Separate applicationId so a debug build never replaces the daily-use install (spec §3.4).
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            // Without the environment variables the release APK stays unsigned and cannot be installed.
            if (releaseKeystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            ndk {
                abiFilters += releaseAbis
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*.md")
        }
    }
}

kotlin {
    compilerOptions {
        optIn.addAll("kotlin.time.ExperimentalTime", "kotlin.uuid.ExperimentalUuidApi")
    }
}

dependencies {
    implementation(projects.ui)
    implementation(projects.core.domain)
    implementation(projects.core.database)
    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)
    implementation(libs.kotlinx.coroutines.android)

    androidTestImplementation(projects.core.testing)
    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
