package com.dataloom.checklist.presentation.draft

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dataloom.checklist.R
import com.dataloom.checklist.ai.draft.ChecklistDraft
import com.dataloom.checklist.ai.draft.ChecklistDraftBuilder
import com.dataloom.checklist.ai.draft.ChecklistDraftSaver
import com.dataloom.checklist.ai.draft.DraftItem
import com.dataloom.checklist.ai.draft.DraftSection
import com.dataloom.checklist.ai.model.AiResult
import com.dataloom.checklist.ai.model.RejectionReason
import com.dataloom.checklist.di.ApplicationScope
import com.dataloom.checklist.domain.common.AppLog
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.usecase.DomainResult
import com.dataloom.checklist.domain.validation.ChecklistValidator
import com.dataloom.checklist.domain.validation.FieldLimits
import com.dataloom.checklist.domain.validation.InputText
import com.dataloom.checklist.domain.validation.ItemValidator
import com.dataloom.checklist.domain.validation.ValidationResult
import com.dataloom.checklist.localization.AppLanguageProvider
import com.dataloom.checklist.presentation.common.UiText
import com.dataloom.checklist.presentation.common.toUiText
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DraftItemUi(val key: String, val name: String, val included: Boolean)

data class DraftSectionUi(val id: String, val name: String, val icon: String, val items: List<DraftItemUi>)

/** The "add an item" dialog of one section. */
data class AddItemUi(val sectionId: String, val sectionName: String, val name: String = "", val error: UiText? = null)

data class DraftReviewUiState(
    val isLoading: Boolean = true,
    /** Set when no draft could be made; the screen offers the manual way. */
    val problem: UiText? = null,
    val title: String = "",
    val titleError: UiText? = null,
    val sections: List<DraftSectionUi> = emptyList(),
    val addItem: AddItemUi? = null,
    val isSaving: Boolean = false,
    val saveError: UiText? = null,
) {
    val includedCount: Int get() = sections.sumOf { section -> section.items.count { it.included } }
    val hasDraft: Boolean get() = !isLoading && problem == null
    val canApprove: Boolean get() = hasDraft && !isSaving && title.isNotBlank() && includedCount > 0
}

sealed interface DraftReviewAction {
    data class TitleChanged(val text: String) : DraftReviewAction
    data class ToggleItem(val sectionId: String, val key: String, val included: Boolean) : DraftReviewAction
    data class RemoveSection(val sectionId: String) : DraftReviewAction
    data class OpenAddItem(val sectionId: String) : DraftReviewAction
    data class AddItemNameChanged(val text: String) : DraftReviewAction
    data object ConfirmAddItem : DraftReviewAction
    data object DismissAddItem : DraftReviewAction
    data object Approve : DraftReviewAction
}

sealed interface DraftReviewEffect {
    data class Saved(val id: ChecklistId) : DraftReviewEffect
}

/**
 * The draft review screen (CL-400): builds a [ChecklistDraft] from the request typed or spoken on Home,
 * lets the user rename it, untick items, drop groups and add their own items, and saves it only on
 * Approve. Nothing is written before that.
 *
 * Edits survive rotation (this ViewModel) and process death: the title and every edit are kept in
 * [SavedStateHandle] and re-applied to the draft, which is rebuilt from the same request.
 */
@HiltViewModel(assistedFactory = DraftReviewViewModel.Factory::class)
class DraftReviewViewModel @AssistedInject constructor(
    @Assisted private val prompt: String,
    private val builder: ChecklistDraftBuilder,
    private val saver: ChecklistDraftSaver,
    private val languageProvider: AppLanguageProvider,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(prompt: String): DraftReviewViewModel
    }

    private val state = MutableStateFlow(DraftReviewUiState())
    val uiState: StateFlow<DraftReviewUiState> = state.asStateFlow()

    private val effects = Channel<DraftReviewEffect>(Channel.BUFFERED)
    val effect = effects.receiveAsFlow()

    /** The draft as built; edits are applied on top of it. */
    private var draft: ChecklistDraft? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val result = try {
            builder.build(prompt, languageProvider.language.value)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            AiResult.Error(failure)
        }
        when (result) {
            is AiResult.Success -> {
                draft = result.value
                state.value = DraftReviewUiState(
                    isLoading = false,
                    title = savedState.get<String>(KEY_TITLE) ?: result.value.title,
                    sections = sectionsUi(result.value),
                )
            }
            is AiResult.Rejected ->
                state.value = DraftReviewUiState(isLoading = false, problem = result.reason.toUiText())
            is AiResult.Unavailable, is AiResult.Error ->
                state.value = DraftReviewUiState(isLoading = false, problem = UiText(R.string.error_generic))
        }
    }

    fun onAction(action: DraftReviewAction) {
        when (action) {
            is DraftReviewAction.TitleChanged -> {
                val text = InputText.forField(action.text, FieldLimits.TITLE_MAX)
                savedState[KEY_TITLE] = text
                state.update { it.copy(title = text, titleError = null) }
            }
            is DraftReviewAction.ToggleItem -> {
                editList(KEY_EXCLUDED) { excluded ->
                    val entry = entry(action.sectionId, action.key)
                    if (action.included) excluded - entry else excluded + entry
                }
                refresh()
            }
            is DraftReviewAction.RemoveSection -> {
                editList(KEY_REMOVED_SECTIONS) { it + action.sectionId }
                refresh()
            }
            is DraftReviewAction.OpenAddItem -> {
                val section = state.value.sections.firstOrNull { it.id == action.sectionId } ?: return
                state.update { it.copy(addItem = AddItemUi(section.id, section.name)) }
            }
            is DraftReviewAction.AddItemNameChanged -> state.update { current ->
                val dialog = current.addItem ?: return@update current
                val name = InputText.forField(action.text, FieldLimits.ITEM_NAME_MAX)
                current.copy(addItem = dialog.copy(name = name, error = null))
            }
            DraftReviewAction.ConfirmAddItem -> confirmAddItem()
            DraftReviewAction.DismissAddItem -> state.update { it.copy(addItem = null) }
            DraftReviewAction.Approve -> approve()
        }
    }

    private fun confirmAddItem() {
        val dialog = state.value.addItem ?: return
        val name = when (val result = ItemValidator.validate(dialog.name, null, null, null)) {
            is ValidationResult.Invalid -> {
                state.update { it.copy(addItem = dialog.copy(error = result.errors.first().toUiText())) }
                return
            }
            is ValidationResult.Valid -> result.value.name
        }
        val section = state.value.sections.firstOrNull { it.id == dialog.sectionId } ?: return
        if (section.items.any { it.name.equals(name, ignoreCase = true) }) {
            state.update { it.copy(addItem = dialog.copy(error = UiText(R.string.draft_item_duplicate))) }
            return
        }
        editList(KEY_ADDED) { it + entry(dialog.sectionId, name) }
        state.update { it.copy(addItem = null) }
        refresh()
    }

    private fun approve() {
        val current = state.value
        if (!current.hasDraft || current.isSaving) return
        val title = when (val result = ChecklistValidator.validate(current.title, null)) {
            is ValidationResult.Invalid -> {
                state.update { it.copy(titleError = result.errors.first().toUiText()) }
                return
            }
            is ValidationResult.Valid -> result.value.title
        }
        val edited = editedDraft(title) ?: return
        if (edited.itemCount == 0) {
            state.update { it.copy(saveError = UiText(R.string.draft_nothing_selected)) }
            return
        }
        state.update { it.copy(isSaving = true, saveError = null) }
        val locale = languageProvider.language.value
        viewModelScope.launch {
            // Runs in the application scope: once approved, the save finishes even if the screen closes.
            val result = try {
                applicationScope.async { saver.save(edited, locale) }.await()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
                AppLog.w(TAG, failure) { "Saving the draft failed" }
                null
            }
            when (result) {
                is DomainResult.Success -> effects.send(DraftReviewEffect.Saved(result.value))
                else -> state.update { it.copy(isSaving = false, saveError = UiText(R.string.error_generic)) }
            }
        }
    }

    /** The built draft with every edit applied (unticked items dropped); null before it is built. */
    private fun editedDraft(title: String): ChecklistDraft? {
        val base = draft ?: return null
        val excluded = list(KEY_EXCLUDED).toSet()
        return ChecklistDraft(
            title = title,
            sections = visibleSections(base).map { section ->
                section.copy(items = section.items.filterNot { entry(section.category.id.value, it.key) in excluded })
            },
        )
    }

    /** Sections not removed, each with the user's own items added; unticked items are still there. */
    private fun visibleSections(base: ChecklistDraft): List<DraftSection> {
        val removed = list(KEY_REMOVED_SECTIONS).toSet()
        val added = list(KEY_ADDED)
        return base.sections.filterNot { it.category.id.value in removed }.map { section ->
            val prefix = section.category.id.value + SEPARATOR
            val extra = added.filter { it.startsWith(prefix) }.map { DraftItem(it.removePrefix(prefix)) }
            section.copy(items = section.items + extra)
        }
    }

    private fun sectionsUi(base: ChecklistDraft): List<DraftSectionUi> {
        val excluded = list(KEY_EXCLUDED).toSet()
        return visibleSections(base).map { section ->
            val id = section.category.id.value
            DraftSectionUi(
                id = id,
                name = section.category.displayName,
                icon = section.category.iconKey,
                items = section.items.map { DraftItemUi(it.key, it.name, entry(id, it.key) !in excluded) },
            )
        }
    }

    private fun refresh() {
        val base = draft ?: return
        state.update { it.copy(saveError = null, sections = sectionsUi(base)) }
    }

    private fun list(key: String): List<String> = savedState.get<ArrayList<String>>(key).orEmpty()

    private fun editList(key: String, edit: (List<String>) -> List<String>) {
        savedState[key] = ArrayList(edit(list(key)).distinct())
    }

    private fun entry(sectionId: String, key: String) = "$sectionId$SEPARATOR$key"

    internal companion object {
        const val TAG = "DraftReview"
        const val KEY_TITLE = "draft_title"
        const val KEY_EXCLUDED = "draft_excluded"
        const val KEY_REMOVED_SECTIONS = "draft_removed_sections"
        const val KEY_ADDED = "draft_added"

        /** Never part of a category ID. */
        private const val SEPARATOR = "\u001F"
    }
}

internal fun RejectionReason.toUiText(): UiText = when (this) {
    RejectionReason.EMPTY_INPUT -> UiText(R.string.draft_empty)
    RejectionReason.TOO_LONG -> UiText(R.string.ai_too_long)
    RejectionReason.NOT_UNDERSTOOD, RejectionReason.NEEDS_CHECKLIST -> UiText(R.string.draft_not_understood)
}
