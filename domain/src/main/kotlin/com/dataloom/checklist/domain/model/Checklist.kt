package com.dataloom.checklist.domain.model

/** Times are epoch milliseconds, UTC. */
data class Checklist(
    val id: ChecklistId,
    val title: String,
    val description: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val isArchived: Boolean,
)

/** Row on the Home screen. */
data class ChecklistSummary(
    val checklist: Checklist,
    val totalItems: Int,
    val completedItems: Int,
) {
    val progress: Float get() = progressOf(completedItems, totalItems)
}

/** A checklist with its sections and items, ordered for display. */
data class ChecklistDetail(
    val checklist: Checklist,
    val sections: List<ChecklistSection>,
) {
    val totalItems: Int get() = sections.sumOf { it.items.size }
    val completedItems: Int get() = sections.sumOf { section -> section.items.count { it.isCompleted } }
    val progress: Float get() = progressOf(completedItems, totalItems)
}

/** One category placed in one checklist. */
data class ChecklistSection(
    val id: SectionId,
    val checklistId: ChecklistId,
    val category: Category,
    val displayOrder: Int,
    val items: List<ChecklistItem>,
)

/** The user's actual item: a snapshot, independent of the master item it may have come from. */
data class ChecklistItem(
    val id: ChecklistItemId,
    val sectionId: SectionId,
    val masterItemId: MasterItemId?,
    val canonicalKey: String?,
    val displayName: String,
    /** Language [displayName] was written in, e.g. "kn". */
    val displayNameLocale: String,
    val quantity: Quantity?,
    val unit: UnitCode?,
    val notes: String?,
    val isCompleted: Boolean,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

internal fun progressOf(completed: Int, total: Int): Float =
    if (total == 0) 0f else completed.toFloat() / total
