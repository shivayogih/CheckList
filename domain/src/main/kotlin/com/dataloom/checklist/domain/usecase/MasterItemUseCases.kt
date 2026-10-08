package com.dataloom.checklist.domain.usecase

import com.dataloom.checklist.domain.model.CategoryId
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.ChecklistItem
import com.dataloom.checklist.domain.model.ChecklistItemId
import com.dataloom.checklist.domain.model.MasterItem
import com.dataloom.checklist.domain.model.NewChecklistItem
import com.dataloom.checklist.domain.model.Quantity
import com.dataloom.checklist.domain.model.SectionId
import com.dataloom.checklist.domain.model.UnitCode
import com.dataloom.checklist.domain.repository.CatalogRepository
import com.dataloom.checklist.domain.repository.ChecklistRepository
import com.dataloom.checklist.domain.validation.ValidationResult
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ObserveMasterItemsUseCase @Inject constructor(private val catalog: CatalogRepository) {
    /** Visible suggestions of one category, most used first. */
    operator fun invoke(categoryId: CategoryId, locale: String): Flow<List<MasterItem>> =
        catalog.observeMasterItems(categoryId, locale)
}

/**
 * Offline item search. The query is trimmed. A blank query returns the category's suggestions
 * (most used first) when [categoryId] is given, so the picker never shows an empty screen, and an
 * empty list for an all-category search, where "everything" is not a useful answer.
 */
class SearchMasterItemsUseCase @Inject constructor(private val catalog: CatalogRepository) {
    suspend operator fun invoke(
        query: String,
        locale: String,
        categoryId: CategoryId? = null,
        limit: Int = DEFAULT_LIMIT,
    ): List<MasterItem> {
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) return catalog.searchMasterItems(trimmed, locale, categoryId, limit)
        if (categoryId == null) return emptyList()
        return catalog.observeMasterItems(categoryId, locale).first().take(limit)
    }

    companion object {
        const val DEFAULT_LIMIT = 50
    }
}

/** One master item picked in the multi-select picker, with an optional amount. */
data class MasterItemSelection(
    val masterItem: MasterItem,
    val quantity: Quantity? = null,
    /** Overrides the master item's default unit. */
    val unit: UnitCode? = null,
)

/**
 * [alreadyPresent] are the existing section items for selections that were skipped because the same
 * master item is already there, so the UI can offer "Increase quantity instead?".
 */
data class AddItemsOutcome(
    val addedItemIds: List<ChecklistItemId>,
    val alreadyPresent: List<ChecklistItem>,
)

/**
 * Adds picked master items to a section as snapshots (ADR-006): the resolved display name, its
 * locale, the canonical key and the unit are copied, so renaming the master item later never changes
 * this checklist. The default unit is copied only when an amount is given, because a unit without an
 * amount is invalid. Validation is all-or-nothing: one bad selection adds nothing.
 */
class AddMasterItemsToSectionUseCase @Inject constructor(
    private val checklists: ChecklistRepository,
    private val catalog: CatalogRepository,
) {
    suspend operator fun invoke(
        checklistId: ChecklistId,
        sectionId: SectionId,
        selections: List<MasterItemSelection>,
        locale: String,
    ): DomainResult<AddItemsOutcome> {
        val section = checklists.findSection(checklistId, sectionId) ?: return failure(DomainError.NotFound)
        val existingByMaster = section.items.filter { it.masterItemId != null }.associateBy { it.masterItemId }

        val alreadyPresent = mutableListOf<ChecklistItem>()
        val newItems = mutableListOf<NewChecklistItem>()
        for (selection in selections.distinctBy { it.masterItem.id }) {
            val master = selection.masterItem
            val existing = existingByMaster[master.id]
            if (existing != null) {
                alreadyPresent += existing
                continue
            }
            val unit = selection.unit ?: master.defaultUnit.takeIf { selection.quantity != null }
            val fields = when (val result = catalog.validateItem(master.displayName, selection.quantity, unit, notes = null)) {
                is ValidationResult.Invalid -> return failure(DomainError.InvalidSelection(master.id, result.errors))
                is ValidationResult.Valid -> result.value
            }
            newItems += NewChecklistItem(
                masterItemId = master.id,
                canonicalKey = master.canonicalKey,
                displayName = fields.name,
                displayNameLocale = locale,
                quantity = fields.quantity,
                unit = fields.unit,
                notes = null,
            )
        }
        val addedIds = if (newItems.isEmpty()) emptyList() else checklists.addItems(sectionId, newItems)
        return success(AddItemsOutcome(addedIds, alreadyPresent))
    }
}
