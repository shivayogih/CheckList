package com.dataloom.checklist.domain.model

/**
 * A reusable category such as Groceries. Seeded categories have a [canonicalKey] and translated
 * names; user categories have a [customName]. [displayName] is already resolved for the locale the
 * repository was asked for (custom name > translation > English > humanized key).
 */
data class Category(
    val id: CategoryId,
    val canonicalKey: String?,
    val customName: String?,
    val displayName: String,
    val iconKey: String,
    val isCustom: Boolean,
    val isHidden: Boolean,
)

/**
 * A reusable item suggestion (template) inside one category. Choosing it creates a
 * [ChecklistItem] snapshot; later edits here never change existing checklists (ADR-006).
 */
data class MasterItem(
    val id: MasterItemId,
    val categoryId: CategoryId,
    /** "rice" for seeded items, "custom:<uuid>" for user items. Stable across languages. */
    val canonicalKey: String,
    val customName: String?,
    val displayName: String,
    val defaultUnit: UnitCode?,
    val isCustom: Boolean,
    val isHidden: Boolean,
    val useCount: Int,
)
