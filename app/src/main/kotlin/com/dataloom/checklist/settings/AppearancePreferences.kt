package com.dataloom.checklist.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Light or dark colours; [SYSTEM] follows the phone. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

private const val LARGE_SCALE = 1.15f
private const val EXTRA_LARGE_SCALE = 1.3f

/** The in-app text size step, applied on top of the phone's own font size. */
enum class TextSize(val scale: Float) {
    NORMAL(1f),
    LARGE(LARGE_SCALE),
    EXTRA_LARGE(EXTRA_LARGE_SCALE),
}

/** How the app looks (CL-302): theme, text size and the 7:1 high-contrast palette. */
data class Appearance(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val textSize: TextSize = TextSize.NORMAL,
    val highContrast: Boolean = false,
)

interface AppearancePreferences {
    val appearance: Flow<Appearance>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setTextSize(size: TextSize)

    suspend fun setHighContrast(enabled: Boolean)
}

/**
 * [AppearancePreferences] in the app's settings DataStore. An unreadable file or an unknown stored name
 * reads as the defaults (follow the phone, normal size, normal contrast).
 */
@Singleton
class DataStoreAppearancePreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : AppearancePreferences {

    override val appearance: Flow<Appearance> = dataStore.data
        .catch { failure -> if (failure is IOException) emit(emptyPreferences()) else throw failure }
        .map { prefs ->
            Appearance(
                themeMode = ThemeMode.entries.firstOrNull { it.name == prefs[THEME_MODE] } ?: ThemeMode.SYSTEM,
                textSize = TextSize.entries.firstOrNull { it.name == prefs[TEXT_SIZE] } ?: TextSize.NORMAL,
                highContrast = prefs[HIGH_CONTRAST] ?: false,
            )
        }
        .distinctUntilChanged()

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    override suspend fun setTextSize(size: TextSize) {
        dataStore.edit { it[TEXT_SIZE] = size.name }
    }

    override suspend fun setHighContrast(enabled: Boolean) {
        dataStore.edit { it[HIGH_CONTRAST] = enabled }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("appearance_theme_mode")
        val TEXT_SIZE = stringPreferencesKey("appearance_text_size")
        val HIGH_CONTRAST = booleanPreferencesKey("appearance_high_contrast")
    }
}
