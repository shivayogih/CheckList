package com.dataloom.checklist.di

import com.dataloom.checklist.onboarding.DataStoreOnboardingStore
import com.dataloom.checklist.onboarding.OnboardingStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds the first-run flag store (CL-250). Kept apart from [AppModule] so parallel work does not collide. */
@Module
@InstallIn(SingletonComponent::class)
abstract class OnboardingModule {

    @Binds
    abstract fun bindOnboardingStore(impl: DataStoreOnboardingStore): OnboardingStore
}
