package com.dataloom.checklist.data.di

import android.content.Context
import com.dataloom.checklist.data.profile.AndroidKeystoreMasterKey
import com.dataloom.checklist.data.profile.EncryptedProfileRepository
import com.dataloom.checklist.data.profile.KeysetProfileAeadProvider
import com.dataloom.checklist.data.profile.ProfileAeadProvider
import com.dataloom.checklist.domain.repository.ProfileRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Encrypted profile (Phase 5). A separate module from [DataModule] so the profile wiring stays in
 * one place. Both bindings are singletons: the repository holds the reset notice and write lock,
 * the provider caches the unwrapped keyset.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ProfileModule {

    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: EncryptedProfileRepository): ProfileRepository

    companion object {

        @Provides
        @Singleton
        fun provideProfileAeadProvider(@ApplicationContext context: Context): ProfileAeadProvider =
            KeysetProfileAeadProvider(
                prefs = context.getSharedPreferences(KeysetProfileAeadProvider.PREFS_FILE, Context.MODE_PRIVATE),
                masterKey = AndroidKeystoreMasterKey(KeysetProfileAeadProvider.MASTER_KEY_ALIAS),
            )
    }
}
