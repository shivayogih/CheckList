package com.dataloom.checklist.presentation.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dataloom.checklist.domain.model.CategoryId
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.usecase.AddCategoriesToChecklistUseCase
import com.dataloom.checklist.domain.usecase.CreateCategoryUseCase
import com.dataloom.checklist.domain.usecase.DomainResult
import com.dataloom.checklist.domain.usecase.ObserveCategoriesUseCase
import com.dataloom.checklist.domain.usecase.ObserveChecklistDetailUseCase
import com.dataloom.checklist.localization.AppLanguageProvider
import com.dataloom.checklist.presentation.common.UiText
import com.dataloom.checklist.presentation.common.toUiText
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddCategoriesUiState(
    val isLoading: Boolean = true,
    /** Categories not yet in the checklist. */
    val categories: List<CategoryOptionUi> = emptyList(),
    val newCategory: NewCategoryDialogUi? = null,
    val isSaving: Boolean = false,
) {
    val selectedCount: Int get() = categories.count { it.selected }
}

sealed interface AddCategoriesAction {
    data class ToggleCategory(val id: CategoryId) : AddCategoriesAction
    data object OpenNewCategory : AddCategoriesAction
    data class NewCategoryNameChanged(val name: String) : AddCategoriesAction
    data object ConfirmNewCategory : AddCategoriesAction
    data object DismissNewCategory : AddCategoriesAction
    data object Save : AddCategoriesAction
}

sealed interface AddCategoriesEffect {
    data class Added(val count: Int) : AddCategoriesEffect
    data class Error(val message: UiText) : AddCategoriesEffect

    /** The checklist was deleted elsewhere. */
    data object ChecklistGone : AddCategoriesEffect
}

/** Adds categories (sections) to an existing checklist (FR-02), including new custom categories. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = AddCategoriesViewModel.Factory::class)
class AddCategoriesViewModel @AssistedInject constructor(
    @Assisted checklistId: String,
    observeCategories: ObserveCategoriesUseCase,
    observeDetail: ObserveChecklistDetailUseCase,
    private val addCategories: AddCategoriesToChecklistUseCase,
    createCategory: CreateCategoryUseCase,
    private val languageProvider: AppLanguageProvider,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(checklistId: String): AddCategoriesViewModel
    }

    private val id = ChecklistId(checklistId)
    private val selected = MutableStateFlow<Set<CategoryId>>(emptySet())
    private val isSaving = MutableStateFlow(false)
    private val dialog = NewCategoryDialogController(createCategory) { languageProvider.language.value }
    private val effects = Channel<AddCategoriesEffect>(Channel.BUFFERED)

    val effect: Flow<AddCategoriesEffect> = effects.receiveAsFlow()

    val uiState: StateFlow<AddCategoriesUiState> = combine(
        languageProvider.language.flatMapLatest { observeCategories(it) },
        languageProvider.language.flatMapLatest { observeDetail(id, it) },
        selected,
        dialog.state,
        isSaving,
    ) { categories, detail, selected, dialog, saving ->
        if (detail == null) effects.trySend(AddCategoriesEffect.ChecklistGone)
        val present = detail?.sections.orEmpty().map { it.category.id }.toSet()
        AddCategoriesUiState(
            isLoading = false,
            categories = categories.filterNot { it.id in present }.map { it.toOption(it.id in selected) },
            newCategory = dialog,
            isSaving = saving,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AddCategoriesUiState())

    fun onAction(action: AddCategoriesAction) {
        when (action) {
            is AddCategoriesAction.ToggleCategory -> selected.update { if (action.id in it) it - action.id else it + action.id }
            AddCategoriesAction.OpenNewCategory -> dialog.open()
            is AddCategoriesAction.NewCategoryNameChanged -> dialog.onNameChanged(action.name)
            AddCategoriesAction.DismissNewCategory -> dialog.dismiss()
            AddCategoriesAction.ConfirmNewCategory -> viewModelScope.launch {
                dialog.confirm()?.let { newId -> selected.update { it + newId } }
            }
            AddCategoriesAction.Save -> save()
        }
    }

    private fun save() {
        if (isSaving.value) return
        val ordered = uiState.value.categories.filter { it.selected }.map { it.id }
        if (ordered.isEmpty()) {
            viewModelScope.launch { effects.send(AddCategoriesEffect.Added(0)) }
            return
        }
        isSaving.value = true
        viewModelScope.launch {
            val effect = when (val result = addCategories(id, ordered)) {
                is DomainResult.Success -> AddCategoriesEffect.Added(result.value.size)
                is DomainResult.Failure -> AddCategoriesEffect.Error(result.error.toUiText())
            }
            isSaving.value = false
            effects.send(effect)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
