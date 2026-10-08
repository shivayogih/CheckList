package com.dataloom.checklist.domain.model

/** Input for creating a checklist item, from a master item or typed by the user. Already validated. */
data class NewChecklistItem(
    val masterItemId: MasterItemId?,
    val canonicalKey: String?,
    val displayName: String,
    val displayNameLocale: String,
    val quantity: Quantity?,
    val unit: UnitCode?,
    val notes: String?,
)

/** Fields to change on an item; null means "leave unchanged" except where noted. */
data class ChecklistItemUpdate(
    val displayName: String? = null,
    val quantity: Quantity? = null,
    val unit: UnitCode? = null,
    val notes: String? = null,
    /** True clears quantity and unit. */
    val clearQuantity: Boolean = false,
    /** True clears notes. */
    val clearNotes: Boolean = false,
)

enum class ChecklistSort { RECENT, TITLE, PROGRESS }

enum class ChecklistFilter { ACTIVE, ARCHIVED, ALL }

data class ChecklistQuery(
    val search: String = "",
    val sort: ChecklistSort = ChecklistSort.RECENT,
    val filter: ChecklistFilter = ChecklistFilter.ACTIVE,
)
