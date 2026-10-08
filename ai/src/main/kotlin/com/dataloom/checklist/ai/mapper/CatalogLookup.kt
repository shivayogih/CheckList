package com.dataloom.checklist.ai.mapper

import com.dataloom.checklist.domain.model.Category
import com.dataloom.checklist.domain.model.CategoryId
import com.dataloom.checklist.domain.model.MasterItem
import com.dataloom.checklist.domain.model.UnitDef
import com.dataloom.checklist.domain.usecase.ObserveCategoriesUseCase
import com.dataloom.checklist.domain.usecase.ObserveUnitsUseCase
import com.dataloom.checklist.domain.usecase.SearchMasterItemsUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Read-only catalog access for the AI layer, built only on domain use cases (the AI never reads
 * storage directly).
 */
class CatalogLookup @Inject constructor(
    private val searchMasterItems: SearchMasterItemsUseCase,
    private val observeCategories: ObserveCategoriesUseCase,
    private val observeUnits: ObserveUnitsUseCase,
) {

    suspend fun search(query: String, locale: String, categoryId: CategoryId? = null, limit: Int = SEARCH_LIMIT): List<MasterItem> =
        searchMasterItems(query, locale, categoryId, limit)

    /**
     * Finds a master item by canonical key. The domain has no key lookup, so the key is searched like
     * a name: the model's [name], the humanized key ("cooking_oil" -> "cooking oil") and its last word,
     * keeping only an exact key match. Null when the catalog has no such key.
     */
    suspend fun masterItemByKey(key: String, name: String?, locale: String): MasterItem? {
        val queries = listOfNotNull(
            name,
            key.replace('_', ' '),
            key.substringAfterLast('_').takeIf { '_' in key },
        ).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        for (lookupLocale in listOf(locale, ENGLISH).distinct()) {
            for (query in queries) {
                search(query, lookupLocale).firstOrNull { it.canonicalKey == key }?.let { return it }
            }
        }
        return null
    }

    /** All categories including hidden ones: a hidden category's items can still be added explicitly. */
    suspend fun categories(locale: String): List<Category> = observeCategories(locale, includeHidden = true).first()

    suspend fun units(): List<UnitDef> = observeUnits().first()

    companion object {
        const val SEARCH_LIMIT = 20
        const val ENGLISH = "en"

        /** Seeded fallback category for typed items that fit nowhere else. */
        const val OTHER_CATEGORY_KEY = "other"
    }
}
