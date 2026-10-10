package com.dataloom.checklist.data.repository

import androidx.room.withTransaction
import com.dataloom.checklist.data.local.database.CheckListDatabase
import com.dataloom.checklist.data.local.entity.CategoryEntity
import com.dataloom.checklist.data.local.entity.MasterItemEntity
import com.dataloom.checklist.data.local.entity.UnitDefEntity
import com.dataloom.checklist.data.local.fts.SearchText
import com.dataloom.checklist.data.mapper.DisplayNames
import com.dataloom.checklist.data.mapper.TranslationIndex
import com.dataloom.checklist.data.mapper.toDomain
import com.dataloom.checklist.data.mapper.toIndex
import com.dataloom.checklist.domain.common.Clock
import com.dataloom.checklist.domain.common.DefaultDispatcher
import com.dataloom.checklist.domain.common.IdGenerator
import com.dataloom.checklist.domain.model.Category
import com.dataloom.checklist.domain.model.CategoryId
import com.dataloom.checklist.domain.model.MasterItem
import com.dataloom.checklist.domain.model.MasterItemId
import com.dataloom.checklist.domain.model.UnitCode
import com.dataloom.checklist.domain.model.UnitDef
import com.dataloom.checklist.domain.repository.CatalogRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Room implementation of [CatalogRepository]. Display names are resolved here per section 6.2, so
 * the domain and UI never see translation rows.
 */
class RoomCatalogRepository @Inject constructor(
    private val db: CheckListDatabase,
    private val clock: Clock,
    private val ids: IdGenerator,
    @param:DefaultDispatcher private val default: CoroutineDispatcher,
) : CatalogRepository {

    private val categoryDao = db.categoryDao()
    private val masterItemDao = db.masterItemDao()
    private val sectionDao = db.sectionDao()
    private val searchIndexDao = db.searchIndexDao()
    private val unitDao = db.unitDao()

    override fun observeCategories(locale: String, includeHidden: Boolean): Flow<List<Category>> =
        combine(
            categoryDao.observeWithUsage(includeHidden),
            categoryDao.observeTranslations(DisplayNames.lookupLocales(locale)),
        ) { rows, translations ->
            val index = translations.toIndex()
            val collator = DisplayNames.collator(locale)
            rows.map { row ->
                row.category.toDomain(index, locale).copy(usageCount = row.usageCount, lastUsedAt = row.lastUsedAt)
            }
                .sortedWith(
                    compareByDescending<Category> { it.usageCount }
                        .thenByDescending { it.lastUsedAt ?: 0L }
                        .thenComparator { a, b -> collator.compare(a.displayName, b.displayName) },
                )
        }.distinctUntilChanged().flowOn(default)

    override suspend fun getCategory(id: CategoryId, locale: String): Category? {
        val category = categoryDao.getById(id.value) ?: return null
        return category.toDomain(categoryTranslations(listOfNotNull(category.canonicalKey), locale), locale)
    }

    override suspend fun createCategory(name: String, iconKey: String): CategoryId {
        val now = clock.nowMillis()
        val id = ids.newId()
        categoryDao.insert(
            CategoryEntity(
                id = id,
                canonicalKey = null,
                customName = name,
                iconKey = iconKey,
                isCustom = true,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return CategoryId(id)
    }

    override suspend fun renameCategory(id: CategoryId, name: String?) {
        categoryDao.rename(id.value, name, clock.nowMillis())
    }

    override suspend fun setCategoryHidden(id: CategoryId, hidden: Boolean) {
        categoryDao.setHidden(id.value, hidden, clock.nowMillis())
    }

    override suspend fun countChecklistsUsing(id: CategoryId): Int = sectionDao.countChecklistsUsing(id.value)

    /** Fails with a constraint error if a checklist still uses the category (ON DELETE RESTRICT). */
    override suspend fun deleteCategory(id: CategoryId) {
        db.withTransaction {
            searchIndexDao.deleteForCategory(id.value)
            categoryDao.delete(id.value)
        }
    }

    override suspend fun categoryNameExists(name: String, locale: String): Boolean {
        val wanted = SearchText.normalize(name)
        val visible = categoryDao.getVisible()
        val index = categoryTranslations(visible.mapNotNull { it.canonicalKey }, locale)
        return visible.any { SearchText.normalize(it.toDomain(index, locale).displayName) == wanted }
    }

    override fun observeMasterItems(categoryId: CategoryId, locale: String): Flow<List<MasterItem>> =
        combine(
            masterItemDao.observeVisibleInCategory(categoryId.value),
            masterItemDao.observeTranslationsForCategory(categoryId.value, DisplayNames.lookupLocales(locale)),
        ) { rows, translations ->
            val index = translations.toIndex()
            sortByUse(rows.map { it.toDomain(index, locale) }, locale)
        }.distinctUntilChanged().flowOn(default)

    override suspend fun searchMasterItems(query: String, locale: String, categoryId: CategoryId?, limit: Int): List<MasterItem> {
        if (limit <= 0) return emptyList()
        val match = SearchText.matchExpression(query)
        if (match == null) {
            val items = masterItemDao.mostUsed(categoryId?.value, limit)
            return sortByUse(items.toDomainItems(locale), locale)
        }
        val normalized = SearchText.normalize(query)
        val hits = masterItemDao.search(match, DisplayNames.lookupLocales(locale), categoryId?.value)
        // An item matches once per language row; keep its best rank.
        val bestRank = hits.groupBy { it.item.id }.mapValues { (_, rows) ->
            rows.minOf { SearchText.rank(it.matchText, normalized) }
        }
        val items = hits.map { it.item }.distinctBy { it.id }.toDomainItems(locale)
        val collator = DisplayNames.collator(locale)
        return items
            .sortedWith(
                compareBy<MasterItem> { bestRank.getValue(it.id.value) }
                    .thenByDescending { it.useCount }
                    .thenComparator { a, b -> collator.compare(a.displayName, b.displayName) },
            )
            .take(limit)
    }

    override suspend fun getMasterItem(id: MasterItemId, locale: String): MasterItem? =
        masterItemDao.getById(id.value)?.let { listOf(it).toDomainItems(locale).single() }

    override suspend fun findMasterItemByName(categoryId: CategoryId, name: String, locale: String): MasterItem? {
        val wanted = SearchText.normalize(name)
        if (wanted.isEmpty()) return null
        val items = masterItemDao.getInCategory(categoryId.value)
        val translations = itemTranslations(items.map { it.canonicalKey }, locale)
        val language = DisplayNames.language(locale)
        val match = items.firstOrNull { item ->
            val names = listOfNotNull(
                item.customName,
                translations[item.canonicalKey]?.get(language),
                translations[item.canonicalKey]?.get(DisplayNames.FALLBACK_LOCALE),
            )
            names.any { SearchText.normalize(it) == wanted }
        }
        return match?.toDomain(translations, locale)
    }

    override suspend fun createMasterItem(categoryId: CategoryId, name: String, nameLocale: String, defaultUnit: UnitCode?): MasterItemId {
        val now = clock.nowMillis()
        val id = ids.newId()
        val item = MasterItemEntity(
            id = id,
            categoryId = categoryId.value,
            canonicalKey = "$CUSTOM_KEY_PREFIX$id",
            customName = name,
            customNameLocale = DisplayNames.language(nameLocale),
            defaultUnitCode = defaultUnit?.value,
            isCustom = true,
            createdAt = now,
            updatedAt = now,
        )
        db.withTransaction {
            masterItemDao.insert(item)
            searchIndexDao.insertAll(
                SearchText.rowsFor(item.id, item.categoryId, item.customName, item.customNameLocale, emptyList()),
            )
        }
        return MasterItemId(id)
    }

    override suspend fun setMasterItemHidden(id: MasterItemId, hidden: Boolean) {
        masterItemDao.setHidden(id.value, hidden, clock.nowMillis())
    }

    override fun observeUnits(): Flow<List<UnitDef>> =
        unitDao.observeAll().map { units -> units.map { it.toDomain() } }.distinctUntilChanged().flowOn(default)

    override suspend fun getUnit(code: UnitCode): UnitDef? = unitDao.getByCode(code.value)?.toDomain()

    override suspend fun createCustomUnit(label: String, allowsDecimal: Boolean): UnitCode {
        val code = UnitCode.CUSTOM_PREFIX + ids.newId()
        db.withTransaction {
            unitDao.insert(
                UnitDefEntity(
                    code = code,
                    allowsDecimal = allowsDecimal,
                    isCustom = true,
                    customLabel = label,
                    sortOrder = SparseOrder.after(unitDao.maxSortOrder()),
                ),
            )
        }
        return UnitCode(code)
    }

    private suspend fun List<MasterItemEntity>.toDomainItems(locale: String): List<MasterItem> {
        val translations = itemTranslations(map { it.canonicalKey }, locale)
        return map { it.toDomain(translations, locale) }
    }

    private suspend fun categoryTranslations(keys: List<String>, locale: String): TranslationIndex =
        if (keys.isEmpty()) emptyMap() else categoryDao.getTranslationsForKeys(keys.distinct(), DisplayNames.lookupLocales(locale)).toIndex()

    private suspend fun itemTranslations(keys: List<String>, locale: String): TranslationIndex =
        if (keys.isEmpty()) emptyMap() else masterItemDao.getTranslations(keys.distinct(), DisplayNames.lookupLocales(locale)).toIndex()

    /** Most used first, then alphabetical in the user's language. */
    private fun sortByUse(items: List<MasterItem>, locale: String): List<MasterItem> {
        val collator = DisplayNames.collator(locale)
        return items.sortedWith(
            compareByDescending<MasterItem> { it.useCount }
                .thenComparator { a, b -> collator.compare(a.displayName, b.displayName) },
        )
    }

    private companion object {
        /** Section 6.5: user items get "custom:<uuid>" so their key never collides with a seeded one. */
        const val CUSTOM_KEY_PREFIX = "custom:"
    }
}
