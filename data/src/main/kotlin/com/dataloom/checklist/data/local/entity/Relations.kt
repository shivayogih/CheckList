package com.dataloom.checklist.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Relation

/** Checklist aggregate loaded by one @Transaction query: checklist -> sections -> category + items. */
data class ChecklistWithSections(
    @Embedded val checklist: ChecklistEntity,
    @Relation(
        entity = ChecklistCategoryEntity::class,
        parentColumn = "id",
        entityColumn = "checklist_id",
    )
    val sections: List<SectionWithItems>,
)

data class SectionWithItems(
    @Embedded val section: ChecklistCategoryEntity,
    @Relation(parentColumn = "category_id", entityColumn = "id")
    val category: CategoryEntity,
    @Relation(parentColumn = "id", entityColumn = "checklist_category_id")
    val items: List<ChecklistItemEntity>,
)

/** Home row: counts are computed in SQL so the list never loads every item. */
data class ChecklistSummaryRow(
    @Embedded val checklist: ChecklistEntity,
    @ColumnInfo(name = "total_items") val totalItems: Int,
    @ColumnInfo(name = "completed_items") val completedItems: Int,
)

/** A category with the number of checklists using it, for "most used first" ordering. */
data class CategoryWithUsage(
    @Embedded val category: CategoryEntity,
    @ColumnInfo(name = "usage_count") val usageCount: Int,
)

/** One FTS hit: the item and the normalized text of the row that matched. */
data class MasterItemMatch(
    @Embedded val item: MasterItemEntity,
    @ColumnInfo(name = "match_text") val matchText: String,
)
