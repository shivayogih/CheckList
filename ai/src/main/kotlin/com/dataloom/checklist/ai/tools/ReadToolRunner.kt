package com.dataloom.checklist.ai.tools

import com.dataloom.checklist.ai.mapper.CatalogLookup
import com.dataloom.checklist.ai.model.AiResult
import com.dataloom.checklist.ai.model.AiSummary
import com.dataloom.checklist.ai.model.CategorySuggestion
import com.dataloom.checklist.ai.model.ChecklistContext
import com.dataloom.checklist.ai.model.ItemSuggestion
import com.dataloom.checklist.ai.model.RejectionReason
import com.dataloom.checklist.ai.model.ToolCall
import com.dataloom.checklist.ai.model.summary
import com.dataloom.checklist.domain.model.MasterItem
import com.dataloom.checklist.domain.usecase.ObserveMasterItemsUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.first

sealed interface ReadToolResult {
    data class Items(val items: List<ItemSuggestion>) : ReadToolResult

    data class Categories(val categories: List<CategorySuggestion>) : ReadToolResult

    data class Summary(val summary: AiSummary) : ReadToolResult
}

/**
 * Answers [RiskClass.READ] tools inside an online model's function-calling loop: the model asks, the
 * device answers from the local catalog, nothing is written and no confirmation is needed (section 13).
 * Answers contain keys and names only, never IDs.
 */
class ReadToolRunner @Inject constructor(
    private val catalog: CatalogLookup,
    private val observeMasterItems: ObserveMasterItemsUseCase,
) {

    suspend fun run(call: ToolCall, context: ChecklistContext): AiResult<ReadToolResult> {
        val tool = AiTool.byName(call.name)?.takeIf { it.risk == RiskClass.READ }
            ?: return AiResult.Rejected(RejectionReason.NOT_UNDERSTOOD)
        val text = { name: String -> (call.arguments[name] as? String)?.trim()?.takeIf { it.isNotEmpty() } }
        val locale = context.locale
        return when (tool) {
            AiTool.SEARCH_MASTER_ITEMS -> {
                val query = text("query") ?: return AiResult.Rejected(RejectionReason.EMPTY_INPUT)
                val category = text("categoryKey")?.let { key -> catalog.categories(locale).firstOrNull { it.canonicalKey == key } }
                AiResult.Success(ReadToolResult.Items(catalog.search(query, locale, category?.id).map { it.toSuggestion() }))
            }
            AiTool.SUGGEST_CATEGORIES -> AiResult.Success(
                ReadToolResult.Categories(
                    catalog.categories(locale).filterNot { it.isHidden }.map { CategorySuggestion(it.canonicalKey, it.displayName) },
                ),
            )
            AiTool.SUGGEST_ITEMS -> {
                val key = text("categoryKey") ?: return AiResult.Rejected(RejectionReason.EMPTY_INPUT)
                val category = catalog.categories(locale).firstOrNull { it.canonicalKey == key }
                    ?: return AiResult.Rejected(RejectionReason.NOT_UNDERSTOOD)
                val items = observeMasterItems(category.id, locale).first().take(CatalogLookup.SEARCH_LIMIT)
                AiResult.Success(ReadToolResult.Items(items.map { it.toSuggestion() }))
            }
            AiTool.SUMMARIZE_CHECKLIST -> AiResult.Success(ReadToolResult.Summary(context.summary()))
            else -> AiResult.Rejected(RejectionReason.NOT_UNDERSTOOD)
        }
    }

    /** User keys ("custom:<uuid>") hold a database ID, so custom items are sent by name only. */
    private fun MasterItem.toSuggestion() = ItemSuggestion(canonicalKey.takeUnless { isCustom }, displayName, defaultUnit)
}
