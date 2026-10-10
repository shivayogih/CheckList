package com.dataloom.checklist.di

import com.dataloom.checklist.domain.common.DefaultDispatcher
import com.dataloom.checklist.localization.AppCompatLanguageProvider
import com.dataloom.checklist.localization.AppLanguageProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * A scope that outlives every screen. Used only to finish a write the user already confirmed (for
 * example an item deletion whose Undo window was still open when the screen closed) and to warm up
 * the database at launch. Nothing that belongs to a screen may run here: that is what viewModelScope is for.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    abstract fun bindLanguageProvider(impl: AppCompatLanguageProvider): AppLanguageProvider

    companion object {
        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(@DefaultDispatcher default: CoroutineDispatcher): CoroutineScope =
            // SupervisorJob: one failed write must not cancel the others. No exception handler on purpose:
            // an unexpected failure should reach the default handler and be seen, not be swallowed.
            CoroutineScope(SupervisorJob() + default + CoroutineName("application"))
    }
}
