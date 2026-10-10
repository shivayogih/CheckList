package com.dataloom.checklist.domain.localization

/**
 * The languages of the first release. Adding a language means adding it here and adding its
 * resource translations; no business logic changes.
 */
object SupportedLanguages {

    val ENGLISH = AppLanguage(tag = "en", nativeName = "English")

    val all: List<AppLanguage> = listOf(
        ENGLISH,
        AppLanguage(tag = "kn", nativeName = "ಕನ್ನಡ"),
        AppLanguage(tag = "hi", nativeName = "हिन्दी"),
        AppLanguage(tag = "ta", nativeName = "தமிழ்"),
        AppLanguage(tag = "te", nativeName = "తెలుగు"),
        AppLanguage(tag = "mr", nativeName = "मराठी"),
        AppLanguage(tag = "ml", nativeName = "മലയാളം"),
    )

    /**
     * Finds a supported language for a BCP 47 tag, ignoring region and case ("kn-IN" -> Kannada).
     * Returns null for blank or unsupported tags.
     */
    fun fromTag(tag: String?): AppLanguage? {
        val language = tag?.trim()?.substringBefore('-')?.substringBefore('_')?.lowercase()
        if (language.isNullOrEmpty()) return null
        return all.firstOrNull { it.tag == language }
    }

    /**
     * Maps the platform's stored application locales (comma-separated tags, empty when the user
     * never chose one) to a preference. Only the first tag matters because the app sets one locale.
     */
    fun preferenceFromTags(tags: String?): LanguagePreference {
        val language = fromTag(tags?.substringBefore(','))
        return if (language == null) LanguagePreference.SystemDefault else LanguagePreference.Specific(language)
    }
}
