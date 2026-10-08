package com.dataloom.checklist.data.seed

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.dataloom.checklist.data.FakeClock
import com.dataloom.checklist.data.FakeSeedSource
import com.dataloom.checklist.data.SequentialIds
import com.dataloom.checklist.data.TestCatalog
import com.dataloom.checklist.data.inMemoryDatabase
import com.dataloom.checklist.data.local.database.CheckListDatabase
import com.dataloom.checklist.data.local.database.CheckListDatabaseCallback
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SeedLoaderTest {

    private val clock = FakeClock()
    private val source = FakeSeedSource()
    private val loader = SeedLoader(source, clock, SequentialIds("seed"))
    private val db = inMemoryDatabase()
    private val sql: SupportSQLiteDatabase get() = db.openHelper.writableDatabase

    @After
    fun tearDown() = db.close()

    @Test
    fun firstRunSeedsUnitsCategoriesItemsTranslationsAndIndex() = runTest {
        assertTrue(loader.seedIfNeeded(sql))

        assertEquals(2, count("unit_def"))
        assertEquals(3, count("category"))
        assertEquals(5, count("master_item"))
        assertEquals(2 + 1 + 1, count("category_translation"))
        assertEquals(4 + 3 + 1, count("master_item_translation"))
        // One index row per item translation; no custom names yet.
        assertEquals(8, db.searchIndexDao().count())
        assertEquals(1, db.seedMetaDao().seedVersion())
        // "akki" alias stored for search.
        assertEquals("akki", scalar("SELECT aliases FROM master_item_translation WHERE canonical_key = 'rice' AND locale = 'kn'"))
    }

    @Test
    fun unitMissingFromCatalogFallsBackToNoDefaultUnit() = runTest {
        loader.seedIfNeeded(sql)

        assertNull(scalar("SELECT default_unit_code FROM master_item WHERE canonical_key = 'cooking_oil'"))
    }

    @Test
    fun secondRunWithSameVersionIsANoOp() = runTest {
        loader.seedIfNeeded(sql)
        val idsBefore = column("SELECT id FROM master_item ORDER BY id")

        assertFalse(loader.seedIfNeeded(sql))

        assertEquals(idsBefore, column("SELECT id FROM master_item ORDER BY id"))
        assertEquals(3, count("category"))
        assertEquals(8, db.searchIndexDao().count())
    }

    @Test
    fun reapplyingTheSameCatalogNeverDuplicatesRows() = runTest {
        loader.seedIfNeeded(sql)
        sql.execSQL("DELETE FROM seed_meta")

        assertTrue(loader.seedIfNeeded(sql))

        assertEquals(3, count("category"))
        assertEquals(5, count("master_item"))
        assertEquals(8, db.searchIndexDao().count())
    }

    @Test
    fun newerVersionUpdatesSeededRowsAndAddsNewOnes() = runTest {
        loader.seedIfNeeded(sql)
        val tomatoId = scalar("SELECT id FROM master_item WHERE canonical_key = 'tomato'")
        source.catalog = TestCatalog.CATALOG_V2

        assertTrue(loader.seedIfNeeded(sql))

        assertEquals("🧺", scalar("SELECT icon_key FROM category WHERE canonical_key = 'groceries'"))
        assertEquals(groceriesId(), scalar("SELECT category_id FROM master_item WHERE canonical_key = 'tomato'"))
        assertEquals("PIECE", scalar("SELECT default_unit_code FROM master_item WHERE canonical_key = 'tomato'"))
        // Updated in place: same row, so checklist back-references stay valid.
        assertEquals(tomatoId, scalar("SELECT id FROM master_item WHERE canonical_key = 'tomato'"))
        assertEquals(6, count("master_item"))
        assertEquals(2, db.seedMetaDao().seedVersion())
    }

    @Test
    fun newerVersionNeverOverwritesUserCustomizedOrHiddenRows() = runTest {
        loader.seedIfNeeded(sql)
        sql.execSQL("UPDATE category SET custom_name = 'My shop' WHERE canonical_key = 'groceries'")
        sql.execSQL("UPDATE category SET is_hidden = 1 WHERE canonical_key = 'vegetables'")
        sql.execSQL("UPDATE master_item SET is_hidden = 1 WHERE canonical_key = 'tomato'")
        sql.execSQL("UPDATE master_item SET custom_name = 'Our flour', custom_name_locale = 'en' WHERE canonical_key = 'rice_flour'")
        source.catalog = TestCatalog.CATALOG_V2

        loader.seedIfNeeded(sql)

        assertEquals("🛒", scalar("SELECT icon_key FROM category WHERE canonical_key = 'groceries'"))
        assertEquals("My shop", scalar("SELECT custom_name FROM category WHERE canonical_key = 'groceries'"))
        assertEquals("🥕", scalar("SELECT icon_key FROM category WHERE canonical_key = 'vegetables'"))
        assertEquals("1", scalar("SELECT is_hidden FROM category WHERE canonical_key = 'vegetables'"))
        assertEquals(vegetablesId(), scalar("SELECT category_id FROM master_item WHERE canonical_key = 'tomato'"))
        assertEquals("1", scalar("SELECT is_hidden FROM master_item WHERE canonical_key = 'tomato'"))
        assertEquals("KG", scalar("SELECT default_unit_code FROM master_item WHERE canonical_key = 'rice_flour'"))
        assertEquals("Our flour", scalar("SELECT custom_name FROM master_item WHERE canonical_key = 'rice_flour'"))
        // The custom name stays searchable after the index rebuild.
        assertEquals(
            "1",
            scalar("SELECT COUNT(*) FROM item_search_fts WHERE ref_type = 'CUSTOM' AND text = 'our flour'"),
        )
    }

    @Test
    fun missingCatalogDoesNothing() = runTest {
        source.catalog = null

        assertFalse(loader.seedIfNeeded(sql))
        assertEquals(0, count("category"))
    }

    @Test
    fun databaseCallbackSeedsOnCreate() = runTest {
        val seeded = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CheckListDatabase::class.java)
            .addCallback(CheckListDatabaseCallback(loader))
            .build()
        try {
            assertEquals(1, seeded.seedMetaDao().seedVersion())
            assertEquals(3, seeded.categoryDao().getVisible().size)
        } finally {
            seeded.close()
        }
    }

    @Test
    fun bundledAssetsAreValidAndSeed() = runTest {
        val assets = AssetSeedSource(ApplicationProvider.getApplicationContext())
        val json = Json { ignoreUnknownKeys = true }
        val catalog = json.decodeFromString<SeedCatalog>(requireNotNull(assets.catalog()))
        val files = assets.translations().map { json.decodeFromString<SeedTranslations>(it) }

        val unitCodes = catalog.units.map { it.code }.toSet()
        val categoryKeys = catalog.categories.map { it.key }.toSet()
        assertTrue(catalog.items.all { it.category in categoryKeys })
        assertTrue(catalog.items.all { it.defaultUnit == null || it.defaultUnit in unitCodes })
        val english = files.single { it.locale == "en" }
        assertTrue("Every item needs an English name", catalog.items.all { it.key in english.items })
        assertTrue("Every category needs an English name", catalog.categories.all { it.key in english.categories })

        assertTrue(SeedLoader(assets, clock, SequentialIds()).seedIfNeeded(sql))
        assertEquals(catalog.items.size, count("master_item"))
    }

    private fun groceriesId() = scalar("SELECT id FROM category WHERE canonical_key = 'groceries'")

    private fun vegetablesId() = scalar("SELECT id FROM category WHERE canonical_key = 'vegetables'")

    private fun count(table: String): Int = scalar("SELECT COUNT(*) FROM $table")!!.toInt()

    private fun scalar(query: String): String? =
        sql.query(query).use { if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null }

    private fun column(query: String): List<String> =
        sql.query(query).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
}
