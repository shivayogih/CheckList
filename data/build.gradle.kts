// Data layer: Room, DataStore, seed catalog, import/export and PDF arrive here from Phase 2.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.dataloom.checklist.data"
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
