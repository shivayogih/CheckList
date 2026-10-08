package com.dataloom.checklist.ai.policy

import com.dataloom.checklist.ai.mapper.ChecklistTarget
import com.dataloom.checklist.ai.mapper.PlannedOperation
import com.dataloom.checklist.ai.mapper.ValidatedPlan
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A validated plan plus what the user must do before it runs. Only the AI layer can create one
 * (internal constructor), so every plan shown for review has passed [com.dataloom.checklist.ai.mapper.PlanValidator].
 * It cannot be executed: execution needs a [ConfirmedPlan].
 */
class ReviewedPlan internal constructor(
    val plan: ValidatedPlan,
    val requirement: ConfirmationRequirement,
) {
    val operations: List<PlannedOperation> get() = plan.operations

    /** True when nothing can be executed (everything was rejected or not understood). */
    val isEmpty: Boolean get() = plan.operations.isEmpty()
}

/**
 * The only input [com.dataloom.checklist.ai.executor.ActionPlanExecutor] accepts. It is created by
 * [PlanGate.confirm] (the user tapped Confirm) or [PlanGate.autoApprove] (policy allows it), never
 * directly, and can be executed once: a second attempt fails instead of adding items twice.
 */
class ConfirmedPlan internal constructor(
    val operations: List<PlannedOperation>,
    internal val locale: String,
) {
    private val consumed = AtomicBoolean(false)

    internal fun consume() {
        check(consumed.compareAndSet(false, true)) { "A confirmed plan can be executed only once" }
    }
}

/** Turns a [ReviewedPlan] into a [ConfirmedPlan]: the single place where "the user said yes" is recorded. */
object PlanGate {

    /**
     * The user confirmed [plan], optionally unticking some steps ([excludedCallIndexes], matching
     * [PlannedOperation.callIndex]). Unticking createChecklist drops every step that writes into it.
     *
     * @throws IllegalArgumentException when nothing is left to run.
     */
    fun confirm(plan: ReviewedPlan, excludedCallIndexes: Set<Int> = emptySet()): ConfirmedPlan {
        var operations = plan.operations.filterNot { it.callIndex in excludedCallIndexes }
        if (plan.operations.any { it is PlannedOperation.CreateChecklist } && operations.none { it is PlannedOperation.CreateChecklist }) {
            operations = operations.filterNot { it.writesIntoNewChecklist() }
        }
        require(operations.isNotEmpty()) { "Nothing to execute" }
        return ConfirmedPlan(operations, plan.plan.locale)
    }

    /** Non-null only when the policy allows running [plan] without asking. */
    fun autoApprove(plan: ReviewedPlan): ConfirmedPlan? =
        if (plan.requirement == ConfirmationRequirement.NONE && !plan.isEmpty) ConfirmedPlan(plan.operations, plan.plan.locale) else null

    private fun PlannedOperation.writesIntoNewChecklist(): Boolean = when (this) {
        is PlannedOperation.AddItem -> checklist == ChecklistTarget.CreatedInPlan
        is PlannedOperation.AddCategory -> checklist == ChecklistTarget.CreatedInPlan
        else -> false
    }
}
