package com.dataloom.checklist.data.mapper

import java.text.Collator
import java.util.Locale

/**
 * Display name resolution for seeded data (section 6.2):
 * custom name > translation[locale] > translation["en"] > humanized canonical key.
 */
object DisplayNames {

    const val FALLBACK_LOCALE = "en"

    /** "kn-IN" or "kn_IN" -> "kn", the form translation rows are keyed by. */
    fun language(locale: String): String =
        locale.trim().substringBefore('-').substringBefore('_').lowercase(Locale.ROOT)
            .ifEmpty { FALLBACK_LOCALE }

    /** Locales to load translations for, most preferred first. */
    fun lookupLocales(locale: String): List<String> = listOf(language(locale), FALLBACK_LOCALE).distinct()

    /** [translations] maps locale -> name for one canonical key. */
    fun resolve(customName: String?, canonicalKey: String?, translations: Map<String, String>, locale: String): String =
        customName?.takeIf { it.isNotBlank() }
            ?: translations[language(locale)]
            ?: translations[FALLBACK_LOCALE]
            ?: humanize(canonicalKey.orEmpty())

    /** "cooking_oil" -> "Cooking oil". A last resort, so a missing translation never shows a blank row. */
    fun humanize(canonicalKey: String): String =
        canonicalKey.replace('_', ' ').replace('-', ' ').trim()
            .replaceFirstChar { it.titlecase(Locale.ROOT) }

    /** Alphabetical order in the user's language, ignoring case. */
    fun collator(locale: String): Collator =
        Collator.getInstance(Locale.forLanguageTag(language(locale))).apply { strength = Collator.SECONDARY }
}
