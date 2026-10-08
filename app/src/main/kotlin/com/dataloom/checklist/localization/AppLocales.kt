package com.dataloom.checklist.localization

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.dataloom.checklist.domain.localization.LanguagePreference
import com.dataloom.checklist.domain.localization.SupportedLanguages

/**
 * Reads and changes the app language through AndroidX per-app locales (ADR-007).
 *
 * The platform (Android 13+) or AppCompat (Android 8-12) owns the stored value, so the app keeps
 * no copy of its own. Changing it recreates the activity in the new language.
 */
object AppLocales {

    fun current(): LanguagePreference =
        SupportedLanguages.preferenceFromTags(AppCompatDelegate.getApplicationLocales().toLanguageTags())

    /** Must be called on the main thread. */
    fun apply(preference: LanguagePreference) {
        val locales = when (preference) {
            LanguagePreference.SystemDefault -> LocaleListCompat.getEmptyLocaleList()
            is LanguagePreference.Specific -> LocaleListCompat.forLanguageTags(preference.language.tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
