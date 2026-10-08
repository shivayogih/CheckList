package com.dataloom.checklist.domain.usecase

import com.dataloom.checklist.domain.model.CategoryId
import com.dataloom.checklist.domain.model.ChecklistDetail
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.ChecklistQuery
import com.dataloom.checklist.domain.model.ChecklistSummary
import com.dataloom.checklist.domain.photo.NoPhotoFileCleaner
import com.dataloom.checklist.domain.photo.PhotoFileCleaner
import com.dataloom.checklist.domain.repository.ChecklistRepository
import com.dataloom.checklist.domain.validation.ChecklistValidator
import com.dataloom.checklist.domain.validation.ValidationResult
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * A saved checklist plus whether another checklist already had the same title. Duplicate titles are
 * allowed ("Groceries" every week), but the UI may want to mention it.
 */
data class SavedChecklist(val id: ChecklistId, val titleAlreadyUsed: Boolean)

class ObserveChecklistsUseCase @Inject constructor(private val checklists: ChecklistRepository) {
    /** Search text is trimmed so a trailing space from the keyboard does not hide results. */
    operator fun invoke(query: ChecklistQuery = ChecklistQuery()): Flow<List<ChecklistSummary>> =
        checklists.observeChecklists(query.copy(search = query.search.trim()))
}

class ObserveChecklistDetailUseCase @Inject constructor(private val checklists: ChecklistRepository) {
    /** Emits null once the checklist is deleted, so the screen can close itself. */
    operator fun invoke(id: ChecklistId, locale: String): Flow<ChecklistDetail?> = checklists.observeChecklist(id, locale)
}

class CreateChecklistUseCase @Inject constructor(private val checklists: ChecklistRepository) {
    /** [categoryIds] may be empty; repeated ids are dropped because a category appears once per list. */
    suspend operator fun invoke(
        title: String,
        description: String? = null,
        categoryIds: List<CategoryId> = emptyList(),
    ): DomainResult<SavedChecklist> {
        val fields = when (val result = ChecklistValidator.validate(title, description)) {
            is ValidationResult.Invalid -> return result.toFailure()
            is ValidationResult.Valid -> result.value
        }
        val titleUsed = checklists.titleExists(fields.title)
        val id = checklists.createChecklist(fields.title, fields.description, categoryIds.distinct())
        return success(SavedChecklist(id, titleUsed))
    }
}

/**
 * True when a checklist other than [excluding] already uses [title] (trimmed, case-insensitive).
 * Forms call it while the user types to show a gentle warning before saving; duplicate titles stay
 * allowed, so this never blocks a save. Blank titles return false (the validator reports those).
 *
 * Added in Phase 3 (CL-122), additive to the Phase 2 contract.
 */
class IsChecklistTitleUsedUseCase @Inject constructor(private val checklists: ChecklistRepository) {
    suspend operator fun invoke(title: String, excluding: ChecklistId? = null): Boolean {
        val clean = title.trim()
        if (clean.isEmpty()) return false
        return checklists.titleExists(clean, excluding)
    }
}

class UpdateChecklistUseCase @Inject constructor(private val checklists: ChecklistRepository) {
    suspend operator fun invoke(id: ChecklistId, title: String, description: String?): DomainResult<SavedChecklist> {
        val fields = when (val result = ChecklistValidator.validate(title, description)) {
            is ValidationResult.Invalid -> return result.toFailure()
            is ValidationResult.Valid -> result.value
        }
        val titleUsed = checklists.titleExists(fields.title, excluding = id)
        checklists.updateChecklist(id, fields.title, fields.description)
        return success(SavedChecklist(id, titleUsed))
    }
}

/** Archive is the gentle alternative to delete: the list leaves Home but stays searchable. */
class ArchiveChecklistUseCase @Inject constructor(private val checklists: ChecklistRepository) {
    suspend operator fun invoke(id: ChecklistId): DomainResult<Unit> = success(checklists.setArchived(id, archived = true))
}

class UnarchiveChecklistUseCase @Inject constructor(private val checklists: ChecklistRepository) {
    suspend operator fun invoke(id: ChecklistId): DomainResult<Unit> = success(checklists.setArchived(id, archived = false))
}

/** Hard delete; the confirmation dialog is the UI's job (section 4). Photo files go after the rows. */
class DeleteChecklistUseCase @Inject constructor(
    private val checklists: ChecklistRepository,
    private val photoFiles: PhotoFileCleaner = NoPhotoFileCleaner,
) {
    suspend operator fun invoke(id: ChecklistId): DomainResult<Unit> {
        val files = photoFiles.filesOfChecklist(id)
        checklists.deleteChecklist(id)
        files.run()
        return success(Unit)
    }
}

/**
 * Deep copy with every item un-ticked. [newTitle] comes from the UI because the "(copy)" suffix is
 * localized text, which the domain never builds.
 */
class DuplicateChecklistUseCase @Inject constructor(private val checklists: ChecklistRepository) {
    suspend operator fun invoke(id: ChecklistId, newTitle: String): DomainResult<SavedChecklist> {
        val title = when (val result = ChecklistValidator.validateTitle(newTitle)) {
            is ValidationResult.Invalid -> return result.toFailure()
            is ValidationResult.Valid -> result.value
        }
        val titleUsed = checklists.titleExists(title)
        val copyId = checklists.duplicateChecklist(id, title)
        return success(SavedChecklist(copyId, titleUsed))
    }
}
