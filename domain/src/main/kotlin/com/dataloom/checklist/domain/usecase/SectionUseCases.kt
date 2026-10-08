package com.dataloom.checklist.domain.usecase

import com.dataloom.checklist.domain.model.CategoryId
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.SectionId
import com.dataloom.checklist.domain.repository.ChecklistRepository
import com.dataloom.checklist.domain.validation.ValidationError
import javax.inject.Inject

/**
 * Appends categories as new sections. Categories already in the checklist are skipped by the
 * repository, so the returned list holds only the sections actually created.
 */
class AddCategoriesToChecklistUseCase @Inject constructor(private val checklists: ChecklistRepository) {
    suspend operator fun invoke(checklistId: ChecklistId, categoryIds: List<CategoryId>): DomainResult<List<SectionId>> {
        val distinct = categoryIds.distinct()
        if (distinct.isEmpty()) return success(emptyList())
        return success(checklists.addSections(checklistId, distinct))
    }
}

/** Removes the section and its items. Destructive: the UI (and the AI executor) confirm first. */
class RemoveSectionUseCase @Inject constructor(private val checklists: ChecklistRepository) {
    suspend operator fun invoke(sectionId: SectionId): DomainResult<Unit> = success(checklists.removeSection(sectionId))
}

/** [toIndex] is the 0-based target display position among the checklist's sections. */
class MoveSectionUseCase @Inject constructor(private val checklists: ChecklistRepository) {
    suspend operator fun invoke(sectionId: SectionId, toIndex: Int): DomainResult<Unit> {
        if (toIndex < 0) return failure(DomainError.Invalid(listOf(ValidationError.NEGATIVE_POSITION)))
        return success(checklists.moveSection(sectionId, toIndex))
    }
}
