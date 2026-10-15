package com.dataloom.checklist.ai.draft

import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.usecase.AddCustomItemUseCase
import com.dataloom.checklist.domain.usecase.AddMasterItemsToSectionUseCase
import com.dataloom.checklist.domain.usecase.CreateChecklistUseCase
import com.dataloom.checklist.domain.usecase.DeleteChecklistUseCase
import com.dataloom.checklist.domain.usecase.DomainError
import com.dataloom.checklist.domain.usecase.DomainResult
import com.dataloom.checklist.domain.usecase.MasterItemSelection
import com.dataloom.checklist.domain.usecase.ObserveChecklistDetailUseCase
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Saves a [ChecklistDraft] the user approved, through the same domain use cases the screens use, so
 * every business rule is checked again at write time. Called only from the review screen's Approve
 * button: building or editing a draft never writes.
 *
 * All or nothing: if any step is refused or fails, the half-made checklist is deleted again.
 */
class ChecklistDraftSaver @Inject constructor(
    private val createChecklist: CreateChecklistUseCase,
    private val addMasterItems: AddMasterItemsToSectionUseCase,
    private val addCustomItem: AddCustomItemUseCase,
    private val observeChecklist: ObserveChecklistDetailUseCase,
    private val deleteChecklist: DeleteChecklistUseCase,
) {

    suspend fun save(draft: ChecklistDraft, locale: String): DomainResult<ChecklistId> {
        val sections = draft.sections.filter { it.items.isNotEmpty() }
        val created = createChecklist(draft.title, null, sections.map { it.category.id })
        val id = when (created) {
            is DomainResult.Failure -> return created
            is DomainResult.Success -> created.value.id
        }
        return try {
            val failure = fill(id, sections, locale)
            if (failure == null) {
                DomainResult.Success(id)
            } else {
                deleteChecklist(id)
                failure
            }
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) { deleteChecklist(id) }
            throw cancellation
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            deleteChecklist(id)
            throw failure
        }
    }

    private suspend fun fill(id: ChecklistId, sections: List<DraftSection>, locale: String): DomainResult.Failure? {
        val detail = observeChecklist(id, locale).first() ?: return DomainResult.Failure(DomainError.NotFound)
        for (section in sections) {
            val sectionId = detail.sections.firstOrNull { it.category.id == section.category.id }?.id
                ?: return DomainResult.Failure(DomainError.NotFound)
            val selections = section.items.mapNotNull { item -> item.masterItem?.let { MasterItemSelection(it) } }
            if (selections.isNotEmpty()) {
                (addMasterItems(id, sectionId, selections, locale) as? DomainResult.Failure)?.let { return it }
            }
            for (item in section.items.filter { it.masterItem == null }) {
                (addCustomItem(id, sectionId, item.name, locale) as? DomainResult.Failure)?.let { return it }
            }
        }
        return null
    }
}
