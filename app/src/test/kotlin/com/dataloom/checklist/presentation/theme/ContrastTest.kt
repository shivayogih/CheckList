package com.dataloom.checklist.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The colour pairs the components draw text and borders with, checked against WCAG 2.2 contrast ratios.
 * The standard palette must reach AA (4.5:1 for text, 3:1 for borders and icons that carry meaning). The
 * high-contrast palette must reach AAA (7:1 for text) and 3:1 for every border.
 *
 * Decorative dividers (`outlineVariant` in the standard palette) are not asserted: the spec uses them as a
 * quiet separator only, never as the single cue for a state.
 */
class ContrastTest {

    private fun ratio(a: Color, b: Color): Double {
        val l1 = a.luminance().toDouble()
        val l2 = b.luminance().toDouble()
        val hi = maxOf(l1, l2)
        val lo = minOf(l1, l2)
        return (hi + 0.05) / (lo + 0.05)
    }

    /** Text colour on background pairs, named so a failure says which one. */
    private fun textPairs(c: ColorScheme, banner: Color): List<Triple<String, Color, Color>> = listOf(
        Triple("onSurface on surface", c.onSurface, c.surface),
        Triple("onBackground on background", c.onBackground, c.background),
        Triple("onSurfaceVariant on surface", c.onSurfaceVariant, c.surface),
        Triple("onSurfaceVariant on surfaceVariant", c.onSurfaceVariant, c.surfaceVariant),
        Triple("onSurfaceVariant on surfaceContainer", c.onSurfaceVariant, c.surfaceContainer),
        Triple("onPrimary on primary", c.onPrimary, c.primary),
        Triple("onPrimaryContainer on primaryContainer", c.onPrimaryContainer, c.primaryContainer),
        Triple("primary on background", c.primary, c.background),
        Triple("primary on surfaceVariant", c.primary, c.surfaceVariant),
        Triple("primary on surfaceContainer", c.primary, c.surfaceContainer),
        Triple("primary on banner", c.primary, banner),
        Triple("onSurface on banner", c.onSurface, banner),
        Triple("error on background", c.error, c.background),
        Triple("error on surfaceContainer", c.error, c.surfaceContainer),
        Triple("onError on error", c.onError, c.error),
        Triple("onErrorContainer on errorContainer", c.onErrorContainer, c.errorContainer),
        Triple("inverseOnSurface on inverseSurface", c.inverseOnSurface, c.inverseSurface),
        Triple("inversePrimary on inverseSurface", c.inversePrimary, c.inverseSurface),
    )

    private fun borderPairs(c: ColorScheme): List<Triple<String, Color, Color>> = listOf(
        Triple("outline on background", c.outline, c.background),
        Triple("outline on surfaceVariant", c.outline, c.surfaceVariant),
        Triple("outline on surfaceContainer", c.outline, c.surfaceContainer),
    )

    private fun check(name: String, c: ColorScheme, banner: Color, text: Double, border: Double, decorative: Boolean) {
        val failures = mutableListOf<String>()
        textPairs(c, banner).forEach { (label, fg, bg) ->
            val r = ratio(fg, bg)
            if (r < text) failures += "$name: $label is %.2f:1, needs $text:1".format(r)
        }
        borderPairs(c).forEach { (label, fg, bg) ->
            val r = ratio(fg, bg)
            if (r < border) failures += "$name: $label is %.2f:1, needs $border:1".format(r)
        }
        if (!decorative) {
            val r = ratio(c.outlineVariant, c.background)
            if (r < border) failures += "$name: outlineVariant on background is %.2f:1, needs $border:1".format(r)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `light palette reaches AA`() =
        check("light", LightColors, LightExtendedColors.bannerContainer, text = 4.5, border = 3.0, decorative = true)

    @Test
    fun `dark palette reaches AA`() =
        check("dark", DarkColors, DarkExtendedColors.bannerContainer, text = 4.5, border = 3.0, decorative = true)

    @Test
    fun `high contrast light palette reaches 7 to 1 for text and 3 to 1 for borders`() = check(
        "high contrast light",
        HighContrastLightColors,
        HighContrastLightExtendedColors.bannerContainer,
        text = 7.0,
        border = 3.0,
        decorative = false,
    )

    @Test
    fun `high contrast dark palette reaches 7 to 1 for text and 3 to 1 for borders`() = check(
        "high contrast dark",
        HighContrastDarkColors,
        HighContrastDarkExtendedColors.bannerContainer,
        text = 7.0,
        border = 3.0,
        decorative = false,
    )

    @Test
    fun `typography never goes below the 15 sp floor`() {
        val t = CheckListTypography
        val styles = listOf(
            t.displayLarge, t.displayMedium, t.displaySmall, t.headlineLarge, t.headlineMedium, t.headlineSmall,
            t.titleLarge, t.titleMedium, t.titleSmall, t.bodyLarge, t.bodyMedium, t.bodySmall,
            t.labelLarge, t.labelMedium, t.labelSmall,
            CheckListText.bigCount, CheckListText.percent, CheckListText.stepperValue, CheckListText.progressLabel,
            CheckListText.unitChip, CheckListText.chip, CheckListText.sectionHeader, CheckListText.categoryName,
            CheckListText.rowTitle, CheckListText.subtitle, CheckListText.emojiSmall, CheckListText.emojiLarge,
        )
        styles.forEach { assertTrue("font size ${it.fontSize} is below 15 sp", it.fontSize.value >= 15f) }
    }
}
