package com.dataloom.checklist.testing.screenshot

/**
 * One way to render a component: theme, font scale and language. Every component is rendered in all of
 * them, in the 7 languages of the app (en, kn, hi, ta, te, mr, ml), at 100 % and at 200 % font size, and the
 * layout assertions run on each (no clipped text, no tap target under 48 dp).
 *
 * A golden image is kept only where [golden] is true: light and dark, English at 200 %, and Kannada, Tamil
 * and Malayalam at 100 % and 200 %. The other languages are checked by the assertions alone, so the
 * repository does not carry 15 images per component.
 *
 * [id] is the suffix of the golden file name, so renaming one renames every golden.
 */
enum class ScreenshotVariant(
    val id: String,
    val dark: Boolean,
    val fontScale: Float,
    val language: String,
    val golden: Boolean,
    val highContrast: Boolean = false,
) {
    Light("light", dark = false, fontScale = 1f, language = "en", golden = true),
    Dark("dark", dark = true, fontScale = 1f, language = "en", golden = true),
    Large("font200", dark = false, fontScale = 2f, language = "en", golden = true),
    Kannada("kn", dark = false, fontScale = 1f, language = "kn", golden = true),
    Tamil("ta", dark = false, fontScale = 1f, language = "ta", golden = true),
    Malayalam("ml", dark = false, fontScale = 1f, language = "ml", golden = true),
    Hindi("hi", dark = false, fontScale = 1f, language = "hi", golden = false),
    Telugu("te", dark = false, fontScale = 1f, language = "te", golden = false),
    Marathi("mr", dark = false, fontScale = 1f, language = "mr", golden = false),
    Kannada200("kn_font200", dark = false, fontScale = 2f, language = "kn", golden = true),
    Tamil200("ta_font200", dark = false, fontScale = 2f, language = "ta", golden = true),
    Malayalam200("ml_font200", dark = false, fontScale = 2f, language = "ml", golden = true),
    Hindi200("hi_font200", dark = false, fontScale = 2f, language = "hi", golden = false),
    Telugu200("te_font200", dark = false, fontScale = 2f, language = "te", golden = false),
    Marathi200("mr_font200", dark = false, fontScale = 2f, language = "mr", golden = false),
}

/**
 * Picks the sample text for the variant's language. Kannada and Tamil have written translations (the two
 * mockup languages). For Malayalam, Hindi, Telugu and Marathi the text is **synthetic**: real words of
 * that script, repeated to 1.3 times the length of the English text. It exercises glyph height, conjuncts
 * and wrapping at realistic widths; it is not a translation.
 */
class Sample(val language: String) {
    fun t(en: String, kn: String = en, ta: String = en): String = when (language) {
        "en" -> en
        "kn" -> kn
        "ta" -> ta
        else -> stress(en, WORDS.getValue(language))
    }

    private fun stress(en: String, words: List<String>): String {
        val wanted = (en.length * 1.3).toInt().coerceAtLeast(4)
        val out = StringBuilder()
        var i = Math.floorMod(en.hashCode(), words.size)
        while (out.length < wanted) {
            if (out.isNotEmpty()) out.append(' ')
            out.append(words[i % words.size])
            i++
        }
        return out.toString()
    }

    private companion object {
        val WORDS = mapOf(
            "ml" to listOf("പട്ടിക", "സാധനങ്ങൾ", "ഷോപ്പിംഗ്", "പൂർത്തിയായി", "വിവരണം", "സംരക്ഷിക്കുക"),
            "hi" to listOf("सूचियाँ", "खरीदारी", "पूर्ण", "सामग्री", "जोड़ें", "संपादित"),
            "te" to listOf("జాబితాలు", "కొనుగోలు", "పూర్తయింది", "వస్తువులు", "జోడించండి", "సవరించు"),
            "mr" to listOf("यादी", "खरेदी", "पूर्ण", "वस्तू", "जोडा", "संपादन"),
        )
    }
}
