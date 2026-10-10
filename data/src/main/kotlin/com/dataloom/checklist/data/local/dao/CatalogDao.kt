package com.dataloom.checklist.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.dataloom.checklist.data.local.entity.CategoryEntity
import com.dataloom.checklist.data.local.entity.CategoryTranslationEntity
import com.dataloom.checklist.data.local.entity.CategoryWithUsage
import com.dataloom.checklist.data.local.entity.ItemSearchFtsEntity
import com.dataloom.checklist.data.local.entity.MasterItemEntity
import com.dataloom.checklist.data.local.entity.MasterItemMatch
import com.dataloom.checklist.data.local.entity.MasterItemTranslationEntity
import com.dataloom.checklist.data.local.entity.SeedMetaEntity
import com.dataloom.checklist.data.local.entity.UnitDefEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Insert
    suspend fun insert(category: CategoryEntity)

    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Query(
        """
        SELECT c.*,
            (SELECT COUNT(*) FROM checklist_category cc WHERE cc.category_id = c.id) AS usage_count,
            (SELECT MAX(cc.created_at) FROM checklist_category cc WHERE cc.category_id = c.id) AS last_used_at
        FROM category c
        WHERE :includeHidden OR c.is_hidden = 0
        """,
    )
    fun observeWithUsage(includeHidden: Boolean): Flow<List<CategoryWithUsage>>

    @Query("SELECT * FROM category WHERE is_hidden = 0")
    suspend fun getVisible(): List<CategoryEntity>

    @Query("UPDATE category SET custom_name = :name, updated_at = :now WHERE id = :id")
    suspend fun rename(id: String, name: String?, now: Long): Int

    @Query("UPDATE category SET is_hidden = :hidden, updated_at = :now WHERE id = :id")
    suspend fun setHidden(id: String, hidden: Boolean, now: Long): Int

    @Query("DELETE FROM category WHERE id = :id")
    suspend fun delete(id: String): Int

    @Query("SELECT * FROM category_translation WHERE locale IN (:locales)")
    fun observeTranslations(locales: List<String>): Flow<List<CategoryTranslationEntity>>

    @Query("SELECT * FROM category_translation WHERE locale IN (:locales)")
    suspend fun getTranslations(locales: List<String>): List<CategoryTranslationEntity>

    @Query("SELECT * FROM category_translation WHERE canonical_key IN (:keys) AND locale IN (:locales)")
    suspend fun getTranslationsForKeys(keys: List<String>, locales: List<String>): List<CategoryTranslationEntity>
}

@Dao
interface MasterItemDao {

    @Insert
    suspend fun insert(item: MasterItemEntity)

    @Query("SELECT * FROM master_item WHERE id = :id")
    suspend fun getById(id: String): MasterItemEntity?

    @Query("SELECT * FROM master_item WHERE category_id = :categoryId AND is_hidden = 0")
    fun observeVisibleInCategory(categoryId: String): Flow<List<MasterItemEntity>>

    @Query("SELECT * FROM master_item WHERE category_id = :categoryId")
    suspend fun getInCategory(categoryId: String): List<MasterItemEntity>

    @Query(
        """
        SELECT t.* FROM master_item_translation t
        WHERE t.locale IN (:locales)
          AND t.canonical_key IN (SELECT m.canonical_key FROM master_item m WHERE m.category_id = :categoryId)
        """,
    )
    fun observeTranslationsForCategory(categoryId: String, locales: List<String>): Flow<List<MasterItemTranslationEntity>>

    @Query("SELECT * FROM master_item_translation WHERE canonical_key IN (:keys) AND locale IN (:locales)")
    suspend fun getTranslations(keys: List<String>, locales: List<String>): List<MasterItemTranslationEntity>

    /** One increment per occurrence, so adding the same suggestion twice counts twice. */
    @Query("UPDATE master_item SET use_count = use_count + 1 WHERE id = :id")
    suspend fun incrementUseCount(id: String)

    @Query("UPDATE master_item SET is_hidden = :hidden, updated_at = :now WHERE id = :id")
    suspend fun setHidden(id: String, hidden: Boolean, now: Long): Int

    /**
     * FTS prefix search. [match] is a sanitized MATCH expression ("ri* fl*"). Rows in [locales] and
     * custom names in any language are searched; hidden items, and without [categoryId] items of
     * hidden categories, are skipped. An item can appear once per matching row.
     */
    @Query(
        """
        SELECT m.*, item_search_fts.text AS match_text
        FROM item_search_fts
        INNER JOIN master_item m ON m.id = item_search_fts.ref_id
        WHERE item_search_fts MATCH :match
          AND (item_search_fts.locale IN (:locales) OR item_search_fts.ref_type = 'CUSTOM')
          AND m.is_hidden = 0
          AND ((:categoryId IS NOT NULL AND m.category_id = :categoryId)
               OR (:categoryId IS NULL
                   AND m.category_id IN (SELECT c.id FROM category c WHERE c.is_hidden = 0)))
        """,
    )
    suspend fun search(match: String, locales: List<String>, categoryId: String?): List<MasterItemMatch>

    /** Suggestions for an empty query: most used first. */
    @Query(
        """
        SELECT m.* FROM master_item m
        WHERE m.is_hidden = 0
          AND ((:categoryId IS NOT NULL AND m.category_id = :categoryId)
               OR (:categoryId IS NULL
                   AND m.category_id IN (SELECT c.id FROM category c WHERE c.is_hidden = 0)))
        ORDER BY m.use_count DESC
        LIMIT :limit
        """,
    )
    suspend fun mostUsed(categoryId: String?, limit: Int): List<MasterItemEntity>
}

/** Writes to the derived FTS index; reads go through [MasterItemDao.search]. */
@Dao
interface SearchIndexDao {

    @Insert
    suspend fun insertAll(rows: List<ItemSearchFtsEntity>)

    @Query("DELETE FROM item_search_fts WHERE ref_id = :masterItemId")
    suspend fun deleteForItem(masterItemId: String)

    @Query("DELETE FROM item_search_fts WHERE category_id = :categoryId")
    suspend fun deleteForCategory(categoryId: String)

    // FTS tables hide rowid from "*", so it is selected explicitly.
    @Query("SELECT rowid, * FROM item_search_fts WHERE ref_id = :masterItemId")
    suspend fun getForItem(masterItemId: String): List<ItemSearchFtsEntity>

    @Query("SELECT COUNT(*) FROM item_search_fts")
    suspend fun count(): Int
}

@Dao
interface UnitDao {

    @Insert
    suspend fun insert(unit: UnitDefEntity)

    @Query("SELECT * FROM unit_def ORDER BY sort_order, code")
    fun observeAll(): Flow<List<UnitDefEntity>>

    @Query("SELECT * FROM unit_def WHERE code = :code")
    suspend fun getByCode(code: String): UnitDefEntity?

    @Query("SELECT MAX(sort_order) FROM unit_def")
    suspend fun maxSortOrder(): Int?
}

@Dao
interface SeedMetaDao {

    @Query("SELECT seed_version FROM seed_meta WHERE id = ${SeedMetaEntity.SINGLE_ROW_ID}")
    suspend fun seedVersion(): Int?
}
