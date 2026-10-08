package com.dataloom.checklist.ai.di

import com.dataloom.checklist.ai.parser.LanguagePacks
import com.dataloom.checklist.ai.parser.OfflineCommandParser
import com.dataloom.checklist.ai.policy.AiSettingsSource
import com.dataloom.checklist.ai.service.AIService
import com.dataloom.checklist.ai.service.CompositeAIService
import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.Optional
import javax.inject.Qualifier
import javax.inject.Singleton

/** Marks the online [AIService] (for example Gemini through Firebase AI Logic) once one is installed. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OnlineAiService

/**
 * Marks the app's [AiSettingsSource] (the Settings store). The slot needs its own key: [AiModule]
 * itself provides the unqualified [AiSettingsSource] (falling back to [AiSettingsSource.DISABLED]),
 * so an unqualified binding in the app would be a duplicate binding.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AiSettingsStore

/**
 * Hilt bindings of the AI layer, installed app-wide automatically because :app depends on :ai.
 * Everything else (validator, policy, executor, [com.dataloom.checklist.ai.AiAssistant]) has an
 * `@Inject` constructor built from domain use cases.
 *
 * Two optional slots let the app or a later module plug in without editing this one:
 * - `@AiSettingsStore AiSettingsSource`: the Settings toggles. Unbound means AI stays off
 *   ([AiSettingsSource.DISABLED]). Everything in :ai injects the unqualified [AiSettingsSource] provided here.
 * - `@OnlineAiService AIService`: the online provider. Unbound means offline only.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @BindsOptionalOf
    @AiSettingsStore
    abstract fun optionalSettings(): AiSettingsSource

    @BindsOptionalOf
    @OnlineAiService
    abstract fun optionalOnlineService(): AIService

    companion object {

        /** Parsed once; packs are immutable. */
        @Provides
        @Singleton
        fun provideLanguagePacks(): LanguagePacks = LanguagePacks.bundled()

        @Provides
        fun provideSettings(@AiSettingsStore settings: Optional<AiSettingsSource>): AiSettingsSource = settings.orElse(AiSettingsSource.DISABLED)

        @Provides
        fun provideAiService(
            offline: OfflineCommandParser,
            @OnlineAiService online: Optional<AIService>,
            settings: AiSettingsSource,
        ): AIService = CompositeAIService(offline, online.orElse(null), settings)
    }
}
