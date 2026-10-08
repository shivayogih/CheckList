package com.dataloom.checklist.ai.executor

import com.dataloom.checklist.ai.mapper.ChecklistTarget
import com.dataloom.checklist.ai.mapper.PlannedOperation
import com.dataloom.checklist.ai.mapper.SectionTarget
import com.dataloom.checklist.ai.policy.ConfirmedPlan
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.ChecklistItemId
import com.dataloom.checklist.domain.model.SectionId
import com.dataloom.checklist.domain.usecase.AddCategoriesToChecklistUseCase
import com.dataloom.checklist.domain.usecase.AddCustomItemUseCase
import com.dataloom.checklist.domain.usecase.AddMasterItemsToSectionUseCase
import com.dataloom.checklist.domain.usecase.CreateChecklistUseCase
import com.dataloom.checklist.domain.usecase.CustomItemOutcome
import com.dataloom.checklist.domain.usecase.DeleteChecklistItemUseCase
import com.dataloom.checklist.domain.usecase.DomainError
import com.dataloom.checklist.domain.usecase.DomainResult
import com.dataloom.checklist.domain.usecase.MasterItemSelection
import com.dataloom.checklist.domain.usecase.ObserveChecklistDetailUseCase
import com.dataloom.checklist.domain.usecase.RemoveSectionUseCase
import com.dataloom.checklist.domain.usecase.SetItemCompletedUseCase
import com.dataloom.checklist.domain.usecase.UpdateChecklistItemUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.first

sealed interface OperationOutcome {
    data object Done : OperationOutcome

    /** The catalog item was already in the section; the UI can offer "Increase quantity instead?". */
    data object AlreadyPresent : OperationOutcome

    /** A use case refused the step (business rule, or the target was deleted meanwhile). */
    data class Failed(val error: DomainError) : OperationOutcome

    /** Not run because an earlier step failed. */
    data object Skipped : OperationOutcome
}

data class OperationResult(val operation: PlannedOperation, val outcome: OperationOutcome)

data class ExecutionReport(
    val results: List<OperationResult>,
    /** Set when the plan created a checklist, so the UI can open it. */
    val createdChecklistId: ChecklistId?,
) {
    val succeeded: Boolean get() = results.all { it.outcome == OperationOutcome.Done || it.outcome == OperationOutcome.AlreadyPresent }
}

/**
 * Runs a [ConfirmedPlan] step by step, only through the same domain use cases the UI calls, so every
 * business rule is checked again at write time (section 11.2). Steps run in order and stop at the first
 * refusal; later steps are reported as [OperationOutcome.Skipped]. Unexpected exceptions (I/O) propagate
 * to the caller, like any use case call.
 */
class ActionPlanExecutor @Inject constructor(
    private val createChecklist: CreateChecklistUseCase,
    private val addCategories: AddCategoriesToChecklistUseCase,
    private val addMasterItems: AddMasterItemsToSectionUseCase,
    private val addCustomItem: AddCustomItemUseCase,
    private val updateItem: UpdateChecklistItemUseCase,
    private val setItemCompleted: SetItemCompletedUseCase,
    private val removeSection: RemoveSectionUseCase,
    private val deleteItem: DeleteChecklistItemUseCase,
    private val observeChecklist: ObserveChecklistDetailUseCase,
) {

    suspend fun execute(plan: ConfirmedPlan): ExecutionReport {
        plan.consume()
        val run = Run(plan.locale)
        val results = mutableListOf<OperationResult>()
        var failed = false
        for (operation in plan.operations) {
            val outcome = if (failed) OperationOutcome.Skipped else run.execute(operation)
            if (outcome is OperationOutcome.Failed) failed = true
            results += OperationResult(operation, outcome)
        }
        return ExecutionReport(results, run.createdChecklistId)
    }

    private inner class Run(private val locale: String) {
        var createdChecklistId: ChecklistId? = null

        suspend fun execute(operation: PlannedOperation): OperationOutcome = when (operation) {
            is PlannedOperation.CreateChecklist ->
                createChecklist(operation.title, operation.description, operation.categories.map { it.id }).then { saved ->
                    createdChecklistId = saved.id
                    OperationOutcome.Done
                }
            is PlannedOperation.AddCategory -> {
                val checklistId = resolve(operation.checklist)
                if (checklistId == null) notFound() else addCategories(checklistId, listOf(operation.category.id)).done()
            }
            is PlannedOperation.AddItem -> addItem(operation)
            is PlannedOperation.UpdateQuantity -> {
                val current = currentItem(operation.checklistId, operation.itemId)
                if (current == null) {
                    notFound()
                } else {
                    updateItem(current.id, current.displayName, operation.quantity, operation.unit ?: current.unit, current.notes).done()
                }
            }
            is PlannedOperation.UpdateUnit -> {
                val current = currentItem(operation.checklistId, operation.itemId)
                if (current == null) notFound() else updateItem(current.id, current.displayName, current.quantity, operation.unit, current.notes).done()
            }
            is PlannedOperation.SetCompleted -> setItemCompleted(operation.itemId, operation.completed).done()
            is PlannedOperation.RemoveSection -> removeSection(operation.sectionId).done()
            is PlannedOperation.DeleteItem -> deleteItem(operation.itemId).done()
        }

        private suspend fun addItem(operation: PlannedOperation.AddItem): OperationOutcome {
            val checklistId = resolve(operation.checklist) ?: return notFound()
            val sectionId = sectionFor(checklistId, operation.section) ?: return notFound()
            val master = operation.masterItem
            return if (master != null) {
                val selection = MasterItemSelection(master, operation.quantity, operation.unit)
                addMasterItems(checklistId, sectionId, listOf(selection), locale).then { outcome ->
                    if (outcome.alreadyPresent.isNotEmpty()) OperationOutcome.AlreadyPresent else OperationOutcome.Done
                }
            } else {
                addCustomItem(checklistId, sectionId, operation.name, locale, operation.quantity, operation.unit).then { outcome ->
                    if (outcome is CustomItemOutcome.AlreadyPresent) OperationOutcome.AlreadyPresent else OperationOutcome.Done
                }
            }
        }

        private fun resolve(target: ChecklistTarget): ChecklistId? = when (target) {
            is ChecklistTarget.Existing -> target.id
            ChecklistTarget.CreatedInPlan -> createdChecklistId
        }

        /** Reads the checklist now, not at proposal time: an earlier step may have added the section. */
        private suspend fun sectionFor(checklistId: ChecklistId, target: SectionTarget): SectionId? = when (target) {
            is SectionTarget.Existing -> target.sectionId
            is SectionTarget.ForCategory -> {
                val detail = observeChecklist(checklistId, locale).first()
                detail?.sections?.firstOrNull { it.category.id == target.categoryId }?.id
                    ?: when (val added = addCategories(checklistId, listOf(target.categoryId))) {
                        is DomainResult.Success -> added.value.firstOrNull()
                        is DomainResult.Failure -> null
                    }
            }
        }

        /** The item editor's use case needs the full state; name and notes are read on the device. */
        private suspend fun currentItem(checklistId: ChecklistId, itemId: ChecklistItemId) =
            observeChecklist(checklistId, locale).first()?.sections?.flatMap { it.items }?.firstOrNull { it.id == itemId }

        private fun notFound(): OperationOutcome = OperationOutcome.Failed(DomainError.NotFound)
    }

    private inline fun <T> DomainResult<T>.then(onSuccess: (T) -> OperationOutcome): OperationOutcome = when (this) {
        is DomainResult.Success -> onSuccess(value)
        is DomainResult.Failure -> OperationOutcome.Failed(error)
    }

    private fun DomainResult<*>.done(): OperationOutcome = then { OperationOutcome.Done }
}
