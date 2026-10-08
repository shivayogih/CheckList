package com.dataloom.checklist.ai

import com.dataloom.checklist.ai.executor.ActionPlanExecutor
import com.dataloom.checklist.ai.executor.ExecutionReport
import com.dataloom.checklist.ai.mapper.PlanValidator
import com.dataloom.checklist.ai.model.ActionPlan
import com.dataloom.checklist.ai.model.AiResult
import com.dataloom.checklist.ai.model.ContextSnapshot
import com.dataloom.checklist.ai.model.GenerateRequest
import com.dataloom.checklist.ai.model.Utterance
import com.dataloom.checklist.ai.policy.AiSettingsSource
import com.dataloom.checklist.ai.policy.ConfirmationPolicy
import com.dataloom.checklist.ai.policy.ConfirmedPlan
import com.dataloom.checklist.ai.policy.PlanGate
import com.dataloom.checklist.ai.policy.ReviewedPlan
import com.dataloom.checklist.ai.service.AIService
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.SectionId
import com.dataloom.checklist.domain.usecase.ObserveChecklistDetailUseCase
import com.dataloom.checklist.domain.usecase.ObserveUnitsUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * The one entry point a ViewModel needs (section 11.2): ask the [AIService] for a plan, validate it,
 * decide what confirmation it needs, and execute it once confirmed.
 *
 * ```text
 * snapshot() -> interpret()/generate() -> ReviewedPlan -- user confirms --> confirm()     -> execute()
 *                                                      `- policy allows --> autoApprove() -'
 * ```
 * Nothing is written before [execute], and [execute] only accepts a [ConfirmedPlan].
 */
class AiAssistant @Inject constructor(
    private val service: AIService,
    private val validator: PlanValidator,
    private val policy: ConfirmationPolicy,
    private val executor: ActionPlanExecutor,
    private val settings: AiSettingsSource,
    private val observeChecklist: ObserveChecklistDetailUseCase,
    private val observeUnits: ObserveUnitsUseCase,
) {

    /** The minimized context of the open checklist ([checklistId] null on Home). */
    suspend fun snapshot(checklistId: ChecklistId?, locale: String, focusedSectionId: SectionId? = null): ContextSnapshot {
        val units = observeUnits().first().map { it.code }
        val detail = checklistId?.let { observeChecklist(it, locale).first() }
        return ContextSnapshot.of(detail, locale, units, focusedSectionId)
    }

    suspend fun interpret(utterance: Utterance, snapshot: ContextSnapshot): AiResult<ReviewedPlan> =
        service.interpret(utterance, snapshot.context).toReviewed(snapshot)

    suspend fun generate(prompt: String, snapshot: ContextSnapshot): AiResult<ReviewedPlan> =
        service.generateChecklist(GenerateRequest(prompt, snapshot.context.locale, snapshot.context.allowedUnits)).toReviewed(snapshot)

    /** Validates a plan from any source (for example an online model's function calls). */
    suspend fun review(plan: ActionPlan, snapshot: ContextSnapshot): ReviewedPlan {
        val validated = validator.validate(plan, snapshot)
        return ReviewedPlan(validated, policy.requirementFor(validated, settings.current()))
    }

    /** The user tapped Confirm on [plan], possibly with some steps unticked. */
    fun confirm(plan: ReviewedPlan, excludedCallIndexes: Set<Int> = emptySet()): ConfirmedPlan =
        PlanGate.confirm(plan, excludedCallIndexes)

    /** Non-null only when [plan] may run without asking (one simple step, and the user allowed it). */
    fun autoApprove(plan: ReviewedPlan): ConfirmedPlan? = PlanGate.autoApprove(plan)

    suspend fun execute(plan: ConfirmedPlan): ExecutionReport = executor.execute(plan)

    private suspend fun AiResult<ActionPlan>.toReviewed(snapshot: ContextSnapshot): AiResult<ReviewedPlan> = when (this) {
        is AiResult.Success -> AiResult.Success(review(value, snapshot))
        is AiResult.Unavailable -> this
        is AiResult.Rejected -> this
        is AiResult.Error -> this
    }
}
