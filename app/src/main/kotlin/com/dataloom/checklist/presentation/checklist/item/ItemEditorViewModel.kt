package com.dataloom.checklist.presentation.checklist.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.ChecklistItemId
import com.dataloom.checklist.domain.model.SectionId
import com.dataloom.checklist.domain.model.UnitCode
import com.dataloom.checklist.domain.model.UnitDef
import com.dataloom.checklist.domain.usecase.AddCustomItemUseCase
import com.dataloom.checklist.domain.usecase.CreateCustomUnitUseCase
import com.dataloom.checklist.domain.usecase.CustomItemOutcome
import com.dataloom.checklist.domain.usecase.DomainError
import com.dataloom.checklist.domain.usecase.DomainResult
import com.dataloom.checklist.domain.usecase.ObserveChecklistDetailUseCase
import com.dataloom.checklist.domain.usecase.ObserveUnitsUseCase
import com.dataloom.checklist.domain.usecase.UpdateChecklistItemUseCase
import com.dataloom.checklist.domain.validation.Field
import com.dataloom.checklist.domain.validation.FieldLimits
import com.dataloom.checklist.domain.validation.InputText
import com.dataloom.checklist.domain.validation.ItemValidator
import com.dataloom.checklist.domain.validation.QuantityInput
import com.dataloom.checklist.domain.validation.QuantityParse
import com.dataloom.checklist.domain.validation.UnitValidator
import com.dataloom.checklist.domain.validation.ValidationError
import com.dataloom.checklist.localization.AppLanguageProvider
import com.dataloom.checklist.presentation.common.UiText
import com.dataloom.checklist.presentation.common.isAccepted
import com.dataloom.checklist.presentation.common.liveError
import com.dataloom.checklist.presentation.common.quantityFieldError
import com.dataloom.checklist.presentation.common.toUiText
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NewUnitDialogUi(
    val label: String = "",
    val allowsDecimal: Boolean = false,
    val error: UiText? = null,
    val isSaving: Boolean = false,
) {
    /** The dialog's Create button: a label that would be accepted (CL-280). */
    val canConfirm: Boolean get() = !isSaving && isAccepted(label, UnitValidator::validateLabel)
}

data class ItemEditorUiState(
    val isEditing: Boolean = false,
    val isLoading: Boolean = true,
    val sectionName: String = "",
    val name: String = "",
    val nameError: UiText? = null,
    /** Raw text so "2," or "2.5" can be typed; parsed (comma or period) only on save. */
    val quantityText: String = "",
    val quantityError: UiText? = null,
    val unit: UnitCode? = null,
    val unitError: UiText? = null,
    val notes: String = "",
    val notesError: UiText? = null,
    /** Custom items only: also add the name to the category's suggestions (FR-05). */
    val saveToSuggestions: Boolean = false,
    val units: List<UnitDef> = emptyList(),
    val newUnit: NewUnitDialogUi? = null,
    val isSaving: Boolean = false,
) {
    val selectedUnit: UnitDef? get() = units.firstOrNull { it.code == unit }

    /**
     * The primary action is enabled only while the form would be accepted (CL-280): a name, an
     * amount that reads as a number and notes within the limit. Unit rules that depend on the amount
     * (a unit needs an amount; whole-number units) are answered by the domain when saving.
     */
    val canSave: Boolean
        get() = !isSaving && !isLoading &&
            isAccepted(name) { ItemValidator.validate(it, null, null, null) } &&
            quantityFieldError(quantityText) == null &&
            isAccepted(notes) { ItemValidator.validate("x", null, null, it) }
}

sealed interface ItemEditorAction {
    data class NameChanged(val name: String) : ItemEditorAction
    data class QuantityChanged(val text: String) : ItemEditorAction
    data class UnitChanged(val unit: UnitCode?) : ItemEditorAction
    data class NotesChanged(val notes: String) : ItemEditorAction
    data class SaveToSuggestionsChanged(val enabled: Boolean) : ItemEditorAction
    data object OpenNewUnit : ItemEditorAction
    data class NewUnitLabelChanged(val label: String) : ItemEditorAction
    data class NewUnitDecimalChanged(val allowsDecimal: Boolean) : ItemEditorAction
    data object ConfirmNewUnit : ItemEditorAction
    data object DismissNewUnit : ItemEditorAction
    data object Save : ItemEditorAction
}

sealed interface ItemEditorEffect {
    data object Saved : ItemEditorEffect

    /** "Save to suggestions" matched an item already in this section; nothing was added. */
    data class AlreadyOnList(val name: String) : ItemEditorEffect

    /** The item, section or checklist was deleted elsewhere. */
    data object Gone : ItemEditorEffect
    data class Error(val message: UiText) : ItemEditorEffect
}

/**
 * One form for a custom item (FR-05, journey J2) and for editing any item: name, quantity (integer or
 * decimal, comma or period), unit (built-in or a new custom unit) and notes (FR-06). A null [itemId]
 * means "create in [sectionId]".
 */
@HiltViewModel(assistedFactory = ItemEditorViewModel.Factory::class)
class ItemEditorViewModel @AssistedInject constructor(
    @Assisted("checklistId") checklistId: String,
    @Assisted("sectionId") sectionId: String,
    @Assisted("itemId") itemId: String?,
    @Assisted("initialName") initialName: String,
    private val observeDetail: ObserveChecklistDetailUseCase,
    observeUnits: ObserveUnitsUseCase,
    private val addCustomItem: AddCustomItemUseCase,
    private val updateItem: UpdateChecklistItemUseCase,
    private val createCustomUnit: CreateCustomUnitUseCase,
    private val languageProvider: AppLanguageProvider,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("checklistId") checklistId: String,
            @Assisted("sectionId") sectionId: String,
            @Assisted("itemId") itemId: String?,
            @Assisted("initialName") initialName: String,
        ): ItemEditorViewModel
    }

    private val checklist = ChecklistId(checklistId)
    private val section = SectionId(sectionId)
    private val item = itemId?.let(::ChecklistItemId)
    private val form = MutableStateFlow(ItemEditorUiState(isEditing = item != null, name = initialName))
    private val effects = Channel<ItemEditorEffect>(Channel.BUFFERED)

    val effect: Flow<ItemEditorEffect> = effects.receiveAsFlow()

    val uiState: StateFlow<ItemEditorUiState> = combine(form, observeUnits()) { form, units -> form.copy(units = units) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), form.value)

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val detail = observeDetail(checklist, languageProvider.language.value).first()
        val target = detail?.sections?.firstOrNull { it.id == section }
        if (target == null) {
            effects.send(ItemEditorEffect.Gone)
            return
        }
        if (item == null) {
            form.update { it.copy(isLoading = false, sectionName = target.category.displayName) }
            return
        }
        val existing = target.items.firstOrNull { it.id == item }
        if (existing == null) {
            effects.send(ItemEditorEffect.Gone)
            return
        }
        form.update {
            it.copy(
                isLoading = false,
                sectionName = target.category.displayName,
                name = existing.displayName,
                quantityText = existing.quantity?.toPlainString().orEmpty(),
                unit = existing.unit,
                notes = existing.notes.orEmpty(),
            )
        }
    }

    fun onAction(action: ItemEditorAction) {
        when (action) {
            is ItemEditorAction.NameChanged -> {
                val name = InputText.forField(action.name, FieldLimits.ITEM_NAME_MAX)
                form.update { it.copy(name = name, nameError = liveError(name) { v -> ItemValidator.validate(v, null, null, null) }) }
            }
            is ItemEditorAction.QuantityChanged -> {
                // Letters and symbols cannot be typed or pasted; digits of other scripts become 0-9.
                val text = QuantityInput.sanitize(action.text)
                form.update { it.copy(quantityText = text, quantityError = quantityFieldError(text), unitError = null) }
            }
            is ItemEditorAction.UnitChanged ->
                form.update { it.copy(unit = action.unit, unitError = null, quantityError = null) }
            is ItemEditorAction.NotesChanged -> {
                val notes = InputText.forField(action.notes, FieldLimits.NOTES_MAX, multiline = true)
                form.update { it.copy(notes = notes, notesError = liveError(notes) { v -> ItemValidator.validate("x", null, null, v) }) }
            }
            is ItemEditorAction.SaveToSuggestionsChanged -> form.update { it.copy(saveToSuggestions = action.enabled) }
            ItemEditorAction.OpenNewUnit -> form.update { it.copy(newUnit = NewUnitDialogUi()) }
            is ItemEditorAction.NewUnitLabelChanged ->
            {
                val label = InputText.forField(action.label, FieldLimits.UNIT_LABEL_MAX)
                form.update { it.copy(newUnit = it.newUnit?.copy(label = label, error = liveError(label, UnitValidator::validateLabel))) }
            }
            is ItemEditorAction.NewUnitDecimalChanged ->
                form.update { it.copy(newUnit = it.newUnit?.copy(allowsDecimal = action.allowsDecimal)) }
            ItemEditorAction.DismissNewUnit -> form.update { it.copy(newUnit = null) }
            ItemEditorAction.ConfirmNewUnit -> confirmNewUnit()
            ItemEditorAction.Save -> save()
        }
    }

    private fun confirmNewUnit() {
        val dialog = form.value.newUnit ?: return
        if (dialog.isSaving) return
        form.update { it.copy(newUnit = dialog.copy(isSaving = true)) }
        viewModelScope.launch {
            when (val result = createCustomUnit(dialog.label, dialog.allowsDecimal)) {
                // The unit the user just created is the one they want for this item.
                is DomainResult.Success -> form.update { it.copy(newUnit = null, unit = result.value, unitError = null) }
                is DomainResult.Failure ->
                    form.update { it.copy(newUnit = dialog.copy(isSaving = false, error = result.error.toUiText())) }
            }
        }
    }

    private fun save() {
        val current = form.value
        if (current.isSaving || current.isLoading) return
        val quantity = when (val parsed = QuantityInput.parse(current.quantityText)) {
            QuantityParse.Empty -> null
            is QuantityParse.Valid -> parsed.quantity
            is QuantityParse.Invalid -> {
                form.update { it.copy(quantityError = parsed.error.toUiText()) }
                return
            }
        }
        form.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = if (item != null) {
                updateItem(item, current.name, quantity, current.unit, current.notes)
            } else {
                addCustomItem(
                    checklistId = checklist,
                    sectionId = section,
                    name = current.name,
                    locale = languageProvider.language.value,
                    quantity = quantity,
                    unit = current.unit,
                    notes = current.notes,
                    saveToMasterList = current.saveToSuggestions,
                )
            }
            form.update { it.copy(isSaving = false) }
            when (result) {
                is DomainResult.Success -> {
                    val value = result.value
                    effects.send(
                        if (value is CustomItemOutcome.AlreadyPresent) {
                            ItemEditorEffect.AlreadyOnList(value.existing.displayName)
                        } else {
                            ItemEditorEffect.Saved
                        },
                    )
                }
                is DomainResult.Failure -> when (val error = result.error) {
                    is DomainError.Invalid -> showFieldErrors(error.errors)
                    DomainError.NotFound -> effects.send(ItemEditorEffect.Gone)
                    else -> effects.send(ItemEditorEffect.Error(error.toUiText()))
                }
            }
        }
    }

    private fun showFieldErrors(errors: List<ValidationError>) {
        fun errorFor(field: Field) = errors.firstOrNull { it.field == field }?.toUiText()
        form.update {
            it.copy(
                nameError = errorFor(Field.ITEM_NAME),
                quantityError = errorFor(Field.QUANTITY),
                unitError = errorFor(Field.UNIT),
                notesError = errorFor(Field.NOTES),
            )
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
