package com.dataloom.checklist.data.di

import com.dataloom.checklist.domain.common.DefaultDispatcher
import com.dataloom.checklist.domain.common.IoDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * The only place in the app that names a concrete dispatcher. Everything else asks for
 * [IoDispatcher] or [DefaultDispatcher], so tests can replace both with a test dispatcher (use
 * `@TestInstallIn(replaces = [CoroutinesModule::class])`) and a source scan in :domain's tests
 * fails the build if `Dispatchers.IO` or `Dispatchers.Default` appears anywhere else.
 */
@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
