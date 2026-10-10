package com.dataloom.checklist.domain.localization

/**
 * A language the app ships translations for.
 *
 * [tag] is a BCP 47 language tag ("kn"). [nativeName] is shown in the language picker in the
 * language's own script, so a user can find their language whatever the current UI language is.
 */
data class AppLanguage(
    val tag: String,
    val nativeName: String,
)

/** What the user chose in Settings > Language. */
sealed interface LanguagePreference {
    /** Follow the device language (falls back to English when it is not supported). */
    data object SystemDefault : LanguagePreference

    data class Specific(val language: AppLanguage) : LanguagePreference
}
