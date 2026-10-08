package com.dataloom.checklist.ai.policy

import com.dataloom.checklist.ai.mapper.PlannedOperation
import com.dataloom.checklist.ai.mapper.SectionTarget
import com.dataloom.checklist.ai.mapper.ValidatedPlan
import com.dataloom.checklist.ai.tools.RiskClass
import javax.inject.Inject

/** User choices about AI (Settings). AI is off by default (section 11.1). */
data class AiSettings(
    val enabled: Boolean = false,
    /** Online AI sends checklist text to a provider, so it is a separate opt-in. */
    val onlineEnabled: Boolean = false,
    /** "Let AI add items without asking": only single, simple, fully understood commands. */
    val autoExecuteSimple: Boolean = false,
)

/**
 * Where [AiSettings] come from. The app binds an implementation backed by its settings store; until it
 * does, [DISABLED] applies and every AI call answers "unavailable".
 */
fun interface AiSettingsSource {
    suspend fun current(): AiSettings

    companion object {
        val DISABLED = AiSettingsSource { AiSettings() }
    }
}

enum class ConfirmationRequirement {
    /** Runs immediately; only possible for one simple step when the user allowed it. */
    NONE,

    /** A short "Add 2 kg Rice?" confirm. */
    CONFIRM,

    /** The full review screen: every step listed, each can be unticked. */
    REVIEW,
}

/**
 * Section 13: Read needs no confirmation; one Create or Modify step is confirmed by default and runs
 * without asking only when the user enabled it; more than one step, any destructive step, a new
 * checklist, or a command that was only partly understood is always reviewed.
 */
class ConfirmationPolicy @Inject constructor() {

    fun requirementFor(plan: ValidatedPlan, settings: AiSettings): ConfirmationRequirement {
        val operations = plan.operations
        val partlyUnderstood = plan.rejected.isNotEmpty() || plan.unresolved.isNotEmpty()
        return when {
            operations.size != 1 || partlyUnderstood -> ConfirmationRequirement.REVIEW
            operations.single().needsReview() -> ConfirmationRequirement.REVIEW
            settings.autoExecuteSimple -> ConfirmationRequirement.NONE
            else -> ConfirmationRequirement.CONFIRM
        }
    }

    private fun PlannedOperation.needsReview(): Boolean =
        tool.risk == RiskClass.DESTRUCTIVE || this is PlannedOperation.CreateChecklist ||
            // An item whose category section is created on the fly changes two things.
            (this is PlannedOperation.AddItem && section is SectionTarget.ForCategory)
}
