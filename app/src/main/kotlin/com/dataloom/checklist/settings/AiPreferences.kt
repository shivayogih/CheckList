package com.dataloom.checklist.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.dataloom.checklist.ai.policy.AiSettings
import com.dataloom.checklist.ai.policy.AiSettingsSource
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The user's AI choices from Settings (CL-240). Everything is off until the user turns it on.
 * Online AI (CL-211) is not offered yet, so [AiSettings.onlineEnabled] is always false.
 */
interface AiPreferences {
    val settings: Flow<AiSettings>

    /** Turning the assistant off also turns "add without asking" off, so it never comes back on by itself. */
    suspend fun setEnabled(enabled: Boolean)

    /** Ignored while the assistant is off. */
    suspend fun setAutoExecuteSimple(enabled: Boolean)
}

/**
 * [AiPreferences] in a Preferences DataStore. Also the [AiSettingsSource] the AI layer reads before every
 * call (bound in [com.dataloom.checklist.di.SettingsModule]), so switching the toggle off stops AI at once.
 * An unreadable file reads as the defaults: AI off.
 */
@Singleton
class DataStoreAiPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : AiPreferences, AiSettingsSource {

    override val settings: Flow<AiSettings> = dataStore.data
        .catch { failure -> if (failure is IOException) emit(emptyPreferences()) else throw failure }
        .map { prefs ->
            val enabled = prefs[ENABLED] ?: false
            AiSettings(
                enabled = enabled,
                onlineEnabled = false,
                autoExecuteSimple = enabled && (prefs[AUTO_EXECUTE_SIMPLE] ?: false),
            )
        }
        .distinctUntilChanged()

    override suspend fun current(): AiSettings = settings.first()

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[ENABLED] = enabled
            if (!enabled) prefs[AUTO_EXECUTE_SIMPLE] = false
        }
    }

    override suspend fun setAutoExecuteSimple(enabled: Boolean) {
        dataStore.edit { prefs ->
            if (prefs[ENABLED] == true) prefs[AUTO_EXECUTE_SIMPLE] = enabled
        }
    }

    companion object {
        /** File name under the app's datastore folder; holds only these flags, no personal data. */
        const val FILE_NAME = "ai_settings"

        private val ENABLED = booleanPreferencesKey("ai_enabled")
        private val AUTO_EXECUTE_SIMPLE = booleanPreferencesKey("ai_auto_execute_simple")
    }
}
