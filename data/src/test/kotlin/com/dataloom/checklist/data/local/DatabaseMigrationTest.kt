package com.dataloom.checklist.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.dataloom.checklist.data.local.database.CheckListDatabase
import com.dataloom.checklist.data.local.database.CheckListDatabaseCallback
import com.dataloom.checklist.data.local.database.DatabaseMigrations
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Migration infrastructure (CL-173, docs/testing.md "Migrations").
 *
 * [MigrationTestHelper] builds a database from the committed schema JSON (data/schemas, added to the
 * unit-test assets in data/build.gradle.kts), so these tests see exactly what an installed app has
 * on disk at that version, not what the current entities would create.
 *
 * Adding schema v2: write `MIGRATION_1_2` in [DatabaseMigrations], then add a test here that calls
 * [createV1WithData], runs `helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)` and
 * checks the new columns. The generic tests below then cover 1 -> latest automatically.
 */
@RunWith(RobolectricTestRunner::class)
class DatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CheckListDatabase::class.java,
    )

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun `schema v1 is created from the committed export`() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            assertEquals(1, db.version)
            val tables = db.names("SELECT name FROM sqlite_master WHERE type = 'table'")
            assertTrue(
                "v1 tables missing: $tables",
                tables.containsAll(
                    listOf(
                        "checklist", "checklist_category", "checklist_item", "category", "category_translation",
                        "master_item", "master_item_translation", "unit_def", "user_profile", "item_search_fts",
                        "seed_meta",
                    ),
                ),
            )
        }
    }

    @Test
    fun `committed schemas are numbered without gaps up to the database version`() {
        val versions = committedVersions()
        assertEquals((1..versions.max()).toList(), versions)
        assertEquals(latestVersion, versions.max())
    }

    @Test
    fun `every version step has a migration`() {
        (1 until latestVersion).forEach { from ->
            assertTrue(
                "No migration from $from to ${from + 1} in DatabaseMigrations.ALL",
                DatabaseMigrations.ALL.any { it.startVersion == from && it.endVersion == from + 1 },
            )
        }
    }

    @Test
    fun `a v1 database migrates to the latest schema and keeps its data`() = runTest {
        createV1WithData()

        helper.runMigrationsAndValidate(TEST_DB, latestVersion, true, *DatabaseMigrations.ALL).close()

        // Opening with the current Room code also checks the identity hash of the migrated schema.
        val room = Room.databaseBuilder(context, CheckListDatabase::class.java, TEST_DB)
            .addMigrations(*DatabaseMigrations.ALL)
            .addCallback(CheckListDatabaseCallback(seedLoader = null))
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals("Goa Trip", room.checklistDao().getById("list-1")?.title)
            assertNotNull(room.sectionDao().getById("sec-1"))
            val item = room.checklistItemDao().getById("item-1")
            assertEquals("Rice", item?.displayName)
            assertEquals(2_500L, item?.quantityMilli)
        } finally {
            room.close()
        }
    }

    /**
     * A small v1 data set written with raw SQL against the v1 columns. Keep it unchanged when the
     * schema moves on: it is what users of version 1 have on their phones.
     */
    private fun createV1WithData() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO unit_def (code, allows_decimal, is_custom, custom_label, sort_order) " +
                    "VALUES ('KG', 1, 0, NULL, 10)",
            )
            db.execSQL(
                "INSERT INTO category (id, canonical_key, custom_name, icon_key, is_custom, is_hidden, " +
                    "created_at, updated_at) " +
                    "VALUES ('cat-1', 'groceries', NULL, '🛒', 0, 0, 0, 0)",
            )
            db.execSQL(
                "INSERT INTO checklist (id, title, description, created_at, updated_at, is_archived, archived_at) " +
                    "VALUES ('list-1', 'Goa Trip', NULL, 0, 0, 0, NULL)",
            )
            db.execSQL(
                "INSERT INTO checklist_category (id, checklist_id, category_id, display_order, created_at) " +
                    "VALUES ('sec-1', 'list-1', 'cat-1', 1000, 0)",
            )
            db.execSQL(
                "INSERT INTO checklist_item (id, checklist_category_id, master_item_id, canonical_key, display_name, " +
                    "display_name_locale, quantity_milli, unit_code, notes, is_completed, completed_at, position, " +
                    "created_at, updated_at) " +
                    "VALUES ('item-1', 'sec-1', NULL, NULL, 'Rice', 'en', 2500, 'KG', NULL, 0, NULL, 1000, 0, 0)",
            )
        }
    }

    private fun SupportSQLiteDatabase.names(sql: String): List<String> = query(sql).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"

        // Gradle runs unit tests with the module directory (data/) as the working directory.
        val schemaDir = File("schemas/${CheckListDatabase::class.java.name}")

        fun committedVersions(): List<Int> =
            schemaDir.listFiles().orEmpty().mapNotNull { it.name.removeSuffix(".json").toIntOrNull() }.sorted()
    }

    /** The version the current code declares in @Database, as Room reports it for a fresh database. */
    private val latestVersion: Int by lazy {
        val db = Room.inMemoryDatabaseBuilder(context, CheckListDatabase::class.java).build()
        try {
            db.openHelper.writableDatabase.version
        } finally {
            db.close()
        }
    }
}
