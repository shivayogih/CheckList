package com.dataloom.checklist.testing

import com.dataloom.checklist.domain.model.BuiltInUnits
import com.dataloom.checklist.domain.model.Category
import com.dataloom.checklist.domain.model.CategoryId
import com.dataloom.checklist.domain.model.MasterItem
import com.dataloom.checklist.domain.model.MasterItemId
import com.dataloom.checklist.domain.model.UnitCode
import com.dataloom.checklist.domain.model.UnitDef
import com.dataloom.checklist.domain.repository.CatalogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory catalog for ViewModel tests (a copy of the :domain test fake; test sources are not shared between modules). Single-language: the locale argument is ignored and every
 * name is "resolved" to the stored display name. IDs are sequential so assertions stay readable.
 */
class FakeCatalogRepository : CatalogRepository {

    private val categories = MutableStateFlow<List<Category>>(emptyList())
    private val masterItems = MutableStateFlow<List<MasterItem>>(emptyList())
    private val units = MutableStateFlow(BuiltInUnits.all)
    private var nextId = 1

    /** Wired to the checklist fake so usage counts reflect real sections. */
    var checklistUsage: (CategoryId) -> Int = { 0 }

    val renameCalls = mutableListOf<Pair<CategoryId, String?>>()
    val searchQueries = mutableListOf<String>()

    fun allCategories(): List<Category> = categories.value

    fun allMasterItems(): List<MasterItem> = masterItems.value

    fun categoryById(id: CategoryId): Category? = categories.value.firstOrNull { it.id == id }

    fun seedCategory(key: String, name: String): Category {
        val category = Category(
            id = CategoryId("cat-${nextId++}"),
            canonicalKey = key,
            customName = null,
            displayName = name,
            iconKey = key,
            isCustom = false,
            isHidden = false,
        )
        categories.update { it + category }
        return category
    }

    fun seedMasterItem(categoryId: CategoryId, key: String, name: String, defaultUnit: UnitCode? = null, useCount: Int = 0): MasterItem {
        val item = MasterItem(
            id = MasterItemId("mi-${nextId++}"),
            categoryId = categoryId,
            canonicalKey = key,
            customName = null,
            displayName = name,
            defaultUnit = defaultUnit,
            isCustom = false,
            isHidden = false,
            useCount = useCount,
        )
        masterItems.update { it + item }
        return item
    }

    override fun observeCategories(locale: String, includeHidden: Boolean): Flow<List<Category>> =
        categories.map { list -> list.filter { includeHidden || !it.isHidden } }

    override suspend fun getCategory(id: CategoryId, locale: String): Category? = categoryById(id)

    override suspend fun createCategory(name: String, iconKey: String): CategoryId {
        val id = CategoryId("cat-${nextId++}")
        categories.update {
            it + Category(id, canonicalKey = null, customName = name, displayName = name, iconKey = iconKey, isCustom = true, isHidden = false)
        }
        return id
    }

    override suspend fun renameCategory(id: CategoryId, name: String?) {
        renameCalls += id to name
        categories.update { list ->
            list.map { c ->
                if (c.id != id) {
                    c
                } else {
                    c.copy(customName = name, displayName = name ?: c.canonicalKey!!.replaceFirstChar { it.uppercase() })
                }
            }
        }
    }

    override suspend fun setCategoryHidden(id: CategoryId, hidden: Boolean) {
        categories.update { list -> list.map { if (it.id == id) it.copy(isHidden = hidden) else it } }
    }

    override suspend fun countChecklistsUsing(id: CategoryId): Int = checklistUsage(id)

    override suspend fun deleteCategory(id: CategoryId) {
        categories.update { list -> list.filterNot { it.id == id } }
        masterItems.update { list -> list.filterNot { it.categoryId == id && it.isCustom } }
    }

    override suspend fun categoryNameExists(name: String, locale: String): Boolean =
        categories.value.any { !it.isHidden && it.displayName.equals(name, ignoreCase = true) }

    override fun observeMasterItems(categoryId: CategoryId, locale: String): Flow<List<MasterItem>> =
        masterItems.map { list ->
            list.filter { it.categoryId == categoryId && !it.isHidden }.sortedByDescending { it.useCount }
        }

    override suspend fun searchMasterItems(query: String, locale: String, categoryId: CategoryId?, limit: Int): List<MasterItem> {
        searchQueries += query
        return masterItems.value
            .filter { !it.isHidden && (categoryId == null || it.categoryId == categoryId) }
            .filter { it.displayName.contains(query, ignoreCase = true) }
            .take(limit)
    }

    override suspend fun getMasterItem(id: MasterItemId, locale: String): MasterItem? =
        masterItems.value.firstOrNull { it.id == id }

    override suspend fun findMasterItemByName(categoryId: CategoryId, name: String, locale: String): MasterItem? =
        masterItems.value.firstOrNull { it.categoryId == categoryId && it.displayName.equals(name.trim(), ignoreCase = true) }

    override suspend fun createMasterItem(categoryId: CategoryId, name: String, nameLocale: String, defaultUnit: UnitCode?): MasterItemId {
        val id = MasterItemId("mi-${nextId++}")
        masterItems.update {
            it + MasterItem(
                id = id,
                categoryId = categoryId,
                canonicalKey = "custom:${id.value}",
                customName = name,
                displayName = name,
                defaultUnit = defaultUnit,
                isCustom = true,
                isHidden = false,
                useCount = 0,
            )
        }
        return id
    }

    override suspend fun setMasterItemHidden(id: MasterItemId, hidden: Boolean) {
        masterItems.update { list -> list.map { if (it.id == id) it.copy(isHidden = hidden) else it } }
    }

    override fun observeUnits(): Flow<List<UnitDef>> = units

    override suspend fun getUnit(code: UnitCode): UnitDef? = units.value.firstOrNull { it.code == code }

    override suspend fun createCustomUnit(label: String, allowsDecimal: Boolean): UnitCode {
        val code = UnitCode("${UnitCode.CUSTOM_PREFIX}${nextId++}")
        units.update { it + UnitDef(code, allowsDecimal, customLabel = label, sortOrder = 1000) }
        return code
    }
}
