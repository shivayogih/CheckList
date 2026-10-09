import java.util.Properties
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    // Annotation processing runs on KSP: kapt is not supported with AGP 9 built-in Kotlin.
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    // Applied here (AGP would apply it too) so the Robolectric settings below see JaCoCo's task extension.
    jacoco
}

// versionCode = MAJOR*10000 + MINOR*1000 + PATCH*100 + BUILD (ADR-016).
// 1.0.0 uses 10000-10099, 1.0.1 starts at 10100, 1.1.0 at 11000.
val versionProperties = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}

fun versionPart(name: String, range: IntRange): Int {
    val value = versionProperties.getProperty(name)?.toIntOrNull()
        ?: error("version.properties: $name is missing or not a number")
    require(value in range) { "version.properties: $name=$value must be in $range" }
    return value
}

val versionMajor = versionPart("VERSION_MAJOR", 1..9999)
val versionMinor = versionPart("VERSION_MINOR", 0..9)
val versionPatch = versionPart("VERSION_PATCH", 0..9)
val versionBuild = versionPart("VERSION_BUILD", 0..99)

// The one place to set the Google Play link printed on exported PDFs (with a QR code) and sent with
// shared files. Leave it empty until the listing is live: with no link, nothing points to the store.
// Use the production listing in every flavor, for example
// "https://play.google.com/store/apps/details?id=com.dataloom.checklist".
val playStoreUrl = ""

android {
    namespace = "com.dataloom.checklist"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.dataloom.checklist"
        minSdk = 26
        targetSdk = 36
        versionCode = versionMajor * 10000 + versionMinor * 1000 + versionPatch * 100 + versionBuild
        versionName = "$versionMajor.$versionMinor.$versionPatch"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "PLAY_STORE_URL", "\"$playStoreUrl\"")
    }

    // One flavor dimension: which environment the build talks to and how it is identified.
    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("String", "ENVIRONMENT", "\"dev\"")
        }
        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            buildConfigField("String", "ENVIRONMENT", "\"staging\"")
        }
        create("production") {
            dimension = "environment"
            buildConfigField("String", "ENVIRONMENT", "\"production\"")
        }
    }

    buildTypes {
        debug {
            // Unit-test coverage (CL-175): createDevDebugUnitTestCoverageReport writes XML and HTML.
            // Instrumentation applies to unit tests only, never to an APK.
            enableUnitTestCoverage = true
        }
        release {
            // Signing is never configured in Git: CI signs release bundles with secrets (section 19.4).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // Generates the locale config from res/values-* so Android 13+ lists our languages in system settings.
        generateLocaleConfig = true
    }

    lint {
        abortOnError = true
        checkDependencies = true
        error += listOf("MissingTranslation", "ExtraTranslation", "HardcodedText")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        // Compose UI tests run on Robolectric (CL-171) and need the merged resources and assets.
        unitTests.isIncludeAndroidResources = true
    }

    testCoverage {
        jacocoVersion = libs.versions.jacoco.get()
    }
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.withType<Test>().configureEach {
    // Robolectric loads app classes through its own class loader; JaCoCo must instrument those too.
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
    // Print each failure's full message in the CI log: UI and accessibility assertions list every
    // problem in the message, which the one-line default hides.
    testLogging {
        events(org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED)
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Compose compiler: domain models are immutable but live in a module without the Compose compiler, so
// they are declared stable here (app/compose-stability.conf). Stability and skippability reports are
// written only on request, because they slow the build: ./gradlew :app:assembleDevRelease -PcomposeReports
composeCompiler {
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose-stability.conf"))
    if (providers.gradleProperty("composeReports").isPresent) {
        reportsDestination.set(layout.buildDirectory.dir("compose_compiler"))
        metricsDestination.set(layout.buildDirectory.dir("compose_compiler"))
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":ai"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    // QR code encoder for the PDF export; pure Java, drawn as vector rects.
    implementation(libs.zxing.core)
    // Settings toggles for the AI assistant (CL-240).
    implementation(libs.androidx.datastore.preferences)
    // Compiles app/src/main/baseline-prof.txt into the install so the startup path is pre-compiled (CL-294).
    implementation(libs.androidx.profileinstaller)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    // Compose UI, accessibility and Hilt graph tests on the JVM (Robolectric, SDK 34 on JDK 17).
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
    // Declares the empty ComponentActivity that createComposeRule() starts; debug builds only.
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Roborazzi without its Gradle plugin: tests record PNGs to app/build/outputs/roborazzi when this is set.
// CI uploads that folder as the "screenshots" artifact.
tasks.withType<Test>().configureEach {
    systemProperty("roborazzi.test.record", "true")
}
