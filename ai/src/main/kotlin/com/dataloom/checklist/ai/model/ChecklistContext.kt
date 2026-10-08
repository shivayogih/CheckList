package com.dataloom.checklist.ai.model

import com.dataloom.checklist.domain.model.CategoryId
import com.dataloom.checklist.domain.model.ChecklistDetail
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.ChecklistItemId
import com.dataloom.checklist.domain.model.Quantity
import com.dataloom.checklist.domain.model.SectionId
import com.dataloom.checklist.domain.model.UnitCode

/**
 * Everything an AI may see about the open checklist (section 11.4, context minimization): the title,
 * category keys or names, item keys or names with quantities and completion, the locale and the allowed
 * unit codes. Never profile data, never notes, never database IDs: sections and items are addressed by
 * short per-request refs ("s1", "i3") that only the device can map back.
 */
data class ChecklistContext(
    val locale: String,
    /** Null when no checklist is open (Home screen); only "create checklist" works then. */
    val title: String?,
    val sections: List<SectionContext>,
    val allowedUnits: List<UnitCode>,
    /** The section the user is looking at; custom items land here when no better section exists. */
    val focusedSectionRef: String? = null,
) {
    val hasChecklist: Boolean get() = title != null

    val items: List<ItemContext> get() = sections.flatMap { it.items }
}

data class SectionContext(
    val ref: String,
    /** Seeded category key ("groceries"); null for a user category, which only has [categoryName]. */
    val categoryKey: String?,
    val categoryName: String,
    val items: List<ItemContext>,
)

data class ItemContext(
    val ref: String,
    val sectionRef: String,
    /** Seeded key ("rice"); null for typed items. User keys ("custom:<uuid>") are never exposed. */
    val canonicalKey: String?,
    val name: String,
    val quantity: Quantity?,
    val unit: UnitCode?,
    val isCompleted: Boolean,
)

/**
 * A [ChecklistContext] plus the device-only map from refs to real IDs. The context may leave the device
 * (online AI); the snapshot never does. Built from the checklist the screen already observes.
 */
class ContextSnapshot private constructor(
    val context: ChecklistContext,
    internal val checklistId: ChecklistId?,
    internal val sections: Map<String, SectionTarget>,
    internal val items: Map<String, ItemTarget>,
) {
    internal data class SectionTarget(val sectionId: SectionId, val categoryId: CategoryId, val name: String)

    internal data class ItemTarget(val itemId: ChecklistItemId, val sectionRef: String, val name: String)

    companion object {
        private const val CUSTOM_KEY_PREFIX = "custom:"

        /**
         * [detail] is the open checklist or null on Home. [focusedSectionId] is the section the user is
         * adding to, if any. Refs are numbered in display order, so the same checklist gives the same refs.
         */
        fun of(
            detail: ChecklistDetail?,
            locale: String,
            allowedUnits: List<UnitCode>,
            focusedSectionId: SectionId? = null,
        ): ContextSnapshot {
            val sectionTargets = linkedMapOf<String, SectionTarget>()
            val itemTargets = linkedMapOf<String, ItemTarget>()
            var focusedRef: String? = null
            var itemNumber = 0
            val sections = detail?.sections.orEmpty().sortedBy { it.displayOrder }.mapIndexed { index, section ->
                val sectionRef = "s${index + 1}"
                sectionTargets[sectionRef] = SectionTarget(section.id, section.category.id, section.category.displayName)
                if (section.id == focusedSectionId) focusedRef = sectionRef
                val items = section.items.sortedBy { it.position }.map { item ->
                    val itemRef = "i${++itemNumber}"
                    itemTargets[itemRef] = ItemTarget(item.id, sectionRef, item.displayName)
                    ItemContext(
                        ref = itemRef,
                        sectionRef = sectionRef,
                        canonicalKey = item.canonicalKey?.takeUnless { it.startsWith(CUSTOM_KEY_PREFIX) },
                        name = item.displayName,
                        quantity = item.quantity,
                        unit = item.unit,
                        isCompleted = item.isCompleted,
                    )
                }
                SectionContext(sectionRef, section.category.canonicalKey, section.category.displayName, items)
            }
            val context = ChecklistContext(
                locale = locale,
                title = detail?.checklist?.title,
                sections = sections,
                allowedUnits = allowedUnits,
                focusedSectionRef = focusedRef,
            )
            return ContextSnapshot(context, detail?.checklist?.id, sectionTargets, itemTargets)
        }

        /** No checklist open. */
        fun empty(locale: String, allowedUnits: List<UnitCode>): ContextSnapshot = of(null, locale, allowedUnits)
    }
}
