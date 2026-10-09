package com.dataloom.checklist.presentation.checklist.create

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dataloom.checklist.domain.model.CategoryId
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.usecase.CreateCategoryUseCase
import com.dataloom.checklist.domain.usecase.CreateChecklistUseCase
import com.dataloom.checklist.domain.usecase.DomainError
import com.dataloom.checklist.domain.usecase.DomainResult
import com.dataloom.checklist.domain.usecase.IsChecklistTitleUsedUseCase
import com.dataloom.checklist.domain.usecase.ObserveCategoriesUseCase
import com.dataloom.checklist.domain.validation.ChecklistValidator
import com.dataloom.checklist.domain.validation.Field
import com.dataloom.checklist.domain.validation.FieldLimits
import com.dataloom.checklist.domain.validation.InputText
import com.dataloom.checklist.localization.AppLanguageProvider
import com.dataloom.checklist.presentation.category.CategoryOptionUi
import com.dataloom.checklist.presentation.category.NewCategoryDialogController
import com.dataloom.checklist.presentation.category.NewCategoryDialogUi
import com.dataloom.checklist.presentation.category.toOption
import com.dataloom.checklist.presentation.common.UiText
import com.dataloom.checklist.presentation.common.isAccepted
import com.dataloom.checklist.presentation.common.liveError
import com.dataloom.checklist.presentation.common.toUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

data class CreateChecklistUiState(
    val title: String = "",
    val titleError: UiText? = null,
    /** Another checklist already has this title. A hint only: duplicates are allowed. */
    val titleAlreadyUsed: Boolean = false,
    val description: String = "",
    val descriptionError: UiText? = null,
    val categories: List<CategoryOptionUi> = emptyList(),
    val newCategory: NewCategoryDialogUi? = null,
    val isSaving: Boolean = false,
) {
    val selectedCount: Int get() = categories.count { it.selected }

    /** Create is enabled only while title and description would be accepted (CL-280). */
    val canCreate: Boolean
        get() = !isSaving && isAccepted(title, ChecklistValidator::validateTitle) &&
            isAccepted(description) { ChecklistValidator.validate("x", it) }
}

sealed interface CreateChecklistAction {
    data class TitleChanged(val title: String) : CreateChecklistAction
    data class DescriptionChanged(val description: String) : CreateChecklistAction
    data class ToggleCategory(val id: CategoryId) : CreateChecklistAction
    data object OpenNewCategory : CreateChecklistAction
    data class NewCategoryNameChanged(val name: String) : CreateChecklistAction
    data object ConfirmNewCategory : CreateChecklistAction
    data object DismissNewCategory : CreateChecklistAction
    data object Create : CreateChecklistAction
}

sealed interface CreateChecklistEffect {
    data class Created(val id: ChecklistId) : CreateChecklistEffect
    data class Error(val message: UiText) : CreateChecklistEffect
}

/**
 * Create checklist (journey J1): a title, an optional description and any number of categories,
 * including new custom ones. Rules (title length, duplicate category names) come from the use cases.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class CreateChecklistViewModel @Inject constructor(
    observeCategories: ObserveCategoriesUseCase,
    private val createChecklist: CreateChecklistUseCase,
    private val isTitleUsed: IsChecklistTitleUsedUseCase,
    createCategory: CreateCategoryUseCase,
    private val languageProvider: AppLanguageProvider,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private data class Form(
        val title: String = "",
        val titleError: UiText? = null,
        val description: String = "",
        val descriptionError: UiText? = null,
        val selected: Set<CategoryId> = emptySet(),
        val isSaving: Boolean = false,
    )

    // The typed title, description and ticked categories survive process death (they are not personal
    // data: a checklist title is not the encrypted profile). Errors and the saving flag do not.
    private val form = MutableStateFlow(
        Form(
            title = savedState.get<String>(KEY_TITLE).orEmpty(),
            description = savedState.get<String>(KEY_DESCRIPTION).orEmpty(),
            selected = savedState.get<ArrayList<String>>(KEY_SELECTED).orEmpty()
                .mapTo(LinkedHashSet()) { CategoryId(it) },
        ),
    )
    private val newCategoryDialog = NewCategoryDialogController(createCategory) { languageProvider.language.value }
    private val effects = Channel<CreateChecklistEffect>(Channel.BUFFERED)

    val effect: Flow<CreateChecklistEffect> = effects.receiveAsFlow()

    private val titleUsed: Flow<Boolean> = form
        .map { it.title }
        .distinctUntilChanged()
        .debounce(TITLE_CHECK_DEBOUNCE_MS)
        .mapLatest { isTitleUsed(it) }
        .onStart { emit(false) }

    private val categories = languageProvider.language.flatMapLatest { observeCategories(it) }

    val uiState: StateFlow<CreateChecklistUiState> = combine(
        form,
        titleUsed,
        categories.onStart { emit(emptyList()) },
        newCategoryDialog.state,
    ) { form, used, categories, dialog ->
        CreateChecklistUiState(
            title = form.title,
            titleError = form.titleError,
            titleAlreadyUsed = used,
            description = form.description,
            descriptionError = form.descriptionError,
            categories = categories.map { it.toOption(selected = it.id in form.selected) },
            newCategory = dialog,
            isSaving = form.isSaving,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CreateChecklistUiState())

    fun onAction(action: CreateChecklistAction) {
        when (action) {
            is CreateChecklistAction.TitleChanged -> {
                val title = InputText.forField(action.title, FieldLimits.TITLE_MAX)
                edit { it.copy(title = title, titleError = liveError(title, ChecklistValidator::validateTitle)) }
            }
            is CreateChecklistAction.DescriptionChanged -> {
                val description = InputText.forField(action.description, FieldLimits.DESCRIPTION_MAX, multiline = true)
                edit {
                    it.copy(
                        description = description,
                        descriptionError = liveError(description) { text -> ChecklistValidator.validate("x", text) },
                    )
                }
            }
            is CreateChecklistAction.ToggleCategory -> edit {
                it.copy(selected = if (action.id in it.selected) it.selected - action.id else it.selected + action.id)
            }
            CreateChecklistAction.OpenNewCategory -> newCategoryDialog.open()
            is CreateChecklistAction.NewCategoryNameChanged -> newCategoryDialog.onNameChanged(action.name)
            CreateChecklistAction.DismissNewCategory -> newCategoryDialog.dismiss()
            CreateChecklistAction.ConfirmNewCategory -> viewModelScope.launch {
                // A category the user just created is one they want in this checklist.
                newCategoryDialog.confirm()?.let { id -> edit { it.copy(selected = it.selected + id) } }
            }
            CreateChecklistAction.Create -> create()
        }
    }

    /** Applies a user edit and mirrors the form fields into saved state. */
    private fun edit(change: (Form) -> Form) {
        val updated = form.updateAndGet(change)
        savedState[KEY_TITLE] = updated.title
        savedState[KEY_DESCRIPTION] = updated.description
        savedState[KEY_SELECTED] = ArrayList(updated.selected.map { it.value })
    }

    private fun create() {
        val current = form.value
        if (current.isSaving) return
        form.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            // Keep the order the user sees: the category list order, not the order of tapping.
            val ordered = uiState.value.categories.filter { it.selected }.map { it.id }
            val categoryIds = ordered + (current.selected - ordered.toSet())
            when (val result = createChecklist(current.title, current.description, categoryIds)) {
                is DomainResult.Success -> {
                    form.update { it.copy(isSaving = false) }
                    effects.send(CreateChecklistEffect.Created(result.value.id))
                }
                is DomainResult.Failure -> {
                    val error = result.error
                    if (error is DomainError.Invalid) {
                        form.update { form ->
                            form.copy(
                                isSaving = false,
                                titleError = error.errors.firstOrNull { it.field == Field.TITLE }?.toUiText(),
                                descriptionError = error.errors.firstOrNull { it.field == Field.DESCRIPTION }?.toUiText(),
                            )
                        }
                    } else {
                        form.update { it.copy(isSaving = false) }
                        effects.send(CreateChecklistEffect.Error(error.toUiText()))
                    }
                }
            }
        }
    }

    internal companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val TITLE_CHECK_DEBOUNCE_MS = 300L
        const val KEY_TITLE = "create_title"
        const val KEY_DESCRIPTION = "create_description"
        const val KEY_SELECTED = "create_selected_categories"
    }
}
