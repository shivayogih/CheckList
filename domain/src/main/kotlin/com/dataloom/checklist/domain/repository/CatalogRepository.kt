package com.dataloom.checklist.domain.repository

import com.dataloom.checklist.domain.model.Category
import com.dataloom.checklist.domain.model.CategoryId
import com.dataloom.checklist.domain.model.MasterItem
import com.dataloom.checklist.domain.model.MasterItemId
import com.dataloom.checklist.domain.model.UnitCode
import com.dataloom.checklist.domain.model.UnitDef
import kotlinx.coroutines.flow.Flow

/** Reusable categories, master items and units. [locale] resolves display names. */
interface CatalogRepository {

    /** Visible categories, most used first, then by display name. */
    fun observeCategories(locale: String, includeHidden: Boolean = false): Flow<List<Category>>

    suspend fun getCategory(id: CategoryId, locale: String): Category?

    suspend fun createCategory(name: String, iconKey: String): CategoryId

    /** Sets a custom name; null restores the translated name of a seeded category. */
    suspend fun renameCategory(id: CategoryId, name: String?)

    suspend fun setCategoryHidden(id: CategoryId, hidden: Boolean)

    /** Number of checklists using the category. */
    suspend fun countChecklistsUsing(id: CategoryId): Int

    /** Deletes an unused category and its custom master items. Callers check usage first. */
    suspend fun deleteCategory(id: CategoryId)

    /** True when a visible category already resolves to this name in [locale] (case-insensitive). */
    suspend fun categoryNameExists(name: String, locale: String): Boolean

    /** Visible master items of a category, most used first. */
    fun observeMasterItems(categoryId: CategoryId, locale: String): Flow<List<MasterItem>>

    /**
     * Offline search over names in [locale], English, aliases and custom names (FTS, section 6.6).
     * Null [categoryId] searches all categories.
     */
    suspend fun searchMasterItems(query: String, locale: String, categoryId: CategoryId? = null, limit: Int = 50): List<MasterItem>

    /** Any master item, hidden or not; null when it does not exist. */
    suspend fun getMasterItem(id: MasterItemId, locale: String): MasterItem?

    suspend fun findMasterItemByName(categoryId: CategoryId, name: String, locale: String): MasterItem?

    suspend fun createMasterItem(categoryId: CategoryId, name: String, nameLocale: String, defaultUnit: UnitCode?): MasterItemId

    suspend fun setMasterItemHidden(id: MasterItemId, hidden: Boolean)

    fun observeUnits(): Flow<List<UnitDef>>

    suspend fun getUnit(code: UnitCode): UnitDef?

    suspend fun createCustomUnit(label: String, allowsDecimal: Boolean): UnitCode
}
