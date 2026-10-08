package com.dataloom.checklist.ai.fixtures

import com.dataloom.checklist.domain.model.BuiltInUnits
import com.dataloom.checklist.domain.model.Category
import com.dataloom.checklist.domain.model.CategoryId
import com.dataloom.checklist.domain.model.MasterItem
import com.dataloom.checklist.domain.model.MasterItemId
import com.dataloom.checklist.domain.model.UnitCode
import com.dataloom.checklist.domain.model.UnitDef
import com.dataloom.checklist.domain.repository.CatalogRepository
import java.io.File
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A [CatalogRepository] over the real bundled seed (data/src/main/assets/seed, read as files), with a
 * search that behaves like the Room FTS one: the user's language plus English plus custom names, every
 * query word a prefix of a word in a name or alias, exact > prefix > contains, then most used.
 * So parser tests exercise real names and transliterations in all 7 languages.
 */
class SeedCatalogRepository : CatalogRepository {

    private data class SeedItem(val key: String, val category: String, val defaultUnit: String?)

    private data class Names(val name: String, val aliases: List<String>)

    private val seedItems: List<SeedItem>
    private val categoryKeys: List<String>
    private val categoryNames = mutableMapOf<String, MutableMap<String, String>>() // locale -> key -> name
    private val itemNames = mutableMapOf<String, MutableMap<String, Names>>() // locale -> key -> names
    private val custom = MutableStateFlow<List<MasterItem>>(emptyList())
    private val customCategories = MutableStateFlow<List<Category>>(emptyList())
    private val units = MutableStateFlow(BuiltInUnits.all)
    private var nextId = 1

    val searchCalls = mutableListOf<Pair<String, String>>()

    init {
        val dir = File(System.getProperty("checklist.seedDir") ?: error("checklist.seedDir system property is not set"))
        val catalog = Json.parseToJsonElement(File(dir, "catalog.json").readText()).jsonObject
        categoryKeys = catalog.getValue("categories").jsonArray.map { it.jsonObject.string("key")!! }
        seedItems = catalog.getValue("items").jsonArray.map {
            val item = it.jsonObject
            SeedItem(item.string("key")!!, item.string("category")!!, item.string("defaultUnit"))
        }
        File(dir, "i18n").listFiles { file -> file.extension == "json" }!!.forEach { file ->
            val root = Json.parseToJsonElement(file.readText()).jsonObject
            val locale = root.string("locale")!!
            categoryNames[locale] = root.getValue("categories").jsonObject.mapValues { it.value.jsonPrimitive.content }.toMutableMap()
            itemNames[locale] = root.getValue("items").jsonObject.mapValues { (_, value) ->
                val names = value.jsonObject
                Names(names.string("name")!!, names["aliases"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty())
            }.toMutableMap()
        }
    }

    private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.takeIf { it.isString }?.content

    private fun language(locale: String) = locale.substringBefore('-').substringBefore('_').lowercase(Locale.ROOT)

    private fun categoryId(key: String) = CategoryId("cat-$key")

    fun category(key: String, locale: String = "en"): Category = seededCategory(key, locale)

    fun masterItem(key: String, locale: String = "en"): MasterItem = seededItem(seedItems.first { it.key == key }, locale)

    private fun seededCategory(key: String, locale: String) = Category(
        id = categoryId(key),
        canonicalKey = key,
        customName = null,
        displayName = categoryNames[language(locale)]?.get(key) ?: categoryNames.getValue("en").getValue(key),
        iconKey = key,
        isCustom = false,
        isHidden = false,
    )

    private fun seededItem(item: SeedItem, locale: String) = MasterItem(
        id = MasterItemId("mi-${item.key}"),
        categoryId = categoryId(item.category),
        canonicalKey = item.key,
        customName = null,
        displayName = itemNames[language(locale)]?.get(item.key)?.name ?: itemNames.getValue("en").getValue(item.key).name,
        defaultUnit = item.defaultUnit?.let(::UnitCode),
        isCustom = false,
        isHidden = false,
        useCount = 0,
    )

    private fun allItems(locale: String): List<MasterItem> = seedItems.map { seededItem(it, locale) } + custom.value

    override fun observeCategories(locale: String, includeHidden: Boolean): Flow<List<Category>> =
        customCategories.map { customs -> categoryKeys.map { seededCategory(it, locale) } + customs }

    override suspend fun getCategory(id: CategoryId, locale: String): Category? =
        categoryKeys.firstOrNull { categoryId(it) == id }?.let { seededCategory(it, locale) }
            ?: customCategories.value.firstOrNull { it.id == id }

    override suspend fun createCategory(name: String, iconKey: String): CategoryId {
        val id = CategoryId("cat-custom-${nextId++}")
        customCategories.value += Category(id, null, name, name, iconKey, isCustom = true, isHidden = false)
        return id
    }

    override suspend fun renameCategory(id: CategoryId, name: String?) = Unit

    override suspend fun setCategoryHidden(id: CategoryId, hidden: Boolean) = Unit

    override suspend fun countChecklistsUsing(id: CategoryId): Int = 0

    override suspend fun deleteCategory(id: CategoryId) = Unit

    override suspend fun categoryNameExists(name: String, locale: String): Boolean = false

    override fun observeMasterItems(categoryId: CategoryId, locale: String): Flow<List<MasterItem>> =
        custom.map { allItems(locale).filter { it.categoryId == categoryId } }

    override suspend fun searchMasterItems(query: String, locale: String, categoryId: CategoryId?, limit: Int): List<MasterItem> {
        searchCalls += query to locale
        val terms = normalize(query).split(' ').map { word -> word.filter(::isTokenChar) }.filter { it.isNotEmpty() }
        if (terms.isEmpty()) return emptyList()
        val wanted = normalize(query)
        val locales = listOf(language(locale), "en").distinct()
        val ranked = allItems(locale).mapNotNull { item ->
            if (categoryId != null && item.categoryId != categoryId) return@mapNotNull null
            val rows: List<List<String>> = if (item.isCustom) {
                listOf(listOf(item.customName!!))
            } else {
                locales.mapNotNull { itemNames[it]?.get(item.canonicalKey) }.map { listOf(it.name) + it.aliases }
            }
            val matching = rows.filter { phrases ->
                val words = phrases.flatMap { normalize(it).split(Regex("[^\\p{L}\\p{N}\\p{M}]+")) }.filter { it.isNotEmpty() }
                terms.all { term -> words.any { it.startsWith(term) } }
            }
            if (matching.isEmpty()) return@mapNotNull null
            val rank = matching.flatten().minOf { phrase ->
                val text = normalize(phrase)
                when {
                    text == wanted -> 0
                    text.startsWith(wanted) -> 1
                    text.contains(wanted) -> 2
                    else -> 3
                }
            }
            item to rank
        }
        return ranked.sortedWith(compareBy<Pair<MasterItem, Int>> { it.second }.thenBy { it.first.displayName })
            .map { it.first }
            .take(limit)
    }

    override suspend fun getMasterItem(id: MasterItemId, locale: String): MasterItem? = allItems(locale).firstOrNull { it.id == id }

    override suspend fun findMasterItemByName(categoryId: CategoryId, name: String, locale: String): MasterItem? =
        allItems(locale).firstOrNull { it.categoryId == categoryId && normalize(it.displayName) == normalize(name) }

    override suspend fun createMasterItem(categoryId: CategoryId, name: String, nameLocale: String, defaultUnit: UnitCode?): MasterItemId {
        val id = MasterItemId("mi-custom-${nextId++}")
        custom.value += MasterItem(id, categoryId, "custom:${id.value}", name, name, defaultUnit, isCustom = true, isHidden = false, useCount = 0)
        return id
    }

    override suspend fun setMasterItemHidden(id: MasterItemId, hidden: Boolean) = Unit

    override fun observeUnits(): Flow<List<UnitDef>> = units

    fun observeUnitsNow(): List<UnitDef> = units.value

    override suspend fun getUnit(code: UnitCode): UnitDef? = units.value.firstOrNull { it.code == code }

    override suspend fun createCustomUnit(label: String, allowsDecimal: Boolean): UnitCode {
        val code = UnitCode("${UnitCode.CUSTOM_PREFIX}${nextId++}")
        units.value += UnitDef(code, allowsDecimal, customLabel = label, sortOrder = 1000)
        return code
    }

    private fun normalize(text: String) =
        Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")

    private fun isTokenChar(char: Char) = when (char.category) {
        CharCategory.NON_SPACING_MARK, CharCategory.COMBINING_SPACING_MARK, CharCategory.ENCLOSING_MARK -> true
        else -> char.isLetterOrDigit()
    }
}
