package com.dataloom.checklist.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.dataloom.checklist.data.local.entity.ChecklistCategoryEntity
import com.dataloom.checklist.data.local.entity.ChecklistEntity
import com.dataloom.checklist.data.local.entity.ChecklistItemEntity
import com.dataloom.checklist.data.local.entity.ChecklistSummaryRow
import com.dataloom.checklist.data.local.entity.ChecklistWithSections
import kotlinx.coroutines.flow.Flow

@Dao
interface ChecklistDao {

    @Insert
    suspend fun insert(checklist: ChecklistEntity)

    /**
     * [archived] null returns both. [pattern] is an escaped LIKE pattern ("%milk%") or null.
     * Newest first; other sort orders are applied by the repository.
     */
    @Query(
        """
        SELECT c.*,
          (SELECT COUNT(*) FROM checklist_item i
             INNER JOIN checklist_category s ON s.id = i.checklist_category_id
             WHERE s.checklist_id = c.id) AS total_items,
          (SELECT COUNT(*) FROM checklist_item i
             INNER JOIN checklist_category s ON s.id = i.checklist_category_id
             WHERE s.checklist_id = c.id AND i.is_completed = 1) AS completed_items
        FROM checklist c
        WHERE (:archived IS NULL OR c.is_archived = :archived)
          AND (:pattern IS NULL
               OR c.title LIKE :pattern ESCAPE '\'
               OR c.description LIKE :pattern ESCAPE '\')
        ORDER BY c.updated_at DESC, c.id
        """,
    )
    fun observeSummaries(archived: Boolean?, pattern: String?): Flow<List<ChecklistSummaryRow>>

    @Transaction
    @Query("SELECT * FROM checklist WHERE id = :id")
    fun observeDetail(id: String): Flow<ChecklistWithSections?>

    @Transaction
    @Query("SELECT * FROM checklist WHERE id = :id")
    suspend fun getDetail(id: String): ChecklistWithSections?

    @Query("SELECT * FROM checklist WHERE id = :id")
    suspend fun getById(id: String): ChecklistEntity?

    @Query("UPDATE checklist SET title = :title, description = :description, updated_at = :now WHERE id = :id")
    suspend fun updateContent(id: String, title: String, description: String?, now: Long): Int

    @Query("UPDATE checklist SET is_archived = :archived, archived_at = :archivedAt, updated_at = :now WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, archivedAt: Long?, now: Long): Int

    /** Any change inside a checklist moves it to the top of "Recent". */
    @Query("UPDATE checklist SET updated_at = :now WHERE id = :id")
    suspend fun touch(id: String, now: Long)

    @Query("DELETE FROM checklist WHERE id = :id")
    suspend fun delete(id: String): Int

    @Query(
        """
        SELECT EXISTS(
          SELECT 1 FROM checklist
          WHERE lower(title) = lower(:title) AND (:excludingId IS NULL OR id != :excludingId)
        )
        """,
    )
    suspend fun titleExists(title: String, excludingId: String?): Boolean
}

/** Sections: rows of checklist_category. */
@Dao
interface SectionDao {

    @Insert
    suspend fun insertAll(sections: List<ChecklistCategoryEntity>)

    @Query("SELECT * FROM checklist_category WHERE id = :id")
    suspend fun getById(id: String): ChecklistCategoryEntity?

    @Query("SELECT * FROM checklist_category WHERE checklist_id = :checklistId ORDER BY display_order, created_at, id")
    suspend fun getForChecklist(checklistId: String): List<ChecklistCategoryEntity>

    @Query("UPDATE checklist_category SET display_order = :displayOrder WHERE id = :id")
    suspend fun updateDisplayOrder(id: String, displayOrder: Int)

    @Query("DELETE FROM checklist_category WHERE id = :id")
    suspend fun delete(id: String): Int

    @Query("SELECT COUNT(DISTINCT checklist_id) FROM checklist_category WHERE category_id = :categoryId")
    suspend fun countChecklistsUsing(categoryId: String): Int
}

@Dao
interface ChecklistItemDao {

    @Insert
    suspend fun insertAll(items: List<ChecklistItemEntity>)

    @Update
    suspend fun update(item: ChecklistItemEntity)

    @Query("SELECT * FROM checklist_item WHERE id = :id")
    suspend fun getById(id: String): ChecklistItemEntity?

    @Query("SELECT * FROM checklist_item WHERE checklist_category_id = :sectionId ORDER BY position, created_at, id")
    suspend fun getForSection(sectionId: String): List<ChecklistItemEntity>

    @Query("SELECT MAX(position) FROM checklist_item WHERE checklist_category_id = :sectionId")
    suspend fun maxPosition(sectionId: String): Int?

    @Query(
        """
        UPDATE checklist_item SET is_completed = :completed, completed_at = :completedAt, updated_at = :now
        WHERE id = :id
        """,
    )
    suspend fun setCompleted(id: String, completed: Boolean, completedAt: Long?, now: Long): Int

    @Query("UPDATE checklist_item SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: String, position: Int)

    @Query("DELETE FROM checklist_item WHERE id = :id")
    suspend fun delete(id: String): Int

    @Query(
        """
        SELECT s.checklist_id FROM checklist_item i
        INNER JOIN checklist_category s ON s.id = i.checklist_category_id
        WHERE i.id = :itemId
        """,
    )
    suspend fun checklistIdOf(itemId: String): String?
}
