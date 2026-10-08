package com.dataloom.checklist.ai.service

import com.dataloom.checklist.ai.model.ActionPlan
import com.dataloom.checklist.ai.model.AiResult
import com.dataloom.checklist.ai.model.AiSummary
import com.dataloom.checklist.ai.model.CategorySuggestion
import com.dataloom.checklist.ai.model.ChecklistContext
import com.dataloom.checklist.ai.model.GenerateRequest
import com.dataloom.checklist.ai.model.ItemSuggestion
import com.dataloom.checklist.ai.model.ItemSuggestionContext
import com.dataloom.checklist.ai.model.Utterance

/**
 * The provider-independent AI contract (architecture section 11.3). Business code depends on this
 * interface, never on a vendor SDK. Implementations only *propose*: they return [ActionPlan]s and
 * suggestions and have no way to write data. Writing happens in
 * [com.dataloom.checklist.ai.executor.ActionPlanExecutor], through domain use cases, after the user
 * confirms.
 *
 * Implementations: [MockAIService] (tests, demos), [com.dataloom.checklist.ai.parser.OfflineCommandParser]
 * (rule-based, offline, 7 languages) and, later, an online service behind the same interface (see
 * docs/ai-automation.md, "Plugging in online AI"). Implementations receive only the minimized
 * [ChecklistContext], never profile data, notes or database IDs.
 */
interface AIService {

    /** A whole new checklist from a description ("Goa trip for 4 days"). */
    suspend fun generateChecklist(request: GenerateRequest): AiResult<ActionPlan>

    suspend fun suggestCategories(title: String, locale: String): AiResult<List<CategorySuggestion>>

    suspend fun suggestItems(context: ItemSuggestionContext): AiResult<List<ItemSuggestion>>

    /** A command about the open checklist ("2 kg rice and milk", "mark milk done"). */
    suspend fun interpret(utterance: Utterance, context: ChecklistContext): AiResult<ActionPlan>

    suspend fun summarize(context: ChecklistContext): AiResult<AiSummary>
}
