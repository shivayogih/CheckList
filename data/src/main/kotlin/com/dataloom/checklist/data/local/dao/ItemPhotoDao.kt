package com.dataloom.checklist.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.dataloom.checklist.data.local.entity.ItemPhotoEntity

/** Rows of item_photo. Reads of a whole checklist go through the @Relation in [ChecklistDao.observeDetail]. */
@Dao
interface ItemPhotoDao {

    @Insert
    suspend fun insertAll(photos: List<ItemPhotoEntity>)

    @Query("SELECT * FROM item_photo WHERE checklist_item_id = :itemId ORDER BY position, created_at, id")
    suspend fun getForItem(itemId: String): List<ItemPhotoEntity>

    @Query("SELECT * FROM item_photo WHERE id = :id")
    suspend fun getById(id: String): ItemPhotoEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM checklist_item WHERE id = :itemId)")
    suspend fun itemExists(itemId: String): Boolean

    @Query("SELECT MAX(position) FROM item_photo WHERE checklist_item_id = :itemId")
    suspend fun maxPosition(itemId: String): Int?

    @Query("DELETE FROM item_photo WHERE id = :id")
    suspend fun delete(id: String): Int

    @Query("UPDATE item_photo SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: String, position: Int)

    @Query("UPDATE item_photo SET caption = :caption WHERE id = :id")
    suspend fun updateCaption(id: String, caption: String?)

    @Query("SELECT file_name FROM item_photo WHERE checklist_item_id = :itemId")
    suspend fun fileNamesOfItem(itemId: String): List<String>

    @Query(
        """
        SELECT p.file_name FROM item_photo p
        INNER JOIN checklist_item i ON i.id = p.checklist_item_id
        WHERE i.checklist_category_id = :sectionId
        """,
    )
    suspend fun fileNamesOfSection(sectionId: String): List<String>

    @Query(
        """
        SELECT p.file_name FROM item_photo p
        INNER JOIN checklist_item i ON i.id = p.checklist_item_id
        INNER JOIN checklist_category s ON s.id = i.checklist_category_id
        WHERE s.checklist_id = :checklistId
        """,
    )
    suspend fun fileNamesOfChecklist(checklistId: String): List<String>

    @Query("SELECT file_name FROM item_photo")
    suspend fun allFileNames(): List<String>
}
