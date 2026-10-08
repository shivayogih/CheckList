// Pure Kotlin/JVM module: it cannot see Android or Room, which keeps business rules portable and fast to test.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    // Only the JSR-330 annotation, so Hilt in :app can construct use cases without :domain knowing Hilt.
    api(libs.javax.inject)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
