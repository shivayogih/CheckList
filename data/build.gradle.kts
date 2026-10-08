// Data layer: Room (single source of truth), seed catalog, and the Hilt bindings of the domain repositories.
// The encrypted profile uses Tink. DataStore, import/export and PDF arrive in later phases.
import com.android.build.api.variant.HostTestBuilder
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    // Applied here (AGP would apply it too) so the Robolectric settings below see JaCoCo's task extension.
    jacoco
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

    buildTypes {
        debug {
            // Unit-test coverage (CL-175): createDebugUnitTestCoverageReport writes XML and HTML.
            enableUnitTestCoverage = true
        }
    }

    testOptions {
        // Robolectric tests read the real seed assets.
        unitTests.isIncludeAndroidResources = true
    }

    testCoverage {
        jacocoVersion = libs.versions.jacoco.get()
    }
}

// MigrationTestHelper reads the committed schemas as assets (CL-173). Added to the unit-test
// component only through the Variant API (AGP 9 no longer accepts the legacy sourceSets cast), so
// the schemas never ship in an APK.
androidComponents {
    onVariants { variant ->
        variant.hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.sources?.assets
            ?.addStaticSourceDirectory(layout.projectDirectory.dir("schemas").asFile.absolutePath)
    }
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.withType<Test>().configureEach {
    // Robolectric loads classes through its own class loader; JaCoCo must instrument those too.
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

// Exported schemas are committed: they are the baseline for migrations and MigrationTestHelper tests.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.serialization.json)
    // Profile encryption: AES-256-GCM keyset wrapped by an Android Keystore key (ADR-011).
    implementation(libs.tink.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
}
