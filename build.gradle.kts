// Plugins are declared once here (apply false) so every module shares one classpath.
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room3) apply false
}

// One JVM bytecode level for every Kotlin/JVM and Kotlin/Android compilation
// (matches Java compileOptions in :androidApp).
subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
