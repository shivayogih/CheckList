package com.dataloom.checklist.localization

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.dataloom.checklist.domain.localization.SupportedLanguages
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The language tag ("kn") that names in the catalog are resolved in: the app language when the user
 * picked one, otherwise the device language, otherwise English. ViewModels observe it so a screen kept
 * on the back stack follows a language change instead of showing names in the old language.
 */
interface AppLanguageProvider {
    val language: StateFlow<String>
}

/** Reads the per-app locale (ADR-007). [MainActivity] refreshes it each time it is (re)created. */
@Singleton
class AppCompatLanguageProvider @Inject constructor() : AppLanguageProvider {

    private val state = MutableStateFlow(resolve())

    override val language: StateFlow<String> = state.asStateFlow()

    /** A language change recreates the activity, which calls this. */
    fun refresh() {
        state.value = resolve()
    }

    private fun resolve(): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val locale = if (appLocales.isEmpty) LocaleListCompat.getAdjustedDefault()[0] else appLocales[0]
        return (SupportedLanguages.fromTag(locale?.toLanguageTag()) ?: SupportedLanguages.ENGLISH).tag
    }
}
