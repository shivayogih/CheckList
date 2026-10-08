// Optional AI layer. It depends on :domain only and never on :data,
// so AI code cannot reach Room even by accident (ADR-001, ADR-008).
plugins {
    alias(libs.plugins.android.library)
}

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
}

dependencies {
    implementation(project(":domain"))
}
