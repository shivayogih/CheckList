package com.dataloom.checklist.data.local

import android.database.sqlite.SQLiteConstraintException
import com.dataloom.checklist.data.inMemoryDatabase
import com.dataloom.checklist.data.local.entity.CategoryEntity
import com.dataloom.checklist.data.local.entity.ChecklistCategoryEntity
import com.dataloom.checklist.data.local.entity.ChecklistEntity
import com.dataloom.checklist.data.local.entity.ChecklistItemEntity
import com.dataloom.checklist.data.local.entity.MasterItemEntity
import com.dataloom.checklist.data.local.entity.UnitDefEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Foreign keys, ON DELETE rules and the category name check from schema v1 (section 5.3). */
@RunWith(RobolectricTestRunner::class)
class DatabaseConstraintsTest {

    private val db = inMemoryDatabase()

    @Before
    fun setUp() = runTest {
        db.unitDao().insert(UnitDefEntity("KG", allowsDecimal = true, isCustom = false, customLabel = null, sortOrder = 10))
        db.categoryDao().insert(category("cat-1"))
        db.masterItemDao().insert(
            MasterItemEntity(
                id = "master-1",
                categoryId = "cat-1",
                canonicalKey = "rice",
                customName = null,
                customNameLocale = null,
                defaultUnitCode = "KG",
                isCustom = false,
                createdAt = 0,
                updatedAt = 0,
            ),
        )
        db.checklistDao().insert(ChecklistEntity("list-1", "Trip", null, createdAt = 0, updatedAt = 0))
        db.sectionDao().insertAll(listOf(ChecklistCategoryEntity("sec-1", "list-1", "cat-1", displayOrder = 1000, createdAt = 0)))
        db.checklistItemDao().insertAll(listOf(item("item-1", masterItemId = "master-1")))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun deletingChecklistCascadesToSectionsAndItems() = runTest {
        db.checklistDao().delete("list-1")

        assertNull(db.sectionDao().getById("sec-1"))
        assertNull(db.checklistItemDao().getById("item-1"))
    }

    @Test
    fun deletingSectionCascadesToItems() = runTest {
        db.sectionDao().delete("sec-1")

        assertNull(db.checklistItemDao().getById("item-1"))
    }

    @Test
    fun categoryInUseCannotBeDeleted() = runTest {
        assertThrows(SQLiteConstraintException::class.java) {
            db.openHelper.writableDatabase.execSQL("DELETE FROM category WHERE id = 'cat-1'")
        }
    }

    @Test
    fun deletingMasterItemKeepsChecklistItemSnapshot() = runTest {
        db.openHelper.writableDatabase.execSQL("DELETE FROM master_item WHERE id = 'master-1'")

        val item = db.checklistItemDao().getById("item-1")!!
        assertNull(item.masterItemId)
        assertEquals("Rice", item.displayName)
    }

    @Test
    fun deletingUnusedCategoryCascadesToMasterItems() = runTest {
        db.checklistDao().delete("list-1")

        db.categoryDao().delete("cat-1")

        assertNull(db.masterItemDao().getById("master-1"))
    }

    @Test
    fun categoryNeedsCanonicalKeyOrCustomName() = runTest {
        assertThrows(SQLiteConstraintException::class.java) {
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO category (id, canonical_key, custom_name, icon_key, is_custom, is_hidden, created_at, updated_at) " +
                    "VALUES ('bad', NULL, NULL, 'x', 1, 0, 0, 0)",
            )
        }
        assertThrows(SQLiteConstraintException::class.java) {
            db.openHelper.writableDatabase.execSQL("UPDATE category SET canonical_key = NULL WHERE id = 'cat-1'")
        }
    }

    @Test
    fun categoryAppearsAtMostOncePerChecklist() = runTest {
        val duplicate = ChecklistCategoryEntity("sec-2", "list-1", "cat-1", displayOrder = 2000, createdAt = 0)

        val error = runCatching { db.sectionDao().insertAll(listOf(duplicate)) }.exceptionOrNull()

        assertTrue("Expected a constraint error, got $error", error is SQLiteConstraintException)
    }

    @Test
    fun itemUnitMustExist() = runTest {
        val error = runCatching {
            db.checklistItemDao().insertAll(listOf(item("item-2", masterItemId = null, unitCode = "NOPE")))
        }.exceptionOrNull()

        assertTrue("Expected a constraint error, got $error", error is SQLiteConstraintException)
    }

    private fun category(id: String) = CategoryEntity(
        id = id,
        canonicalKey = "groceries",
        customName = null,
        iconKey = "x",
        isCustom = false,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun item(id: String, masterItemId: String?, unitCode: String? = "KG") = ChecklistItemEntity(
        id = id,
        checklistCategoryId = "sec-1",
        masterItemId = masterItemId,
        canonicalKey = "rice",
        displayName = "Rice",
        displayNameLocale = "en",
        quantityMilli = 2_500,
        unitCode = unitCode,
        notes = null,
        isCompleted = false,
        completedAt = null,
        position = 1000,
        createdAt = 0,
        updatedAt = 0,
    )
}
