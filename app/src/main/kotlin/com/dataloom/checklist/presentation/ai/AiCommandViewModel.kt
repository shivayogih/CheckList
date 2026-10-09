package com.dataloom.checklist.presentation.ai

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dataloom.checklist.R
import com.dataloom.checklist.ai.AiAssistant
import com.dataloom.checklist.ai.executor.ExecutionReport
import com.dataloom.checklist.ai.executor.OperationOutcome
import com.dataloom.checklist.ai.mapper.PlannedOperation
import com.dataloom.checklist.ai.model.AiResult
import com.dataloom.checklist.ai.model.RejectionReason
import com.dataloom.checklist.ai.model.UnavailableReason
import com.dataloom.checklist.ai.model.Utterance
import com.dataloom.checklist.ai.policy.ConfirmedPlan
import com.dataloom.checklist.ai.policy.ReviewedPlan
import com.dataloom.checklist.di.ApplicationScope
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.Quantity
import com.dataloom.checklist.domain.model.UnitCode
import com.dataloom.checklist.domain.validation.FieldLimits
import com.dataloom.checklist.domain.validation.InputText
import com.dataloom.checklist.localization.AppLanguageProvider
import com.dataloom.checklist.presentation.common.UiText
import com.dataloom.checklist.settings.AiPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What a proposed step does; the review sheet turns it into a translated sentence. */
enum class AiStepKind { CREATE_CHECKLIST, ADD_CATEGORY, ADD_ITEM, SET_QUANTITY, SET_UNIT, COMPLETE, UNCOMPLETE, REMOVE_SECTION, DELETE_ITEM }

/** One row of the review sheet. [callIndex] ties it back to the plan when the user unticks it. */
data class AiStepUi(
    val callIndex: Int,
    val kind: AiStepKind,
    /** Item, category, section or checklist name, as the user will see it in the list. */
    val name: String,
    val quantity: Quantity?,
    val unit: UnitCode?,
    /** Where an added item goes ("Groceries"); null for other steps. */
    val sectionName: String?,
    val checked: Boolean = true,
)

data class AiReviewUi(
    val steps: List<AiStepUi>,
    /** Parts of the command that produced no step, shown so nothing is dropped silently. */
    val notUnderstood: List<String>,
) {
    val canConfirm: Boolean get() = steps.any { it.checked }
}

/** A message under the command field; [isProblem] when something did not happen. */
data class AiMessageUi(val lines: List<UiText>, val isProblem: Boolean)

data class AiCommandUiState(
    /** The command field is shown only while the assistant is on in Settings. */
    val isAvailable: Boolean = false,
    val command: String = "",
    val isWorking: Boolean = false,
    /** Set while the review sheet is open. Nothing has been written yet. */
    val review: AiReviewUi? = null,
    val message: AiMessageUi? = null,
) {
    val canSubmit: Boolean get() = isAvailable && !isWorking && review == null && command.isNotBlank()
}

sealed interface AiCommandAction {
    data class CommandChanged(val text: String) : AiCommandAction
    data object Submit : AiCommandAction
    data class ToggleStep(val callIndex: Int, val checked: Boolean) : AiCommandAction

    /** Runs only the ticked steps. */
    data object Confirm : AiCommandAction

    /** Closes the review sheet; nothing is written. */
    data object Cancel : AiCommandAction
    data object DismissMessage : AiCommandAction
}

/**
 * The AI command field of the checklist detail screen (CL-240): the user types "2 kg rice and 1 litre
 * milk" in any app language, [AiAssistant] proposes a plan, the user reviews it step by step and only
 * the ticked steps run, through the confirmation gate and the same use cases the screens use.
 * A plan the user allowed to run without asking (Settings, one simple step) skips the sheet.
 */
@HiltViewModel(assistedFactory = AiCommandViewModel.Factory::class)
class AiCommandViewModel @AssistedInject constructor(
    @Assisted checklistId: String,
    private val assistant: AiAssistant,
    preferences: AiPreferences,
    private val languageProvider: AppLanguageProvider,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(checklistId: String): AiCommandViewModel
    }

    private val id = ChecklistId(checklistId)
    // The typed command survives process death. The review sheet and the plan behind it do not: nothing
    // has been written at that point, and a plan must never run from restored state the user did not see.
    private val state = MutableStateFlow(AiCommandUiState(command = savedState.get<String>(KEY_COMMAND).orEmpty()))

    /** The plan behind [AiCommandUiState.review]; dropped on Cancel so it can never run later. */
    private var proposed: ReviewedPlan? = null

    val uiState: StateFlow<AiCommandUiState> = combine(
        preferences.settings.map { it.enabled }.distinctUntilChanged(),
        state,
    ) { enabled, current -> current.copy(isAvailable = enabled) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), state.value)

    fun onAction(action: AiCommandAction) {
        when (action) {
            is AiCommandAction.CommandChanged -> {
                // Control and bidi characters never reach the parser; the length is the assistant's own limit.
                val text = InputText.forField(action.text, FieldLimits.AI_COMMAND_MAX, multiline = true)
                savedState[KEY_COMMAND] = text
                state.update { it.copy(command = text) }
            }
            AiCommandAction.Submit -> submit()
            is AiCommandAction.ToggleStep -> state.update { current ->
                val review = current.review ?: return@update current
                current.copy(
                    review = review.copy(
                        steps = review.steps.map { if (it.callIndex == action.callIndex) it.copy(checked = action.checked) else it },
                    ),
                )
            }
            AiCommandAction.Confirm -> confirm()
            AiCommandAction.Cancel -> {
                proposed = null
                state.update { it.copy(review = null) }
            }
            AiCommandAction.DismissMessage -> state.update { it.copy(message = null) }
        }
    }

    private fun submit() {
        val current = state.value
        if (current.isWorking || current.review != null) return
        val text = current.command.trim()
        if (text.isEmpty()) {
            state.update { it.copy(message = problem(UiText(R.string.ai_empty_command))) }
            return
        }
        state.update { it.copy(isWorking = true, message = null) }
        viewModelScope.launch {
            val locale = languageProvider.language.value
            val result = guarded { assistant.interpret(Utterance(text, locale), assistant.snapshot(id, locale)) }
            when (result) {
                is AiResult.Success -> propose(result.value)
                is AiResult.Unavailable -> finish(problem(result.reason.toUiText()))
                is AiResult.Rejected -> finish(problem(result.reason.toUiText()))
                is AiResult.Error -> finish(problem(UiText(R.string.error_generic)))
            }
        }
    }

    private suspend fun propose(plan: ReviewedPlan) {
        if (plan.isEmpty) {
            finish(problem(UiText(R.string.ai_not_understood)))
            return
        }
        val approved = assistant.autoApprove(plan)
        if (approved != null) {
            execute(approved)
            return
        }
        proposed = plan
        state.update { it.copy(isWorking = false, review = plan.toReviewUi()) }
    }

    private fun confirm() {
        val plan = proposed ?: return
        val review = state.value.review ?: return
        if (!review.canConfirm || state.value.isWorking) return
        val excluded = review.steps.filterNot { it.checked }.map { it.callIndex }.toSet()
        proposed = null
        state.update { it.copy(review = null, isWorking = true) }
        val confirmed = try {
            assistant.confirm(plan, excluded)
        } catch (_: IllegalArgumentException) {
            // Unticking a new checklist also drops what goes into it; nothing may be left.
            finish(problem(UiText(R.string.ai_nothing_selected)))
            return
        }
        viewModelScope.launch { execute(confirmed) }
    }

    /**
     * Runs in the application scope: once confirmed, the plan finishes even if the screen closes,
     * so it is never left half done because of navigation.
     */
    private suspend fun execute(plan: ConfirmedPlan) {
        val report = guarded { AiResult.Success(applicationScope.async { assistant.execute(plan) }.await()) }
        when (report) {
            is AiResult.Success -> {
                savedState[KEY_COMMAND] = ""
                state.update { it.copy(isWorking = false, command = "", message = report.value.toMessage()) }
            }
            else -> finish(problem(UiText(R.string.error_generic)))
        }
    }

    private fun finish(message: AiMessageUi) {
        state.update { it.copy(isWorking = false, message = message) }
    }

    /** Storage failures surface as a message, never as a crash; cancellation still propagates. */
    private suspend fun <T> guarded(block: suspend () -> AiResult<T>): AiResult<T> =
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            AiResult.Error(failure)
        }

    internal companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val KEY_COMMAND = "ai_command"
    }
}

private fun problem(text: UiText) = AiMessageUi(listOf(text), isProblem = true)

internal fun UnavailableReason.toUiText(): UiText = when (this) {
    UnavailableReason.DISABLED -> UiText(R.string.ai_unavailable_disabled)
    UnavailableReason.TIMEOUT -> UiText(R.string.ai_unavailable_timeout)
    UnavailableReason.OFFLINE, UnavailableReason.QUOTA, UnavailableReason.NOT_SUPPORTED -> UiText(R.string.ai_unavailable_other)
}

internal fun RejectionReason.toUiText(): UiText = when (this) {
    RejectionReason.EMPTY_INPUT -> UiText(R.string.ai_empty_command)
    RejectionReason.TOO_LONG -> UiText(R.string.ai_too_long)
    RejectionReason.NOT_UNDERSTOOD, RejectionReason.NEEDS_CHECKLIST -> UiText(R.string.ai_not_understood)
}

internal fun ReviewedPlan.toReviewUi(): AiReviewUi = AiReviewUi(
    steps = operations.map { it.toStepUi() },
    notUnderstood = plan.unresolved.map { it.text } +
        plan.rejected.map { rejected ->
            (rejected.call.arguments["name"] ?: rejected.call.arguments["title"])?.toString() ?: rejected.call.name
        },
)

private fun PlannedOperation.toStepUi(): AiStepUi = when (this) {
    is PlannedOperation.CreateChecklist -> step(AiStepKind.CREATE_CHECKLIST, title)
    is PlannedOperation.AddCategory -> step(AiStepKind.ADD_CATEGORY, category.displayName)
    is PlannedOperation.AddItem -> step(AiStepKind.ADD_ITEM, name, quantity, unit, sectionName)
    is PlannedOperation.UpdateQuantity -> step(AiStepKind.SET_QUANTITY, itemName, quantity, unit)
    is PlannedOperation.UpdateUnit -> step(AiStepKind.SET_UNIT, itemName, unit = unit)
    is PlannedOperation.SetCompleted -> step(if (completed) AiStepKind.COMPLETE else AiStepKind.UNCOMPLETE, itemName)
    is PlannedOperation.RemoveSection -> step(AiStepKind.REMOVE_SECTION, sectionName)
    is PlannedOperation.DeleteItem -> step(AiStepKind.DELETE_ITEM, itemName)
}

private fun PlannedOperation.step(
    kind: AiStepKind,
    name: String,
    quantity: Quantity? = null,
    unit: UnitCode? = null,
    sectionName: String? = null,
) = AiStepUi(callIndex, kind, name, quantity, unit, sectionName)

/** The name a result message uses for a step. */
private fun PlannedOperation.subject(): String = when (this) {
    is PlannedOperation.CreateChecklist -> title
    is PlannedOperation.AddCategory -> category.displayName
    is PlannedOperation.AddItem -> name
    is PlannedOperation.UpdateQuantity -> itemName
    is PlannedOperation.UpdateUnit -> itemName
    is PlannedOperation.SetCompleted -> itemName
    is PlannedOperation.RemoveSection -> sectionName
    is PlannedOperation.DeleteItem -> itemName
}

/** "Done: Rice, Milk." plus, when needed, what was already there and what did not run. */
internal fun ExecutionReport.toMessage(): AiMessageUi {
    fun names(predicate: (OperationOutcome) -> Boolean) =
        results.filter { predicate(it.outcome) }.map { it.operation.subject() }.distinct().joinToString(", ")

    val done = names { it == OperationOutcome.Done }
    val already = names { it == OperationOutcome.AlreadyPresent }
    val failed = names { it is OperationOutcome.Failed || it == OperationOutcome.Skipped }
    val lines = buildList {
        if (done.isNotEmpty()) add(UiText(R.string.ai_result_done, listOf(done)))
        if (already.isNotEmpty()) add(UiText(R.string.ai_result_already, listOf(already)))
        if (failed.isNotEmpty()) add(UiText(R.string.ai_result_failed, listOf(failed)))
    }
    return AiMessageUi(lines, isProblem = failed.isNotEmpty())
}
