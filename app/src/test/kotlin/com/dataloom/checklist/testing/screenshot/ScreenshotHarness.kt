package com.dataloom.checklist.testing.screenshot

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.presentation.components.PreviewSurface
import com.github.takahirom.roborazzi.captureRoboImage
import java.util.Locale

/*
 * The screenshot harness (CL-262). A test does three things:
 *
 *     compose.setScreenshotContent(variant) { sample -> MyComponent(sample.t("Rice", kn = "...", ta = "...")) }
 *     compose.assertNoTextOverflow()
 *     compose.assertTouchTargetsAtLeast48()
 *     compose.captureScene("my_component", variant)
 *
 * The variant sets the theme (light, dark, high contrast), the font scale and the language **inside** the
 * composition, so no Activity is recreated and nothing leaks between tests.
 */

internal const val SCENE_TAG = "screenshot-scene"

/** Where the goldens live, relative to the module directory (Gradle runs unit tests from there). */
internal const val GOLDEN_DIR = "src/test/screenshots"

/** The width of the scene in dp: the 412 dp phone of the mockups. */
private const val SCENE_WIDTH_DP = 412

/**
 * Sets the content of the test rule inside a themed 412 dp wide scene. [content] gets a [Sample] to
 * choose English, Kannada or Tamil texts.
 */
internal fun ComposeContentTestRule.setScreenshotContent(
    variant: ScreenshotVariant,
    content: @Composable (Sample) -> Unit,
) {
    setContent {
        ScreenshotEnvironment(variant) {
            Box(Modifier.testTag(SCENE_TAG).width(SCENE_WIDTH_DP.dp)) {
                PreviewSurface(darkTheme = variant.dark, highContrast = variant.highContrast) {
                    content(Sample(variant.language))
                }
            }
        }
    }
}

/**
 * Applies the variant's font scale, locale and test fonts to everything inside. The locale goes into the
 * `Configuration` and the `Context`, so `stringResource` in a screen picks `values-kn` too.
 */
@Composable
internal fun ScreenshotEnvironment(variant: ScreenshotVariant, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val baseDensity = LocalDensity.current
    val locale = Locale.forLanguageTag(variant.language)
    val configuration = Configuration(LocalConfiguration.current).apply {
        setLocale(locale)
        fontScale = variant.fontScale
    }
    val context = base.createConfigurationContext(configuration)
    CompositionLocalProvider(
        LocalContext provides context,
        LocalConfiguration provides configuration,
        LocalDensity provides Density(baseDensity.density, variant.fontScale),
    ) {
        ProvideTextStyle(TextStyle(fontFamily = TestFonts.familyOrNull)) { content() }
    }
}

/** The tagged scene, which is only as tall as its content, so goldens are not 917 dp of white. */
internal fun ComposeContentTestRule.scene(): SemanticsNodeInteraction = onNodeWithTag(SCENE_TAG)

/**
 * Records or verifies the golden `src/test/screenshots/<name>_<variant>.png`. What happens depends on the
 * Gradle property: `-Proborazzi.test.record=true` writes, `-Proborazzi.test.verify=true` compares and fails on a
 * difference, and with neither the call does nothing (so a plain `testDevDebugUnitTest` stays fast).
 */
internal fun ComposeContentTestRule.captureScene(name: String, variant: ScreenshotVariant) {
    waitForIdle()
    if (!variant.golden) return
    scene().captureRoboImage("$GOLDEN_DIR/${name}_${variant.id}.png")
}
