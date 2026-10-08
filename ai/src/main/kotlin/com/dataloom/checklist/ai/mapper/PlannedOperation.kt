package com.dataloom.checklist.ai.mapper

import com.dataloom.checklist.ai.model.PlanSource
import com.dataloom.checklist.ai.model.ToolCall
import com.dataloom.checklist.ai.model.UnresolvedFragment
import com.dataloom.checklist.ai.tools.AiTool
import com.dataloom.checklist.domain.model.Category
import com.dataloom.checklist.domain.model.CategoryId
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.ChecklistItemId
import com.dataloom.checklist.domain.model.MasterItem
import com.dataloom.checklist.domain.model.Quantity
import com.dataloom.checklist.domain.model.SectionId
import com.dataloom.checklist.domain.model.UnitCode
import com.dataloom.checklist.domain.validation.ValidationError

/** Which checklist an operation writes to. */
sealed interface ChecklistTarget {
    data class Existing(val id: ChecklistId) : ChecklistTarget

    /** The checklist the plan's first call (createChecklist) creates. */
    data object CreatedInPlan : ChecklistTarget
}

/** Where an item goes: an existing section, or the section of a category that is added first. */
sealed interface SectionTarget {
    data class Existing(val sectionId: SectionId) : SectionTarget

    data class ForCategory(val categoryId: CategoryId) : SectionTarget
}

/**
 * A tool call that passed validation, with refs and keys resolved to real IDs and catalog entries.
 * Display fields ([AddItem.name], [AddItem.sectionName]...) let the review screen describe each step
 * ("Add 2 kg Rice to Groceries") without another lookup. [callIndex] points back to the [ToolCall].
 */
sealed interface PlannedOperation {
    val tool: AiTool
    val callIndex: Int

    data class CreateChecklist(
        override val callIndex: Int,
        val title: String,
        val description: String?,
        val categories: List<Category>,
    ) : PlannedOperation {
        override val tool: AiTool get() = AiTool.CREATE_CHECKLIST
    }

    data class AddCategory(
        override val callIndex: Int,
        val checklist: ChecklistTarget,
        val category: Category,
    ) : PlannedOperation {
        override val tool: AiTool get() = AiTool.ADD_CATEGORY
    }

    data class AddItem(
        override val callIndex: Int,
        val checklist: ChecklistTarget,
        val section: SectionTarget,
        val sectionName: String,
        /** Null for a typed (custom) item. */
        val masterItem: MasterItem?,
        val name: String,
        val quantity: Quantity?,
        val unit: UnitCode?,
    ) : PlannedOperation {
        override val tool: AiTool get() = AiTool.ADD_CHECKLIST_ITEM
    }

    data class UpdateQuantity(
        override val callIndex: Int,
        val checklistId: ChecklistId,
        val itemId: ChecklistItemId,
        val itemName: String,
        val quantity: Quantity,
        /** Null keeps the item's current unit. */
        val unit: UnitCode?,
    ) : PlannedOperation {
        override val tool: AiTool get() = AiTool.UPDATE_ITEM_QUANTITY
    }

    data class UpdateUnit(
        override val callIndex: Int,
        val checklistId: ChecklistId,
        val itemId: ChecklistItemId,
        val itemName: String,
        val unit: UnitCode,
    ) : PlannedOperation {
        override val tool: AiTool get() = AiTool.UPDATE_ITEM_UNIT
    }

    data class SetCompleted(
        override val callIndex: Int,
        val itemId: ChecklistItemId,
        val itemName: String,
        val completed: Boolean,
    ) : PlannedOperation {
        override val tool: AiTool get() = if (completed) AiTool.COMPLETE_ITEM else AiTool.UNCOMPLETE_ITEM
    }

    data class RemoveSection(
        override val callIndex: Int,
        val sectionId: SectionId,
        val sectionName: String,
    ) : PlannedOperation {
        override val tool: AiTool get() = AiTool.REMOVE_CATEGORY
    }

    data class DeleteItem(
        override val callIndex: Int,
        val itemId: ChecklistItemId,
        val itemName: String,
    ) : PlannedOperation {
        override val tool: AiTool get() = AiTool.DELETE_ITEM
    }
}

/** Why a proposed call was refused. The review screen maps each to a string resource. */
sealed interface ToolProblem {
    data object UnknownTool : ToolProblem

    /** Read tools answer the model inside its loop; they are never plan steps. */
    data object NotAPlanStep : ToolProblem

    data class MissingArgument(val argument: String) : ToolProblem

    data class UnexpectedArgument(val argument: String) : ToolProblem

    data class WrongType(val argument: String) : ToolProblem

    /** A section or item ref that is not in this request's context. */
    data class UnknownRef(val argument: String) : ToolProblem

    /** Not a built-in or existing custom unit; never created silently. */
    data object UnknownUnit : ToolProblem

    /** Not a number, not positive, above 99,999 or more than three decimals. */
    data object InvalidQuantity : ToolProblem

    data object UnknownCategory : ToolProblem

    data object CategoryAlreadyInChecklist : ToolProblem

    /** The call needs a checklist and there is none (none open, or the plan's createChecklist failed). */
    data object NoChecklist : ToolProblem

    data object CreateChecklistNotFirst : ToolProblem

    /** A typed item with no section to go to and no "Other" category to fall back on. */
    data object NoSectionForItem : ToolProblem

    /** The same business rules the UI enforces (title length, whole-number units...). */
    data class Invalid(val errors: List<ValidationError>) : ToolProblem
}

data class RejectedCall(val callIndex: Int, val call: ToolCall, val problems: List<ToolProblem>)

/** Result of validating an [com.dataloom.checklist.ai.model.ActionPlan] against the device's data. */
data class ValidatedPlan(
    val operations: List<PlannedOperation>,
    val rejected: List<RejectedCall>,
    val unresolved: List<UnresolvedFragment>,
    val source: PlanSource,
    val locale: String,
)
