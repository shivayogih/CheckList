package com.dataloom.checklist.ai.parser

import com.dataloom.checklist.ai.mapper.CatalogLookup
import com.dataloom.checklist.ai.model.ActionPlan
import com.dataloom.checklist.ai.model.AiResult
import com.dataloom.checklist.ai.model.AiSummary
import com.dataloom.checklist.ai.model.CategorySuggestion
import com.dataloom.checklist.ai.model.ChecklistContext
import com.dataloom.checklist.ai.model.GenerateRequest
import com.dataloom.checklist.ai.model.ItemContext
import com.dataloom.checklist.ai.model.ItemSuggestion
import com.dataloom.checklist.ai.model.ItemSuggestionContext
import com.dataloom.checklist.ai.model.PlanSource
import com.dataloom.checklist.ai.model.RejectionReason
import com.dataloom.checklist.ai.model.ToolCall
import com.dataloom.checklist.ai.model.UnavailableReason
import com.dataloom.checklist.ai.model.UnresolvedFragment
import com.dataloom.checklist.ai.model.UnresolvedReason
import com.dataloom.checklist.ai.model.Utterance
import com.dataloom.checklist.ai.model.summary
import com.dataloom.checklist.ai.service.AIService
import com.dataloom.checklist.ai.tools.AiTool
import com.dataloom.checklist.domain.model.MasterItem
import javax.inject.Inject

/**
 * Fully offline, rule-based [AIService] for quick commands in English, Kannada, Hindi, Tamil, Telugu,
 * Marathi and Malayalam (section 11.5). Not an LLM: [CommandGrammar] reads the intent, items,
 * quantities and units from the bundled language packs, and item names are looked up with the domain
 * catalog search in the user's language, the script's languages and English. "5 ಕೆಜಿ ಅಕ್ಕಿ",
 * "5 किलो चावल" and "5 kg rice" all become `addChecklistItem(rice, 5, KG)`.
 *
 * Like every [AIService] it only proposes: the plan is validated, confirmed and executed elsewhere.
 * Items not in the catalog are proposed as typed items; items to tick or remove must already be in
 * the open checklist.
 */
class OfflineCommandParser @Inject constructor(
    private val grammar: CommandGrammar,
    private val catalog: CatalogLookup,
) : AIService {

    override suspend fun interpret(utterance: Utterance, context: ChecklistContext): AiResult<ActionPlan> {
        val text = utterance.text.trim()
        if (text.isEmpty()) return AiResult.Rejected(RejectionReason.EMPTY_INPUT)
        if (text.length > Utterance.MAX_LENGTH) return AiResult.Rejected(RejectionReason.TOO_LONG)
        val command = grammar.parse(text, context.locale)
        if (command.intent != CommandIntent.CREATE_CHECKLIST && !context.hasChecklist) {
            return AiResult.Rejected(RejectionReason.NEEDS_CHECKLIST)
        }
        val calls = mutableListOf<ToolCall>()
        val unresolved = command.unparsed.mapTo(mutableListOf()) { UnresolvedFragment(it, UnresolvedReason.NOT_UNDERSTOOD) }
        when (command.intent) {
            CommandIntent.CREATE_CHECKLIST -> {
                val title = command.title
                if (title == null) unresolved += UnresolvedFragment(text, UnresolvedReason.MISSING_TITLE)
                else calls += ToolCall(AiTool.CREATE_CHECKLIST.toolName, mapOf("title" to title))
            }
            CommandIntent.ADD -> command.items.forEach { calls += addCall(it, command) }
            CommandIntent.COMPLETE, CommandIntent.UNCOMPLETE, CommandIntent.REMOVE, CommandIntent.UPDATE ->
                command.items.forEach { item ->
                    when (val call = existingItemCall(item, command, context)) {
                        null -> unresolved += UnresolvedFragment(item.text, UnresolvedReason.ITEM_NOT_IN_CHECKLIST)
                        else -> call.fold({ calls += it }, { unresolved += it })
                    }
                }
        }
        if (calls.isEmpty()) return AiResult.Rejected(RejectionReason.NOT_UNDERSTOOD)
        return AiResult.Success(ActionPlan(calls, PlanSource.OFFLINE_PARSER, unresolved))
    }

    private suspend fun addCall(item: ParsedItem, command: ParsedCommand): ToolCall {
        val master = findInCatalog(item.name, command)
        return ToolCall(
            AiTool.ADD_CHECKLIST_ITEM.toolName,
            buildMap {
                put("name", master?.displayName ?: item.name)
                master?.let { put("canonicalKey", it.canonicalKey) }
                item.quantity?.let { put("quantity", it.toPlainString()) }
                item.unit?.let { put("unit", it.value) }
            },
        )
    }

    /** A call, or why this item cannot be acted on; null when the item is not in the checklist. */
    private suspend fun existingItemCall(item: ParsedItem, command: ParsedCommand, context: ChecklistContext): Either? {
        val match = findInChecklist(item.name, command, context)
        if (match == null) {
            if (command.intent != CommandIntent.REMOVE) return null
            val section = findSection(item.name, command, context) ?: return null
            return Either.Call(ToolCall(AiTool.REMOVE_CATEGORY.toolName, mapOf("sectionRef" to section)))
        }
        val call = when (command.intent) {
            CommandIntent.COMPLETE -> ToolCall(AiTool.COMPLETE_ITEM.toolName, mapOf("itemRef" to match.ref))
            CommandIntent.UNCOMPLETE -> ToolCall(AiTool.UNCOMPLETE_ITEM.toolName, mapOf("itemRef" to match.ref))
            CommandIntent.REMOVE -> ToolCall(AiTool.DELETE_ITEM.toolName, mapOf("itemRef" to match.ref))
            CommandIntent.UPDATE -> {
                val quantity = item.quantity ?: return Either.Skip(UnresolvedFragment(item.text, UnresolvedReason.MISSING_QUANTITY))
                ToolCall(
                    AiTool.UPDATE_ITEM_QUANTITY.toolName,
                    buildMap {
                        put("itemRef", match.ref)
                        put("quantity", quantity.toPlainString())
                        item.unit?.let { put("unit", it.value) }
                    },
                )
            }
            CommandIntent.ADD, CommandIntent.CREATE_CHECKLIST -> return null
        }
        return Either.Call(call)
    }

    private sealed interface Either {
        data class Call(val call: ToolCall) : Either

        data class Skip(val fragment: UnresolvedFragment) : Either

        fun fold(onCall: (ToolCall) -> Unit, onSkip: (UnresolvedFragment) -> Unit) = when (this) {
            is Call -> onCall(call)
            is Skip -> onSkip(fragment)
        }
    }

    /**
     * Searches the name, then its variants, in each command language; the first acceptable hit wins.
     * The catalog search is a prefix search built for a picker, so a command accepts a hit only when it
     * is clearly the same word (see [accepts]): "egg" must not become "eggplant", "pa" not "Pan card".
     */
    private suspend fun findInCatalog(name: String, command: ParsedCommand): MasterItem? {
        command.nameVariants(name).forEachIndexed { index, candidate ->
            val query = TextNormalizer.normalize(candidate)
            for (language in command.languages) {
                // Several items can share a stem (a bigger catalogue has "பால்" and "பாலக் கீரை"): take the
                // first hit that is clearly the same word, not only the first hit.
                catalog.search(candidate, language).firstOrNull { accepts(it, query, isVariant = index > 0, language) }
                    ?.let { return it }
            }
        }
        return null
    }

    /**
     * A hit is the same word when its display name in the searched language equals the query, has it
     * as a whole word ("oil" in "Cooking Oil"), or differs only by a short ending ("केल" -> "केला").
     * Otherwise the search matched an alias the domain does not expose, typically a transliteration
     * ("akki" for ಅಕ್ಕಿ). That is trusted only for the word as typed (not a shortened variant), at least
     * [MIN_ALIAS_LENGTH] letters, and for 3-letter words only when no English name or alias starts with
     * it, because English rows are searched in every language and hold the "egg" -> "eggplant" traps.
     */
    private suspend fun accepts(hit: MasterItem, query: String, isVariant: Boolean, language: String): Boolean {
        val length = query.codePointCount(0, query.length)
        val name = TextNormalizer.normalize(hit.displayName)
        if (name == query || query in name.split(NON_WORD)) return true
        if (name.startsWith(query) && name.codePointCount(0, name.length) - length <= MAX_ENDING && length >= MIN_STEM) return true
        if (isVariant || length < MIN_ALIAS_LENGTH) return false
        if (length > MIN_ALIAS_LENGTH) return true
        return language != ENGLISH && catalog.search(query, ENGLISH).none { it.id == hit.id }
    }

    /**
     * An item of the open checklist by typed name (or a variant), by catalog key ("akki" finds a
     * "Rice" row), or by a unique name that starts with the query. For tick/untick, rows that still
     * need the change are preferred.
     */
    private suspend fun findInChecklist(name: String, command: ParsedCommand, context: ChecklistContext): ItemContext? {
        val items = context.items
        val prefer = { candidates: List<ItemContext> ->
            when (command.intent) {
                CommandIntent.COMPLETE -> candidates.firstOrNull { !it.isCompleted } ?: candidates.firstOrNull()
                CommandIntent.UNCOMPLETE -> candidates.firstOrNull { it.isCompleted } ?: candidates.firstOrNull()
                else -> candidates.firstOrNull()
            }
        }
        val variants = command.nameVariants(name).map(TextNormalizer::normalize)
        prefer(items.filter { TextNormalizer.normalize(it.name) in variants })?.let { return it }
        findInCatalog(name, command)?.let { master -> prefer(items.filter { it.canonicalKey == master.canonicalKey })?.let { return it } }
        val query = variants.first()
        return items.filter { TextNormalizer.normalize(it.name).startsWith(query) }.singleOrNull()
    }

    /** A section by category name or key ("remove vegetables"); returns its ref. */
    private fun findSection(name: String, command: ParsedCommand, context: ChecklistContext): String? {
        val variants = command.nameVariants(name).map(TextNormalizer::normalize)
        return context.sections.firstOrNull { section ->
            TextNormalizer.normalize(section.categoryName) in variants || section.categoryKey in variants
        }?.ref
    }

    override suspend fun generateChecklist(request: GenerateRequest): AiResult<ActionPlan> =
        AiResult.Unavailable(UnavailableReason.NOT_SUPPORTED)

    override suspend fun suggestCategories(title: String, locale: String): AiResult<List<CategorySuggestion>> =
        AiResult.Unavailable(UnavailableReason.NOT_SUPPORTED)

    override suspend fun suggestItems(context: ItemSuggestionContext): AiResult<List<ItemSuggestion>> =
        AiResult.Unavailable(UnavailableReason.NOT_SUPPORTED)

    /** Counting needs no AI, so the offline service can always summarize. */
    override suspend fun summarize(context: ChecklistContext): AiResult<AiSummary> = AiResult.Success(context.summary())

    private companion object {
        const val ENGLISH = "en"

        /** Shortest typed word trusted to match through an alias. */
        const val MIN_ALIAS_LENGTH = 3

        /** Inflection allowance: "केल" still finds "केला", "பால" finds "பால்". */
        const val MAX_ENDING = 2
        const val MIN_STEM = 2
        val NON_WORD = Regex("[^\\p{L}\\p{N}\\p{M}]+")
    }
}
