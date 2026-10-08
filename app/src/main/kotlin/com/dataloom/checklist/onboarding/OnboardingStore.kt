package com.dataloom.checklist.onboarding

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Remembers whether the first-run flow has been finished (CL-250). The flag is a plain
 * convenience, not personal data, so it lives in Preferences DataStore, not in the encrypted profile.
 *
 * Contract: the flag only ever goes from false to true. Replaying the tutorial from Settings never
 * touches it.
 */
interface OnboardingStore {

    /** True once the user finished or skipped the first-run flow. A read error counts as "not yet". */
    val completed: Flow<Boolean>

    suspend fun markCompleted()
}

private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(name = "onboarding")

@Singleton
class DataStoreOnboardingStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : OnboardingStore {

    override val completed: Flow<Boolean> = context.onboardingDataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it[KEY_COMPLETED] ?: false }

    override suspend fun markCompleted() {
        context.onboardingDataStore.edit { it[KEY_COMPLETED] = true }
    }

    private companion object {
        /** The flag name from UI-SPEC section 1. */
        val KEY_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }
}
