package com.dataloom.checklist.data.seed

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.dataloom.checklist.data.FakeClock
import com.dataloom.checklist.data.FakeSeedSource
import com.dataloom.checklist.data.SequentialIds
import com.dataloom.checklist.data.inMemoryDatabase
import com.dataloom.checklist.data.repository.RoomCatalogRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The India master catalogue (CL-310): upgrade from the first seed, idempotency, search, translations. */
@RunWith(RobolectricTestRunner::class)
class IndiaCatalogSeedTest {

    private val clock = FakeClock()
    private val db = inMemoryDatabase()
    private val sql: SupportSQLiteDatabase get() = db.openHelper.writableDatabase
    private val json = Json { ignoreUnknownKeys = true }
    private val bundled = AssetSeedSource(ApplicationProvider.getApplicationContext())
    private val catalog = json.decodeFromString<SeedCatalog>(requireNotNull(bundled.catalog()))
    private val files = bundled.translations()
        .map { json.decodeFromString<SeedTranslations>(it) }
        .associateBy { it.locale }

    @After
    fun tearDown() = db.close()

    @Test
    fun bundledCatalogHas31CategoriesAnd563CatalogueItems() {
        assertEquals(31, catalog.categories.count { it.catalogId != null })
        val catalogueItems = catalog.items.filter { it.catalogId != null }
        assertEquals(563, catalogueItems.size)
        assertEquals(563, catalogueItems.map { it.catalogId }.toSet().size)
        assertEquals(catalog.items.size, catalog.items.map { it.key }.toSet().size)
        assertEquals(setOf("en", "kn", "hi", "ta", "te", "mr", "ml"), files.keys)
    }

    @Test
    fun everyItemHasNamesInAllSevenLanguagesOrIsListedForReview() {
        val review = File("../docs/catalog-translation-review.md").readText()
        val scripts = mapOf(
            "kn" to 'ಀ'..'೿', "hi" to 'ऀ'..'ॿ', "mr" to 'ऀ'..'ॿ',
            "ta" to '஀'..'௿', "te" to 'ఀ'..'౿', "ml" to 'ഀ'..'ൿ',
        )
        scripts.forEach { (locale, range) ->
            val translations = files.getValue(locale)
            catalog.items.forEach { item ->
                val name = translations.items[item.key]?.name
                assertTrue("$locale is missing ${item.key}", !name.isNullOrBlank())
                val native = name!!.any { it in range }
                // A name with no native script (ORS, LED) or a fallback must be flagged for a reviewer.
                val flagged = item.catalogId != null && review.contains("`${item.catalogId}`")
                assertTrue("$locale ${item.key} '$name' has no native script, not in review list", native || flagged)
            }
            catalog.categories.forEach {
                assertTrue("$locale category ${it.key}", !translations.categories[it.key].isNullOrBlank())
            }
        }
    }

    @Test
    fun upgradeFromTheFirstSeedKeepsIdsAndUserData() = runTest {
        val locales = listOf("en", "kn", "hi", "ta", "te", "mr", "ml")
        val v1 = FakeSeedSource(legacy("catalog.json"), locales.map { legacy("i18n/$it.json") })
        assertTrue(SeedLoader(v1, clock, SequentialIds("old")).seedIfNeeded(sql))
        assertEquals(15, count("category"))
        assertEquals(87, count("master_item"))
        val riceId = scalar("SELECT id FROM master_item WHERE canonical_key = 'rice'")
        val sweetsId = scalar("SELECT id FROM master_item WHERE canonical_key = 'sweets'")
        val groceriesId = scalar("SELECT id FROM category WHERE canonical_key = 'groceries'")
        val giftsId = scalar("SELECT id FROM category WHERE canonical_key = 'gifts'")
        // The user's data: a renamed category, a custom item in an old category, a checklist snapshot.
        sql.execSQL("UPDATE category SET custom_name = 'My shop' WHERE canonical_key = 'vegetables'")
        sql.execSQL("UPDATE master_item SET is_hidden = 1 WHERE canonical_key = 'tea'")
        sql.execSQL("INSERT INTO checklist (id, title, created_at, updated_at) VALUES ('cl', 'Week', 1, 1)")
        sql.execSQL(
            "INSERT INTO checklist_category (id, checklist_id, category_id, display_order, created_at)" +
                " VALUES ('cc', 'cl', '$groceriesId', 0, 1)",
        )
        sql.execSQL(
            "INSERT INTO checklist_item (id, checklist_category_id, master_item_id, canonical_key, display_name," +
                " display_name_locale, quantity_milli, unit_code, is_completed, position, created_at, updated_at)" +
                " VALUES ('ci', 'cc', '$riceId', 'rice', 'Old rice name', 'en', 2000, 'KG', 1, 0, 1, 1)",
        )
        val snapshotBefore = row("SELECT * FROM checklist_item WHERE id = 'ci'")

        assertTrue(SeedLoader(bundled, clock, SequentialIds("new")).seedIfNeeded(sql))

        // Existing ids are kept: same row, so the snapshot still points at it.
        assertEquals(riceId, scalar("SELECT id FROM master_item WHERE canonical_key = 'rice'"))
        assertEquals(snapshotBefore, row("SELECT * FROM checklist_item WHERE id = 'ci'"))
        assertEquals("1", scalar("SELECT COUNT(*) FROM checklist_item WHERE master_item_id = '$riceId'"))
        // New master data arrived; nothing was deleted.
        assertEquals(catalog.categories.size + 3, count("category"))
        assertEquals(catalog.items.size, count("master_item"))
        assertEquals(13, count("unit_def"))
        assertEquals("1", scalar("SELECT COUNT(*) FROM master_item WHERE canonical_key = 'itm0003'"))
        // User-owned rows are untouched.
        assertEquals("My shop", scalar("SELECT custom_name FROM category WHERE canonical_key = 'vegetables'"))
        assertEquals("1", scalar("SELECT is_hidden FROM master_item WHERE canonical_key = 'tea'"))
        // A merged-away category is hidden once empty and its item moved to Events & Celebrations.
        assertEquals("1", scalar("SELECT is_hidden FROM category WHERE id = '$giftsId'"))
        assertEquals(
            scalar("SELECT id FROM category WHERE canonical_key = 'cat029'"),
            scalar("SELECT category_id FROM master_item WHERE id = '$sweetsId'"),
        )
    }

    @Test
    fun applyingTheCatalogueTwiceChangesNothing() = runTest {
        val loader = SeedLoader(bundled, clock, SequentialIds("seed"))
        assertTrue(loader.seedIfNeeded(sql))
        val ids = column("SELECT id FROM master_item ORDER BY canonical_key")
        val translations = count("master_item_translation")
        val index = db.searchIndexDao().count()
        assertFalse(loader.seedIfNeeded(sql))

        sql.execSQL("DELETE FROM seed_meta")
        assertTrue(loader.seedIfNeeded(sql))

        assertEquals(ids, column("SELECT id FROM master_item ORDER BY canonical_key"))
        assertEquals(translations, count("master_item_translation"))
        assertEquals(index, db.searchIndexDao().count())
        assertEquals(catalog.categories.size, count("category"))
        assertEquals(7 * catalog.items.size, translations)
    }

    @Test
    fun itemsSearchableInKannadaByNativeNameEnglishNameAndTag() = runTest {
        SeedLoader(bundled, clock, SequentialIds("seed")).seedIfNeeded(sql)
        val repo = RoomCatalogRepository(db, clock, SequentialIds("x"), Dispatchers.Unconfined)
        val basmati = catalog.items.single { it.catalogId == "ITM0003" }
        val kannadaName = files.getValue("kn").items.getValue(basmati.key).name

        val byNative = repo.searchMasterItems(kannadaName.split(' ').first(), "kn").map { it.canonicalKey }
        val byEnglish = repo.searchMasterItems("basmati", "kn").map { it.canonicalKey }
        val byTag = repo.searchMasterItems("pantry", "kn", limit = 500).map { it.canonicalKey }

        assertTrue(basmati.key in byNative)
        assertTrue(basmati.key in byEnglish)
        assertTrue(basmati.key in byTag)
        assertTrue(repo.searchMasterItems("Rice & grains", "ta", limit = 500).any { it.canonicalKey == basmati.key })
    }

    private fun legacy(path: String): String = File("../tools/seed/src_data/legacy_v1/$path").readText()

    private fun count(table: String): Int = scalar("SELECT COUNT(*) FROM $table")!!.toInt()

    private fun scalar(query: String): String? =
        sql.query(query).use { if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null }

    private fun column(query: String): List<String> =
        sql.query(query).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    private fun row(query: String): List<String?> = sql.query(query).use { cursor ->
        cursor.moveToFirst()
        List(cursor.columnCount) { if (cursor.isNull(it)) null else cursor.getString(it) }
    }
}
