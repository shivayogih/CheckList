package com.dataloom.checklist.ai.draft

import com.dataloom.checklist.domain.model.Category
import com.dataloom.checklist.domain.model.MasterItem

/**
 * A proposed checklist that exists only in memory until the user approves it on the review screen.
 * Nothing in the database changes while a draft is built, edited or dropped.
 */
data class ChecklistDraft(
    val title: String,
    val sections: List<DraftSection>,
) {
    val itemCount: Int get() = sections.sumOf { it.items.size }
}

data class DraftSection(val category: Category, val items: List<DraftItem>)

/** A catalog item ([masterItem] set) or a typed one; [name] is already in the app language. */
data class DraftItem(val name: String, val masterItem: MasterItem? = null) {
    /** Stable within a section, for list keys and edits. */
    val key: String get() = masterItem?.canonicalKey ?: "custom:${name.lowercase()}"
}
