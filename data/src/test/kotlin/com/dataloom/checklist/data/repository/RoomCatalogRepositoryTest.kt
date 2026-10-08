package com.dataloom.checklist.data.repository

import android.database.sqlite.SQLiteConstraintException
import app.cash.turbine.test
import com.dataloom.checklist.data.FakeClock
import com.dataloom.checklist.data.FakeSeedSource
import com.dataloom.checklist.data.SequentialIds
import com.dataloom.checklist.data.inMemoryDatabase
import com.dataloom.checklist.data.seed.SeedLoader
import com.dataloom.checklist.domain.model.CategoryId
import com.dataloom.checklist.domain.model.MasterItem
import com.dataloom.checklist.domain.model.NewChecklistItem
import com.dataloom.checklist.domain.model.UnitCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomCatalogRepositoryTest {

    private val clock = FakeClock()
    private val ids = SequentialIds()
    private val db = inMemoryDatabase()
    private val catalog = RoomCatalogRepository(db, clock, ids)
    private val checklists = RoomChecklistRepository(db, clock, ids)

    private lateinit var groceries: CategoryId
    private lateinit var vegetables: CategoryId

    @Before
    fun setUp() = runTest {
        SeedLoader(FakeSeedSource(), clock, SequentialIds("seed")).seedIfNeeded(db.openHelper.writableDatabase)
        val all = catalog.observeCategories("en").first()
        groceries = all.single { it.canonicalKey == "groceries" }.id
        vegetables = all.single { it.canonicalKey == "vegetables" }.id
    }

    @After
    fun tearDown() = db.close()

    // Display names (section 6.2)

    @Test
    fun displayNameFallsBackFromLocaleToEnglishToHumanizedKey() = runTest {
        val kannada = catalog.observeCategories("kn-IN").first().associateBy { it.canonicalKey }

        assertEquals("ದಿನಸಿ", kannada.getValue("groceries").displayName)
        assertEquals("Vegetables", kannada.getValue("vegetables").displayName)
        assertEquals("Cooking essentials", kannada.getValue("cooking_essentials").displayName)
    }

    @Test
    fun customNameOverridesTranslationsAndResetRestoresThem() = runTest {
        catalog.renameCategory(groceries, "Kirana")
        assertEquals("Kirana", catalog.getCategory(groceries, "kn")?.displayName)

        catalog.renameCategory(groceries, null)
        assertEquals("ದಿನಸಿ", catalog.getCategory(groceries, "kn")?.displayName)
    }

    @Test
    fun categoriesMostUsedFirstThenAlphabetical() = runTest {
        checklists.createChecklist("Week", null, listOf(vegetables))

        val names = catalog.observeCategories("en").first().map { it.displayName }

        assertEquals(listOf("Vegetables", "Cooking essentials", "Groceries"), names)
    }

    @Test
    fun hiddenCategoriesOnlyWhenRequested() = runTest {
        catalog.observeCategories("en").test {
            assertEquals(3, awaitItem().size)
            catalog.setCategoryHidden(vegetables, true)
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(3, catalog.observeCategories("en", includeHidden = true).first().size)
    }

    @Test
    fun customCategoryLifecycle() = runTest {
        val gifts = catalog.createCategory("Gifts", "🎁")
        assertTrue(catalog.categoryNameExists("  gifts ", "en"))
        assertTrue(catalog.categoryNameExists("ದಿನಸಿ", "kn"))
        assertFalse(catalog.categoryNameExists("Toys", "en"))

        val list = checklists.createChecklist("Birthday", null, listOf(gifts))
        assertEquals(1, catalog.countChecklistsUsing(gifts))
        val blocked = runCatching { catalog.deleteCategory(gifts) }.exceptionOrNull()
        assertTrue("Expected RESTRICT, got $blocked", blocked is SQLiteConstraintException)

        checklists.deleteChecklist(list)
        catalog.createMasterItem(gifts, "Card", "en", null)
        catalog.deleteCategory(gifts)
        assertNull(catalog.getCategory(gifts, "en"))
        assertTrue(catalog.searchMasterItems("card", "en").isEmpty())
    }

    // Master items

    @Test
    fun masterItemsVisibleMostUsedFirst() = runTest {
        val rice = item("rice")
        catalog.observeMasterItems(groceries, "en").test {
            assertEquals(listOf("Basmati rice", "Rice", "Rice flour"), awaitItem().map { it.displayName })

            addToNewChecklist(rice)
            assertEquals(listOf("Rice", "Basmati rice", "Rice flour"), awaitItem().map { it.displayName })

            catalog.setMasterItemHidden(rice.id, true)
            assertEquals(listOf("Basmati rice", "Rice flour"), awaitItem().map { it.displayName })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun createdMasterItemHasCustomKeyAndIsSearchableInAnyLanguage() = runTest {
        val id = catalog.createMasterItem(groceries, "Ragi hittu", "kn-IN", UnitCode("KG"))

        val created = catalog.getMasterItem(id, "en")!!
        assertTrue(created.canonicalKey.startsWith("custom:"))
        assertTrue(created.isCustom)
        assertEquals("Ragi hittu", created.displayName)
        assertEquals(listOf(id), catalog.searchMasterItems("ragi", "hi").map { it.id })
        assertEquals(id, catalog.findMasterItemByName(groceries, "RAGI HITTU", "en")?.id)
    }

    @Test
    fun findMasterItemByNameMatchesLocaleAndEnglishNames() = runTest {
        assertEquals("rice", catalog.findMasterItemByName(groceries, "ಅಕ್ಕಿ", "kn")?.canonicalKey)
        assertEquals("rice", catalog.findMasterItemByName(groceries, "rice", "kn")?.canonicalKey)
        assertNull(catalog.findMasterItemByName(vegetables, "rice", "en"))
    }

    // Search (section 6.6)

    @Test
    fun searchRanksExactThenPrefixThenContains() = runTest {
        val names = catalog.searchMasterItems("rice", "en").map { it.displayName }

        assertEquals(listOf("Rice", "Rice flour", "Basmati rice"), names)
    }

    @Test
    fun searchBreaksTiesByUseCount() = runTest {
        addToNewChecklist(item("rice_flour"))

        val names = catalog.searchMasterItems("ri", "en").map { it.displayName }

        assertEquals(listOf("Rice flour", "Rice", "Basmati rice"), names)
    }

    @Test
    fun searchInKannadaScriptAndTransliteration() = runTest {
        assertEquals(listOf("ಅಕ್ಕಿ", "ಅಕ್ಕಿ ಹಿಟ್ಟು"), catalog.searchMasterItems("ಅಕ್ಕಿ", "kn").map { it.displayName })
        assertEquals(listOf("ಅಕ್ಕಿ", "ಅಕ್ಕಿ ಹಿಟ್ಟು"), catalog.searchMasterItems("Akki", "kn").map { it.displayName })
        // English names stay searchable in any language.
        assertEquals("ಟೊಮೆಟೊ", catalog.searchMasterItems("tom", "kn").single().displayName)
    }

    @Test
    fun searchInHindiOnlyMatchesHindiAndEnglish() = runTest {
        assertEquals(listOf("चावल"), catalog.searchMasterItems("chawal", "hi").map { it.displayName })
        assertTrue(catalog.searchMasterItems("chawal", "kn").isEmpty())
        assertTrue(catalog.searchMasterItems("akki", "hi").isEmpty())
    }

    @Test
    fun searchFiltersByCategoryAndSkipsHidden() = runTest {
        assertTrue(catalog.searchMasterItems("tom", "en", categoryId = groceries).isEmpty())
        assertEquals(1, catalog.searchMasterItems("tom", "en", categoryId = vegetables).size)

        catalog.setCategoryHidden(vegetables, true)
        assertTrue(catalog.searchMasterItems("tom", "en").isEmpty())

        catalog.setMasterItemHidden(item("rice").id, true)
        assertEquals(listOf("Rice flour", "Basmati rice"), catalog.searchMasterItems("rice", "en").map { it.displayName })
    }

    @Test
    fun searchIgnoresFtsSyntaxAndHonoursLimit() = runTest {
        assertEquals(1, catalog.searchMasterItems("\"rice*\" (", "en", limit = 1).size)
        // Upper-case OR would be an FTS operator; lower-cased it is just another required word.
        assertTrue(catalog.searchMasterItems("rice OR tomato", "en").isEmpty())
        assertTrue(catalog.searchMasterItems("*\"-", "en").isNotEmpty()) // no terms: most used suggestions
        assertTrue(catalog.searchMasterItems("rice", "en", limit = 0).isEmpty())
    }

    // Units

    @Test
    fun customUnitsSortAfterBuiltIns() = runTest {
        val code = catalog.createCustomUnit("Sack", allowsDecimal = false)

        assertTrue(code.isCustom)
        val units = catalog.observeUnits().first()
        assertEquals(listOf("KG", "PIECE", code.value), units.map { it.code.value })
        assertEquals("Sack", catalog.getUnit(code)?.customLabel)
        assertNotNull(catalog.getUnit(UnitCode("KG")))
    }

    private suspend fun item(key: String): MasterItem =
        catalog.searchMasterItems(key.replace('_', ' '), "en").first { it.canonicalKey == key }

    private suspend fun addToNewChecklist(item: MasterItem) {
        val list = checklists.createChecklist("List ${ids.newId()}", null, listOf(item.categoryId))
        val section = checklists.observeChecklist(list, "en").first()!!.sections.single().id
        checklists.addItems(
            section,
            listOf(NewChecklistItem(item.id, item.canonicalKey, item.displayName, "en", null, null, null)),
        )
    }
}
