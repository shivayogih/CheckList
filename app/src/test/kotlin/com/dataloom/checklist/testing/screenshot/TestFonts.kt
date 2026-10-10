package com.dataloom.checklist.testing.screenshot

import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import android.graphics.fonts.Font as PlatformFont
import android.graphics.fonts.FontFamily as PlatformFontFamily

/**
 * Fonts for screenshot tests. Robolectric ships Roboto and little else: no Kannada, no Tamil and no colour
 * emoji, so those would be drawn as empty boxes in every golden. The fonts below are **test resources only**
 * (`src/test/resources/screenshot-fonts`); the app itself uses the system fonts and bundles none.
 * The folder is not called `fonts` on purpose: Robolectric native graphics finds its own system fonts through
 * a classpath folder of that name, and a test folder with the same name hides them (Typeface fails to start).
 *
 *  - Noto Sans for Kannada, Tamil, Malayalam, Devanagari (Hindi, Marathi) and Telugu, regular and bold, each
 *    cut to its own script block with `pyftsubset` so Latin text still uses the platform font (SIL OFL 1.1,
 *    see `OFL.txt`).
 *  - Noto Color Emoji, cut down with `pyftsubset` to the ~25 emoji the test scenes use (the full font is 25 MB).
 *
 * They are chained as **custom fallbacks in front of the system font** in one `Typeface`, so Latin text
 * still uses the platform font and only characters it lacks come from these files. If the platform refuses
 * to build the chain, [familyOrNull] is `null` and the tests run with the default fonts; the golden then
 * shows boxes for those scripts, which is visible in review.
 */
internal object TestFonts {

    private fun resource(name: String): File {
        val url = requireNotNull(TestFonts::class.java.classLoader?.getResource("screenshot-fonts/$name")) {
            "Test font screenshot-fonts/$name is missing from src/test/resources"
        }
        return File(url.toURI())
    }

    private fun family(vararg names: String): PlatformFontFamily {
        val fonts = names.map { PlatformFont.Builder(resource(it)).build() }
        val builder = PlatformFontFamily.Builder(fonts.first())
        fonts.drop(1).forEach { builder.addFont(it) }
        return builder.build()
    }

    private val typeface: Typeface? by lazy {
        try {
            Typeface.CustomFallbackBuilder(family("NotoColorEmoji-subset.ttf"))
                .addCustomFallback(family("NotoSansKannada_400Regular.ttf", "NotoSansKannada_700Bold.ttf"))
                .addCustomFallback(family("NotoSansTamil_400Regular.ttf", "NotoSansTamil_700Bold.ttf"))
                .addCustomFallback(family("NotoSansMalayalam_400Regular.ttf", "NotoSansMalayalam_700Bold.ttf"))
                .addCustomFallback(family("NotoSansDevanagari_400Regular.ttf", "NotoSansDevanagari_700Bold.ttf"))
                .addCustomFallback(family("NotoSansTelugu_400Regular.ttf", "NotoSansTelugu_700Bold.ttf"))
                .setSystemFallback("sans-serif")
                .build()
        } catch (t: Throwable) {
            val reason = "${t::class.java.simpleName}: ${t.message}"
            System.err.println("TestFonts: could not build the fallback typeface ($reason)")
            null
        }
    }

    /** The font family every screenshot test uses, or `null` to keep the default fonts. */
    val familyOrNull: FontFamily? by lazy { typeface?.let { FontFamily(it) } }
}
