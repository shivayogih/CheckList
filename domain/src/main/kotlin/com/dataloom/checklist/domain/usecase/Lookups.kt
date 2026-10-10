package com.dataloom.checklist.domain.usecase

import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.ChecklistSection
import com.dataloom.checklist.domain.model.Quantity
import com.dataloom.checklist.domain.model.SectionId
import com.dataloom.checklist.domain.model.UnitCode
import com.dataloom.checklist.domain.repository.CatalogRepository
import com.dataloom.checklist.domain.repository.ChecklistRepository
import com.dataloom.checklist.domain.validation.ItemFields
import com.dataloom.checklist.domain.validation.ItemValidator
import com.dataloom.checklist.domain.validation.ValidationError
import com.dataloom.checklist.domain.validation.ValidationResult
import kotlinx.coroutines.flow.first

/**
 * Locale for reads whose display names are never shown (existence, ids, flags). Any supported
 * locale works; English is always seeded.
 */
internal const val LOOKUP_LOCALE = "en"

/**
 * The repository has no "get section" call, so the current section is read from the checklist's
 * detail flow. Null when the checklist or the section no longer exists.
 */
internal suspend fun ChecklistRepository.findSection(
    checklistId: ChecklistId,
    sectionId: SectionId,
    locale: String = LOOKUP_LOCALE,
): ChecklistSection? = observeChecklist(checklistId, locale).first()?.sections?.firstOrNull { it.id == sectionId }

/** Resolves [unit] in the catalog, then applies the pure item rules; an unknown code is an error too. */
internal suspend fun CatalogRepository.validateItem(
    name: String,
    quantity: Quantity?,
    unit: UnitCode?,
    notes: String?,
): ValidationResult<ItemFields> {
    val unitDef = unit?.let { getUnit(it) }
    if (unit != null && unitDef == null) {
        val others = (ItemValidator.validate(name, quantity, null, notes) as? ValidationResult.Invalid)?.errors.orEmpty()
        return ValidationResult.Invalid(others + ValidationError.UNKNOWN_UNIT)
    }
    return ItemValidator.validate(name, quantity, unitDef, notes)
}
