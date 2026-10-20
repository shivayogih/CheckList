package com.dataloom.checklist.ai.draft

import com.dataloom.checklist.ai.mapper.CatalogLookup
import com.dataloom.checklist.ai.model.AiResult
import com.dataloom.checklist.ai.model.RejectionReason
import com.dataloom.checklist.ai.model.Utterance
import com.dataloom.checklist.ai.parser.LanguagePacks
import com.dataloom.checklist.ai.parser.TextNormalizer
import com.dataloom.checklist.domain.model.Category
import com.dataloom.checklist.domain.model.MasterItem
import com.dataloom.checklist.domain.usecase.ObserveMasterItemsUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Turns a request typed or spoken on Home ("Create checklist for my next Goa trip") into a
 * [ChecklistDraft], fully offline. Only reads: the catalog is read through domain use cases and
 * nothing is written; saving is [ChecklistDraftSaver]'s job, after the user approves.
 *
 * 1. [DraftMatcher] picks scenario templates; their catalog keys become the user's own catalog rows
 *    (names in the app language, hidden items left out) and their typed items get the app-language name.
 * 2. Without a template, a category named in the request ("medicines", "ಸ್ಟೇಷನರಿ") gives its most used items.
 * 3. Otherwise the request is honestly not understood, and the UI offers the manual way.
 */
class ChecklistDraftBuilder @Inject constructor(
    private val matcher: DraftMatcher,
    private val catalog: CatalogLookup,
    private val observeMasterItems: ObserveMasterItemsUseCase,
    private val packs: LanguagePacks,
) {

    suspend fun build(prompt: String, locale: String): AiResult<ChecklistDraft> {
        val text = prompt.trim()
        if (text.isEmpty()) return AiResult.Rejected(RejectionReason.EMPTY_INPUT)
        if (text.length > Utterance.MAX_LENGTH) return AiResult.Rejected(RejectionReason.TOO_LONG)
        val categories = catalog.categories(locale).filterNot { it.isHidden }
        val match = matcher.match(text, locale)
        val draft = if (match != null) {
            fromTemplates(match, categories, locale)
        } else {
            fromCategoryNames(text, categories, locale)
        }
        return if (draft == null || draft.itemCount == 0) {
            AiResult.Rejected(RejectionReason.NOT_UNDERSTOOD)
        } else {
            AiResult.Success(draft)
        }
    }

    private suspend fun fromTemplates(
        match: DraftMatcher.Match,
        categories: List<Category>,
        locale: String,
    ): ChecklistDraft {
        val byKey = categories.associateBy { it.canonicalKey }
        val sections = LinkedHashMap<String, MutableList<DraftItem>>()
        val sectionCategories = LinkedHashMap<String, Category>()
        for (template in match.templates) {
            for (section in template.sections) {
                val category = byKey[section.category] ?: continue
                val masters = observeMasterItems(category.id, locale).first().associateBy { it.canonicalKey }
                val items = sections.getOrPut(section.category) { mutableListOf() }
                sectionCategories[section.category] = category
                section.items.mapNotNull { masters[it] }.forEach { items.addUnique(DraftItem(it.displayName, it)) }
                section.custom.forEach { names -> items.addUnique(DraftItem(DraftMatcher.localized(names, locale))) }
            }
        }
        return ChecklistDraft(
            title = match.title,
            sections = sections.mapNotNull { (key, items) ->
                items.takeIf { it.isNotEmpty() }?.let { DraftSection(sectionCategories.getValue(key), it) }
            },
        )
    }

    /** "Medicines", "stationery list": the named categories with their most used items. */
    private suspend fun fromCategoryNames(text: String, categories: List<Category>, locale: String): ChecklistDraft? {
        val words = TextNormalizer.words(text).filterNot { it in ignoredWords }
        if (words.isEmpty()) return null
        val english = catalog.categories(CatalogLookup.ENGLISH).associate { it.id to it.displayName }
        val named = categories.filter { category ->
            category.canonicalKey != CatalogLookup.OTHER_CATEGORY_KEY &&
                listOfNotNull(category.displayName, english[category.id])
                    .flatMap { TextNormalizer.words(it) }
                    .filter { it.codePointCount(0, it.length) >= MIN_CATEGORY_WORD && it !in ignoredWords }
                    .any { name -> words.any { it == name || it.startsWith(name) } }
        }.take(MAX_CATEGORIES)
        if (named.isEmpty()) return null
        val sections = named.map { category ->
            val items: List<MasterItem> = observeMasterItems(category.id, locale).first().take(ITEMS_PER_CATEGORY)
            DraftSection(category, items.map { DraftItem(it.displayName, it) })
        }
        return ChecklistDraft(matcher.title(text) ?: named.first().displayName, sections)
    }

    /** "and", "of", "please"... in every language, and generic words of category names ("supplies"). */
    private val ignoredWords: Set<String> by lazy {
        (packs.packs.values.flatMap { it.separators + it.fillers } + GENERIC_CATEGORY_WORDS)
            .flatMap { TextNormalizer.words(it) }
            .toSet()
    }

    private fun MutableList<DraftItem>.addUnique(item: DraftItem) {
        if (none { it.key == item.key }) add(item)
    }

    companion object {
        const val MAX_CATEGORIES = 3
        const val ITEMS_PER_CATEGORY = 12

        /** Shorter category words ("pet", "aid") match too much by prefix. */
        const val MIN_CATEGORY_WORD = 4
        private val GENERIC_CATEGORY_WORDS = listOf(
            "daily", "needs", "supplies", "first", "items", "other", "custom", "tasks", "ready", "cook",
            "accessories", "recurring", "admin", "care", "home",
        )
    }
}
