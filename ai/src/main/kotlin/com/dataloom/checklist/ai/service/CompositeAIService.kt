package com.dataloom.checklist.ai.service

import com.dataloom.checklist.ai.model.ActionPlan
import com.dataloom.checklist.ai.model.AiResult
import com.dataloom.checklist.ai.model.AiSummary
import com.dataloom.checklist.ai.model.CategorySuggestion
import com.dataloom.checklist.ai.model.ChecklistContext
import com.dataloom.checklist.ai.model.GenerateRequest
import com.dataloom.checklist.ai.model.ItemSuggestion
import com.dataloom.checklist.ai.model.ItemSuggestionContext
import com.dataloom.checklist.ai.model.UnavailableReason
import com.dataloom.checklist.ai.model.Utterance
import com.dataloom.checklist.ai.policy.AiSettings
import com.dataloom.checklist.ai.policy.AiSettingsSource
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Picks the best available service (section 11.5): nothing when AI is off; the offline parser first for
 * commands; the online service, when the user enabled it and one is installed, for what the parser
 * cannot do. Online calls have a timeout so AI never blocks the checklist. Failures become [AiResult]
 * values; only cancellation propagates.
 */
class CompositeAIService(
    private val offline: AIService,
    private val online: AIService?,
    private val settings: AiSettingsSource,
    private val onlineTimeoutMillis: Long = DEFAULT_ONLINE_TIMEOUT_MILLIS,
) : AIService {

    override suspend fun interpret(utterance: Utterance, context: ChecklistContext): AiResult<ActionPlan> =
        route { current ->
            val local = guarded { offline.interpret(utterance, context) }
            if (local is AiResult.Success || !current.onlineAllowed()) local else onlineCall { it.interpret(utterance, context) }
        }

    override suspend fun generateChecklist(request: GenerateRequest): AiResult<ActionPlan> =
        route { preferOnline(it) { service -> service.generateChecklist(request) } }

    override suspend fun suggestCategories(title: String, locale: String): AiResult<List<CategorySuggestion>> =
        route { preferOnline(it) { service -> service.suggestCategories(title, locale) } }

    override suspend fun suggestItems(context: ItemSuggestionContext): AiResult<List<ItemSuggestion>> =
        route { preferOnline(it) { service -> service.suggestItems(context) } }

    override suspend fun summarize(context: ChecklistContext): AiResult<AiSummary> =
        route { preferOnline(it) { service -> service.summarize(context) } }

    private suspend fun <T> route(block: suspend (AiSettings) -> AiResult<T>): AiResult<T> {
        val current = settings.current()
        if (!current.enabled) return AiResult.Unavailable(UnavailableReason.DISABLED)
        return block(current)
    }

    private suspend fun <T> preferOnline(current: AiSettings, call: suspend (AIService) -> AiResult<T>): AiResult<T> =
        if (current.onlineAllowed()) onlineCall(call) else guarded { call(offline) }

    private fun AiSettings.onlineAllowed() = online != null && onlineEnabled

    private suspend fun <T> onlineCall(call: suspend (AIService) -> AiResult<T>): AiResult<T> {
        val service = online ?: return AiResult.Unavailable(UnavailableReason.NOT_SUPPORTED)
        return withTimeoutOrNull(onlineTimeoutMillis) { guarded { call(service) } }
            ?: AiResult.Unavailable(UnavailableReason.TIMEOUT)
    }

    private suspend fun <T> guarded(call: suspend () -> AiResult<T>): AiResult<T> =
        try {
            call()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            AiResult.Error(failure)
        }

    companion object {
        const val DEFAULT_ONLINE_TIMEOUT_MILLIS = 15_000L
    }
}
