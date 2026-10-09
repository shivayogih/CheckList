package com.dataloom.checklist.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG 2.1 AA contrast of the app's colour schemes (CL-144, docs/accessibility.md): at least
 * 4.5:1 for text and 3:1 for UI component boundaries, in light and dark. The pairs are the roles
 * Material 3 components actually draw together, including the roles the theme does not set and
 * inherits from the Material baseline.
 */
class ThemeContrastTest {

    @Test
    fun `light scheme meets WCAG AA`() = assertScheme("light", LightColors)

    @Test
    fun `dark scheme meets WCAG AA`() = assertScheme("dark", DarkColors)

    @Test
    fun `contrast formula matches known WCAG values`() {
        assertTrue(contrast(Color.Black, Color.White) in 20.99..21.01)
        assertTrue(contrast(Color.White, Color.White) in 0.99..1.01)
        // #767676 on white is the classic 4.54:1 minimum grey.
        assertTrue(contrast(Color(0xFF767676), Color.White) in 4.5..4.6)
    }

    private fun assertScheme(name: String, s: ColorScheme) {
        val text = listOf(
            "onPrimary on primary" to (s.onPrimary to s.primary),
            "onPrimaryContainer on primaryContainer" to (s.onPrimaryContainer to s.primaryContainer),
            "onSecondaryContainer on secondaryContainer (selected chips)" to
                (s.onSecondaryContainer to s.secondaryContainer),
            "onBackground on background" to (s.onBackground to s.background),
            "onSurface on surface" to (s.onSurface to s.surface),
            "onSurfaceVariant on surface (secondary text)" to (s.onSurfaceVariant to s.surface),
            "onSurfaceVariant on surfaceVariant" to (s.onSurfaceVariant to s.surfaceVariant),
            "onSurface on surfaceContainerHighest (cards)" to (s.onSurface to s.surfaceContainerHighest),
            "onSurface on surfaceContainerHigh (dialogs)" to (s.onSurface to s.surfaceContainerHigh),
            "onSurface on surfaceContainer (bars)" to (s.onSurface to s.surfaceContainer),
            "primary on surface (text buttons)" to (s.primary to s.surface),
            "primary on surfaceContainerHigh (dialog buttons)" to (s.primary to s.surfaceContainerHigh),
            "error on surface (field errors)" to (s.error to s.surface),
            "onError on error" to (s.onError to s.error),
            "onErrorContainer on errorContainer (notices)" to (s.onErrorContainer to s.errorContainer),
            "inverseOnSurface on inverseSurface (snackbars)" to (s.inverseOnSurface to s.inverseSurface),
            "inversePrimary on inverseSurface (snackbar action)" to (s.inversePrimary to s.inverseSurface),
        )
        val ui = listOf(
            "outline on surface (field and button borders)" to (s.outline to s.surface),
            "primary on surface (checkbox, switch, progress)" to (s.primary to s.surface),
            "onSurfaceVariant on surface (unchecked checkbox)" to (s.onSurfaceVariant to s.surface),
        )
        val failures = text.mapNotNull { (label, pair) -> failure(label, pair, TEXT_MIN) } +
            ui.mapNotNull { (label, pair) -> failure(label, pair, UI_MIN) }
        assertTrue("$name scheme below WCAG AA:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    private fun failure(label: String, pair: Pair<Color, Color>, minimum: Double): String? {
        val ratio = contrast(pair.first, pair.second)
        return if (ratio + 1e-9 >= minimum) null else "$label: %.2f:1 < $minimum:1".format(ratio)
    }

    private companion object {
        const val TEXT_MIN = 4.5
        const val UI_MIN = 3.0

        /** WCAG 2.1 contrast ratio from sRGB relative luminance. */
        fun contrast(a: Color, b: Color): Double {
            val la = luminance(a)
            val lb = luminance(b)
            return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
        }

        fun luminance(color: Color): Double {
            val argb = color.toArgb()
            fun channel(shift: Int): Double {
                val c = ((argb shr shift) and 0xFF) / 255.0
                return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        }
    }
}
