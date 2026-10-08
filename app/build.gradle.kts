import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    // Annotation processing runs on KSP: kapt is not supported with AGP 9 built-in Kotlin.
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
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
}
