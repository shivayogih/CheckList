package com.dataloom.checklist.testing.screenshot

/**
 * One way to render a component: theme, font scale and language. Every component gets a golden for each
 * of these (UI-SPEC section 5): light and dark at 100 %, light at 200 %, and Kannada and Tamil, the two
 * scripts with the tallest glyphs.
 *
 * [id] is the suffix of the golden file name, so renaming one renames every golden.
 */
enum class ScreenshotVariant(
    val id: String,
    val dark: Boolean,
    val fontScale: Float,
    val language: String,
    val highContrast: Boolean = false,
) {
    Light("light", dark = false, fontScale = 1f, language = "en"),
    Dark("dark", dark = true, fontScale = 1f, language = "en"),
    Large("font200", dark = false, fontScale = 2f, language = "en"),
    Kannada("kn", dark = false, fontScale = 1f, language = "kn"),
    Tamil("ta", dark = false, fontScale = 1f, language = "ta"),
}

/** Picks the sample text for the variant's language: English, Kannada or Tamil. */
class Sample(val language: String) {
    fun t(en: String, kn: String = en, ta: String = en): String = when (language) {
        "kn" -> kn
        "ta" -> ta
        else -> en
    }
}
