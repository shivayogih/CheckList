package com.dataloom.checklist.di

import android.content.Context
import com.dataloom.checklist.ads.AdPlatform
import com.dataloom.checklist.ads.InstallAge
import com.dataloom.checklist.ads.PackageInstallAge
import com.dataloom.checklist.ads.createAdPlatform
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Ads (CL-370). [createAdPlatform] comes from src/adsOn (AdMob) or src/adsOff (no ads), whichever the
 * flavor's ads switch compiled in, so this module is the same for every build.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AdsModule {

    @Binds
    abstract fun bindInstallAge(impl: PackageInstallAge): InstallAge

    companion object {
        @Provides
        @Singleton
        fun provideAdPlatform(@ApplicationContext context: Context): AdPlatform = createAdPlatform(context)
    }
}
