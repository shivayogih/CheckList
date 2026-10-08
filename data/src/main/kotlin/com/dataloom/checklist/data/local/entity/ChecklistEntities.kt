package com.dataloom.checklist.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Schema v1, section 5.3. IDs are UUID text (ADR-003); times are epoch millis UTC.

@Entity(
    tableName = "checklist",
    indices = [Index(value = ["is_archived", "updated_at"])],
)
data class ChecklistEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "is_archived", defaultValue = "0") val isArchived: Boolean = false,
    @ColumnInfo(name = "archived_at") val archivedAt: Long? = null,
)

/**
 * One category placed in one checklist (a "section"). RESTRICT on the category keeps a category
 * from being deleted while any checklist still uses it; the UI offers "hide" instead (section 7).
 */
@Entity(
    tableName = "checklist_category",
    foreignKeys = [
        ForeignKey(
            entity = ChecklistEntity::class,
            parentColumns = ["id"],
            childColumns = ["checklist_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["checklist_id", "category_id"], unique = true),
        Index(value = ["category_id"]),
    ],
)
data class ChecklistCategoryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "checklist_id") val checklistId: String,
    @ColumnInfo(name = "category_id") val categoryId: String,
    /** Sparse (steps of 1000) so a move rewrites one row. */
    @ColumnInfo(name = "display_order") val displayOrder: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/**
 * The user's item: a snapshot of name, quantity and unit (ADR-006). [masterItemId] is only a
 * back-reference for ranking, so deleting the master item sets it to null instead of deleting this.
 */
@Entity(
    tableName = "checklist_item",
    foreignKeys = [
        ForeignKey(
            entity = ChecklistCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["checklist_category_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MasterItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["master_item_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = UnitDefEntity::class,
            parentColumns = ["code"],
            childColumns = ["unit_code"],
        ),
    ],
    indices = [
        Index(value = ["checklist_category_id", "position"]),
        Index(value = ["master_item_id"]),
        Index(value = ["unit_code"]),
    ],
)
data class ChecklistItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "checklist_category_id") val checklistCategoryId: String,
    @ColumnInfo(name = "master_item_id") val masterItemId: String?,
    @ColumnInfo(name = "canonical_key") val canonicalKey: String?,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "display_name_locale") val displayNameLocale: String,
    /** Amount x 1000 (ADR-004). */
    @ColumnInfo(name = "quantity_milli") val quantityMilli: Long?,
    @ColumnInfo(name = "unit_code") val unitCode: String?,
    val notes: String?,
    @ColumnInfo(name = "is_completed") val isCompleted: Boolean,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    /** Sparse (steps of 1000) so a move rewrites one row. */
    val position: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
