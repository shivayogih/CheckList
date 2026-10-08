// Plugins are declared here once (apply false) so every module resolves the same versions.
// AGP 9 compiles Kotlin itself ("built-in Kotlin"), so org.jetbrains.kotlin.android is not used.
// Declaring the Kotlin JVM plugin here also pins the Kotlin Gradle plugin version for the whole build.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
}
