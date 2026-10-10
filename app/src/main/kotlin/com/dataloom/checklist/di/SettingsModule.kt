package com.dataloom.checklist.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.dataloom.checklist.ai.di.AiSettingsStore
import com.dataloom.checklist.ai.policy.AiSettingsSource
import com.dataloom.checklist.settings.AiPreferences
import com.dataloom.checklist.settings.AppearancePreferences
import com.dataloom.checklist.settings.DataStoreAiPreferences
import com.dataloom.checklist.settings.DataStoreAppearancePreferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * App settings stored in a Preferences DataStore. Binding [DataStoreAiPreferences] into the AI layer's
 * optional `@AiSettingsStore` slot is what lets AI run at all: without it AI stays off (see AiModule).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {

    @Binds
    abstract fun bindAiPreferences(impl: DataStoreAiPreferences): AiPreferences

    @Binds
    abstract fun bindAppearancePreferences(impl: DataStoreAppearancePreferences): AppearancePreferences

    @Binds
    @AiSettingsStore
    abstract fun bindAiSettingsSource(impl: DataStoreAiPreferences): AiSettingsSource

    companion object {
        /** One DataStore per file and process, so it must be a singleton. */
        @Provides
        @Singleton
        fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(DataStoreAiPreferences.FILE_NAME) }
    }
}
