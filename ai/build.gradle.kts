// Optional AI layer. It depends on :domain only and never on :data,
// so AI code cannot reach Room even by accident (ADR-001, ADR-008).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Unit tests resolve real item names against the bundled seed catalog. It is read as plain files,
// not through a Gradle dependency, so :ai still cannot see :data classes.
val seedDir = rootProject.file("data/src/main/assets/seed")

android {
    namespace = "com.dataloom.checklist.ai"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all { test ->
            test.systemProperty("checklist.seedDir", seedDir.absolutePath)
            test.inputs.dir(seedDir).withPathSensitivity(PathSensitivity.RELATIVE)
        }
    }
}

dependencies {
    implementation(project(":domain"))

    // Language packs (src/main/resources) are JSON.
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
