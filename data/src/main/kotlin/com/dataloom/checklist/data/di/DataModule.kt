package com.dataloom.checklist.data.di

import android.content.Context
import com.dataloom.checklist.data.local.database.CheckListDatabase
import com.dataloom.checklist.data.repository.RoomCatalogRepository
import com.dataloom.checklist.data.repository.RoomChecklistRepository
import com.dataloom.checklist.data.seed.AssetSeedSource
import com.dataloom.checklist.data.seed.SeedLoader
import com.dataloom.checklist.data.seed.SeedSource
import com.dataloom.checklist.domain.common.Clock
import com.dataloom.checklist.domain.common.IdGenerator
import com.dataloom.checklist.domain.repository.CatalogRepository
import com.dataloom.checklist.domain.repository.ChecklistRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the domain repository interfaces to their Room implementations, app-wide. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindChecklistRepository(impl: RoomChecklistRepository): ChecklistRepository

    @Binds
    @Singleton
    abstract fun bindCatalogRepository(impl: RoomCatalogRepository): CatalogRepository

    @Binds
    abstract fun bindSeedSource(impl: AssetSeedSource): SeedSource

    companion object {

        /** One database per process: Room's invalidation tracking only works within one instance. */
        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context, seedLoader: SeedLoader): CheckListDatabase =
            CheckListDatabase.build(context, seedLoader)

        @Provides
        fun provideClock(): Clock = Clock.SYSTEM

        @Provides
        fun provideIdGenerator(): IdGenerator = IdGenerator.UUID_V4
    }
}
