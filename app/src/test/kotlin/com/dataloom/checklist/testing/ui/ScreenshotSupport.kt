package com.dataloom.checklist.testing.ui

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.dataloom.checklist.presentation.theme.CheckListTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.util.Locale

/*
 * Shared helpers for Compose UI and screenshot tests on Robolectric (CL-216). They know nothing about
 * a particular screen, so every feature's tests (and the UI foundation track) can reuse them.
 *
 * Screenshots are recorded with Roborazzi (library only, no Gradle plugin) to
 * app/build/outputs/roborazzi, which CI uploads as the "screenshots" artifact. Tests need
 * `@RunWith(RobolectricTestRunner::class)` and `@GraphicsMode(GraphicsMode.Mode.NATIVE)`.
 */

/** Where screenshots are written, relative to the :app module directory (Gradle's test working directory). */
const val SCREENSHOT_DIR = "build/outputs/roborazzi"

/** The look of one screenshot: theme, font scale and language. */
data class ScreenshotVariant(
    val name: String,
    val darkTheme: Boolean = false,
    val fontScale: Float = 1f,
    /** BCP 47 tag such as "ta"; null keeps English. */
    val languageTag: String? = null,
) {
    companion object {
        val LIGHT = ScreenshotVariant("light")
        val DARK = ScreenshotVariant("dark", darkTheme = true)
        val LARGE_FONT = ScreenshotVariant("font200", fontScale = 2f)
        val TAMIL = ScreenshotVariant("tamil", languageTag = "ta")

        /** The four variants the spec asks for: light, dark, 200 % font scale, and one Tamil locale. */
        val STANDARD = listOf(LIGHT, DARK, LARGE_FONT, TAMIL)
    }
}

/**
 * Hosts [content] in the app theme with the variant's dark mode, font scale and locale, so
 * `stringResource` returns the translated text and text scales exactly like on a phone.
 */
@Composable
fun ScreenshotEnvironment(variant: ScreenshotVariant, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    val configuration = remember(variant, baseConfiguration) {
        Configuration(baseConfiguration).apply {
            fontScale = variant.fontScale
            variant.languageTag?.let {
                val locale = Locale.forLanguageTag(it)
                setLocales(LocaleList(locale))
                setLayoutDirection(locale)
            }
        }
    }
    val context = remember(variant) { base.createConfigurationContext(configuration) }
    CompositionLocalProvider(
        LocalContext provides context,
        LocalConfiguration provides configuration,
        LocalResources provides context.resources,
        LocalDensity provides Density(context.resources.displayMetrics.density, variant.fontScale),
    ) {
        CheckListTheme(darkTheme = variant.darkTheme) {
            Surface(color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize()) { content() }
            }
        }
    }
}

/** Renders [content] for [variant] and records `<SCREENSHOT_DIR>/<name>-<variant>.png`. */
fun ComposeContentTestRule.capture(name: String, variant: ScreenshotVariant, content: @Composable () -> Unit) {
    captureAll(name, listOf(variant), content)
}

/**
 * Records one PNG per variant from a single composition (a rule accepts `setContent` once per test):
 * the variant is switched between captures, so the same content is shown light, dark, scaled and translated.
 */
fun ComposeContentTestRule.captureAll(
    name: String,
    variants: List<ScreenshotVariant> = ScreenshotVariant.STANDARD,
    content: @Composable () -> Unit,
) {
    var current by mutableStateOf(variants.first())
    setContent { ScreenshotEnvironment(current, content) }
    variants.forEach { variant ->
        current = variant
        waitForIdle()
        onRoot().captureRoboImage("$SCREENSHOT_DIR/$name-${variant.name}.png")
    }
}
