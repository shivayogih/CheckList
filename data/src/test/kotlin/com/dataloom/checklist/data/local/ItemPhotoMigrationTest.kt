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
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Migration 1 to 2 (CL-210): a real version 1 database, built from the committed 1.json and filled
 * with data, is migrated; every old row must survive and the new table must work, including its
 * cascade. `runMigrationsAndValidate` compares the result with the committed 2.json.
 */
@RunWith(RobolectricTestRunner::class)
class ItemPhotoMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CheckListDatabase::class.java,
    )

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        context.deleteDatabase(DB)
    }

    @Test
    fun `migrating 1 to 2 keeps every row and adds an empty item_photo table with its index`() {
        createV1WithData()

        helper.runMigrationsAndValidate(DB, 2, true, DatabaseMigrations.MIGRATION_1_2).use { db ->
            assertEquals(1L, db.count("checklist"))
            assertEquals(2L, db.count("checklist_category"))
            assertEquals(3L, db.count("checklist_item"))
            assertEquals(1L, db.count("unit_def"))
            assertEquals(1L, db.count("category"))
            assertEquals(0L, db.count("item_photo"))
            val indices = db.names("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'item_photo'")
            assertTrue(indices.toString(), "index_item_photo_checklist_item_id_position" in indices)
            val item = db.query("SELECT display_name, quantity_milli, unit_code, position FROM checklist_item WHERE id = 'item-1'")
            item.use {
                assertTrue(it.moveToFirst())
                assertEquals("Rice", it.getString(0))
                assertEquals(2500L, it.getLong(1))
                assertEquals("KG", it.getString(2))
                assertEquals(1000, it.getInt(3))
            }
        }
    }

    @Test
    fun `after migrating the current Room code opens the database and photos cascade with their item`() = runTest {
        createV1WithData()
        helper.runMigrationsAndValidate(DB, 2, true, DatabaseMigrations.MIGRATION_1_2).close()

        val room = Room.databaseBuilder(context, CheckListDatabase::class.java, DB)
            .addMigrations(*DatabaseMigrations.ALL)
            .addCallback(CheckListDatabaseCallback(seedLoader = null))
            .allowMainThreadQueries()
            .build()
        try {
            val items = room.checklistItemDao()
            assertEquals("Rice", items.getById("item-1")?.displayName)
            room.openHelper.writableDatabase.execSQL(
                "INSERT INTO item_photo (id, checklist_item_id, file_name, width, height, byte_size, position, caption, created_at) " +
                    "VALUES ('ph-1', 'item-1', 'a.jpg', 800, 600, 12345, 1000, NULL, 5)",
            )
            assertEquals(1, room.itemPhotoDao().getForItem("item-1").size)

            items.delete("item-1")

            assertEquals(0, room.itemPhotoDao().getForItem("item-1").size)
            assertEquals(2L, room.openHelper.writableDatabase.count("checklist_item"))
        } finally {
            room.close()
        }
    }

    /** Written with raw SQL against the version 1 columns: this is what version 1 users have on their phones. */
    private fun createV1WithData() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL("INSERT INTO unit_def (code, allows_decimal, is_custom, custom_label, sort_order) VALUES ('KG', 1, 0, NULL, 10)")
            db.execSQL(
                "INSERT INTO category (id, canonical_key, custom_name, icon_key, is_custom, is_hidden, created_at, updated_at) " +
                    "VALUES ('cat-1', 'groceries', NULL, 'x', 0, 0, 0, 0)",
            )
            db.execSQL(
                "INSERT INTO checklist (id, title, description, created_at, updated_at, is_archived, archived_at) " +
                    "VALUES ('list-1', 'Goa Trip', 'Notes', 0, 0, 0, NULL)",
            )
            db.execSQL(
                "INSERT INTO checklist_category (id, checklist_id, category_id, display_order, created_at) " +
                    "VALUES ('sec-1', 'list-1', 'cat-1', 1000, 0)",
            )
            db.execSQL(
                "INSERT INTO checklist_category (id, checklist_id, category_id, display_order, created_at) " +
                    "VALUES ('sec-2', 'list-1', 'cat-1', 2000, 0)",
            )
            listOf("item-1" to "Rice", "item-2" to "Salt", "item-3" to "Oil").forEachIndexed { index, (id, name) ->
                db.execSQL(
                    "INSERT INTO checklist_item (id, checklist_category_id, master_item_id, canonical_key, display_name, " +
                        "display_name_locale, quantity_milli, unit_code, notes, is_completed, completed_at, position, created_at, updated_at) " +
                        "VALUES ('$id', 'sec-1', NULL, NULL, '$name', 'en', 2500, 'KG', NULL, 0, NULL, ${(index + 1) * 1000}, 0, 0)",
                )
            }
        }
    }

    private fun SupportSQLiteDatabase.count(table: String): Long =
        query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }

    private fun SupportSQLiteDatabase.names(sql: String): List<String> = query(sql).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
    }

    private companion object {
        const val DB = "item-photo-migration-test.db"
    }
}
