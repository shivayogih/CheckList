package com.dataloom.checklist.ai.service

import com.dataloom.checklist.ai.model.ActionPlan
import com.dataloom.checklist.ai.model.AiResult
import com.dataloom.checklist.ai.model.AiSummary
import com.dataloom.checklist.ai.model.CategorySuggestion
import com.dataloom.checklist.ai.model.ChecklistContext
import com.dataloom.checklist.ai.model.GenerateRequest
import com.dataloom.checklist.ai.model.ItemSuggestion
import com.dataloom.checklist.ai.model.ItemSuggestionContext
import com.dataloom.checklist.ai.model.PlanSource
import com.dataloom.checklist.ai.model.RejectionReason
import com.dataloom.checklist.ai.model.ToolCall
import com.dataloom.checklist.ai.model.Utterance
import com.dataloom.checklist.ai.model.summary
import com.dataloom.checklist.ai.tools.AiTool
import com.dataloom.checklist.domain.model.BuiltInUnits

/**
 * Deterministic [AIService] for tests and demos (section 11.5): no network, no randomness, canned plans
 * built from seeded catalog keys, so the review screen and executor can be built and tested with no
 * real AI. [scripted] answers specific utterances (matched case-insensitively after trimming); every
 * request is recorded in [requests] so tests can assert what was asked. Canned plans carry only keys,
 * numbers and the user's own words; display names come from the catalog during validation.
 */
class MockAIService(
    private val scripted: Map<String, AiResult<ActionPlan>> = emptyMap(),
) : AIService {

    private val recorded = mutableListOf<String>()

    /** "interpret:<text>", "generate:<prompt>", "suggestCategories:<title>", ... in call order. */
    val requests: List<String> get() = synchronized(recorded) { recorded.toList() }

    private fun record(entry: String) = synchronized(recorded) { recorded += entry }

    override suspend fun interpret(utterance: Utterance, context: ChecklistContext): AiResult<ActionPlan> {
        record("interpret:${utterance.text}")
        scripted[utterance.text.trim().lowercase()]?.let { return it }
        if (!context.hasChecklist) return AiResult.Rejected(RejectionReason.NEEDS_CHECKLIST)
        return AiResult.Success(ActionPlan(listOf(item("rice", "2", BuiltInUnits.KG.code.value), item("milk", "1", BuiltInUnits.LITRE.code.value)), PlanSource.MOCK))
    }

    override suspend fun generateChecklist(request: GenerateRequest): AiResult<ActionPlan> {
        record("generate:${request.prompt}")
        val title = request.prompt.trim().take(TITLE_MAX)
        if (title.isEmpty()) return AiResult.Rejected(RejectionReason.EMPTY_INPUT)
        val calls = listOf(
            ToolCall(AiTool.CREATE_CHECKLIST.toolName, mapOf("title" to title, "categories" to listOf("groceries"))),
            item("rice", "5", BuiltInUnits.KG.code.value),
            item("dal", "1", BuiltInUnits.KG.code.value),
            item("milk", "2", BuiltInUnits.LITRE.code.value),
        )
        return AiResult.Success(ActionPlan(calls, PlanSource.MOCK))
    }

    override suspend fun suggestCategories(title: String, locale: String): AiResult<List<CategorySuggestion>> {
        record("suggestCategories:$title")
        return AiResult.Success(listOf(CategorySuggestion("groceries", "groceries"), CategorySuggestion("vegetables", "vegetables")))
    }

    override suspend fun suggestItems(context: ItemSuggestionContext): AiResult<List<ItemSuggestion>> {
        record("suggestItems:${context.categoryKey}")
        return AiResult.Success(listOf(ItemSuggestion("rice", "rice", BuiltInUnits.KG.code), ItemSuggestion("dal", "dal", BuiltInUnits.KG.code)))
    }

    override suspend fun summarize(context: ChecklistContext): AiResult<AiSummary> {
        record("summarize")
        return AiResult.Success(context.summary())
    }

    private fun item(key: String, quantity: String, unit: String) = ToolCall(
        AiTool.ADD_CHECKLIST_ITEM.toolName,
        mapOf("name" to key, "canonicalKey" to key, "quantity" to quantity, "unit" to unit),
    )

    private companion object {
        const val TITLE_MAX = 100
    }
}
