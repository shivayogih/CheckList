package com.dataloom.checklist.presentation.masteritem

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dataloom.checklist.R
import com.dataloom.checklist.domain.model.CategoryId
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.MasterItem
import com.dataloom.checklist.domain.model.MasterItemId
import com.dataloom.checklist.domain.model.Quantity
import com.dataloom.checklist.domain.model.SectionId
import com.dataloom.checklist.domain.model.UnitCode
import com.dataloom.checklist.domain.model.UnitDef
import com.dataloom.checklist.domain.usecase.AddMasterItemsToSectionUseCase
import com.dataloom.checklist.domain.usecase.DomainError
import com.dataloom.checklist.domain.usecase.DomainResult
import com.dataloom.checklist.domain.usecase.MasterItemSelection
import com.dataloom.checklist.domain.usecase.ObserveChecklistDetailUseCase
import com.dataloom.checklist.domain.usecase.ObserveUnitsUseCase
import com.dataloom.checklist.domain.usecase.SearchMasterItemsUseCase
import com.dataloom.checklist.localization.AppLanguageProvider
import com.dataloom.checklist.presentation.common.UiText
import com.dataloom.checklist.presentation.common.toUiText
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A suggestion row. When [selected], the row shows an optional amount and unit. */
data class MasterItemRowUi(
    val id: MasterItemId,
    val name: String,
    val selected: Boolean,
    val quantityText: String = "",
    val unit: UnitCode? = null,
    val error: UiText? = null,
)

data class AddItemsUiState(
    val sectionName: String = "",
    val query: String = "",
    /** Search every category instead of only the section's own. */
    val allCategories: Boolean = false,
    /** Selected items first (even when the current search no longer finds them), then results. */
    val rows: List<MasterItemRowUi> = emptyList(),
    val units: List<UnitDef> = emptyList(),
    val isSaving: Boolean = false,
) {
    val selectedCount: Int get() = rows.count { it.selected }

    /** Offer "Create new item" when the typed name is not an exact suggestion (journey J2). */
    val canCreateCustom: Boolean
        get() = query.isNotBlank() && rows.none { it.name.equals(query.trim(), ignoreCase = true) }
}

sealed interface AddItemsAction {
    data class QueryChanged(val query: String) : AddItemsAction
    data class AllCategoriesChanged(val enabled: Boolean) : AddItemsAction
    data class ToggleItem(val id: MasterItemId) : AddItemsAction
    data class QuantityChanged(val id: MasterItemId, val text: String) : AddItemsAction
    data class UnitChanged(val id: MasterItemId, val unit: UnitCode?) : AddItemsAction
    data object AddSelected : AddItemsAction
}

sealed interface AddItemsEffect {
    data class Added(val count: Int) : AddItemsEffect

    /** Some picks were already on the list; [names] were skipped, [addedCount] were added. */
    data class AlreadyOnList(val names: List<String>, val addedCount: Int) : AddItemsEffect
    data class Error(val message: UiText) : AddItemsEffect

    /** The section or its checklist was deleted elsewhere. */
    data object SectionGone : AddItemsEffect
}

/**
 * "Add item" (FR-04, FR-11, journey J1 step 5): offline, ranked search of master items in the app
 * language (ranking is the repository's: exact > prefix > contains, then most used), multi-select
 * with an optional amount per pick, then "Add selected" in one transaction.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel(assistedFactory = AddItemsViewModel.Factory::class)
class AddItemsViewModel @AssistedInject constructor(
    @Assisted("checklistId") checklistId: String,
    @Assisted("sectionId") sectionId: String,
    observeDetail: ObserveChecklistDetailUseCase,
    observeUnits: ObserveUnitsUseCase,
    private val searchMasterItems: SearchMasterItemsUseCase,
    private val addMasterItems: AddMasterItemsToSectionUseCase,
    private val languageProvider: AppLanguageProvider,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(@Assisted("checklistId") checklistId: String, @Assisted("sectionId") sectionId: String): AddItemsViewModel
    }

    private data class Draft(val item: MasterItem, val quantityText: String, val unit: UnitCode?, val error: UiText? = null)

    private val checklist = ChecklistId(checklistId)
    private val section = SectionId(sectionId)
    private val query = MutableStateFlow("")
    private val allCategories = MutableStateFlow(false)
    private val drafts = MutableStateFlow<Map<MasterItemId, Draft>>(linkedMapOf())
    private val isSaving = MutableStateFlow(false)
    private val effects = Channel<AddItemsEffect>(Channel.BUFFERED)

    val effect: Flow<AddItemsEffect> = effects.receiveAsFlow()

    private val currentSection = languageProvider.language
        .flatMapLatest { observeDetail(checklist, it) }
        .map { detail -> detail?.sections?.firstOrNull { it.id == section } }
        .onEach { if (it == null) effects.trySend(AddItemsEffect.SectionGone) }
        // One database subscription shared by the search and the screen state.
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    private val results: StateFlow<List<MasterItem>> = combine(
        query.debounce(SEARCH_DEBOUNCE_MS),
        allCategories,
        languageProvider.language,
        currentSection.map { it?.category?.id }.distinctUntilChanged(),
    ) { q, all, language, categoryId -> SearchRequest(q, language, if (all) null else categoryId, valid = all || categoryId != null) }
        .distinctUntilChanged()
        .mapLatest { request -> if (request.valid) searchMasterItems(request.query, request.language, request.categoryId) else emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private data class SearchRequest(
        val query: String,
        val language: String,
        val categoryId: CategoryId?,
        val valid: Boolean,
    )

    private val inputs = combine(query, allCategories, isSaving) { q, all, saving -> Triple(q, all, saving) }

    val uiState: StateFlow<AddItemsUiState> = combine(
        inputs,
        currentSection.onStart { emit(null) },
        results,
        drafts,
        observeUnits(),
    ) { (q, all, saving), section, results, drafts, units ->
        val selectedRows = drafts.values.map { it.toRow() }
        val resultRows = results.filterNot { it.id in drafts }.map { MasterItemRowUi(it.id, it.displayName, selected = false) }
        AddItemsUiState(
            sectionName = section?.category?.displayName.orEmpty(),
            query = q,
            allCategories = all,
            rows = selectedRows + resultRows,
            units = units,
            isSaving = saving,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AddItemsUiState())

    fun onAction(action: AddItemsAction) {
        when (action) {
            is AddItemsAction.QueryChanged -> query.value = action.query
            is AddItemsAction.AllCategoriesChanged -> allCategories.value = action.enabled
            is AddItemsAction.ToggleItem -> toggle(action.id)
            is AddItemsAction.QuantityChanged -> editDraft(action.id) { it.copy(quantityText = action.text, error = null) }
            is AddItemsAction.UnitChanged -> editDraft(action.id) { it.copy(unit = action.unit, error = null) }
            AddItemsAction.AddSelected -> addSelected()
        }
    }

    private fun toggle(id: MasterItemId) {
        drafts.update { current ->
            if (id in current) {
                current - id
            } else {
                val item = results.value.firstOrNull { it.id == id } ?: return@update current
                // The unit defaults to the item's usual unit (kg for rice); it is used only with an amount.
                current + (id to Draft(item, quantityText = "", unit = item.defaultUnit))
            }
        }
    }

    private fun editDraft(id: MasterItemId, edit: (Draft) -> Draft) {
        drafts.update { current -> current[id]?.let { current + (id to edit(it)) } ?: current }
    }

    private fun addSelected() {
        if (isSaving.value) return
        val picks = drafts.value.values.toList()
        if (picks.isEmpty()) return

        val parsed = picks.associate { draft -> draft.item.id to draft.quantityText.trim().takeIf { it.isNotEmpty() }?.let { Quantity.parse(it) } }
        val unreadable = picks.filter { it.quantityText.isNotBlank() && parsed[it.item.id] == null }.map { it.item.id }.toSet()
        if (unreadable.isNotEmpty()) {
            drafts.update { current ->
                current.mapValues { (id, draft) -> if (id in unreadable) draft.copy(error = UiText(R.string.error_quantity_invalid)) else draft }
            }
            return
        }

        val selections = picks.map { draft ->
            val quantity = parsed[draft.item.id]
            MasterItemSelection(draft.item, quantity, unit = draft.unit.takeIf { quantity != null })
        }
        isSaving.value = true
        viewModelScope.launch {
            val result = addMasterItems(checklist, section, selections, languageProvider.language.value)
            isSaving.value = false
            when (result) {
                is DomainResult.Success -> {
                    val outcome = result.value
                    drafts.value = linkedMapOf()
                    val effect = if (outcome.alreadyPresent.isEmpty()) {
                        AddItemsEffect.Added(outcome.addedItemIds.size)
                    } else {
                        AddItemsEffect.AlreadyOnList(outcome.alreadyPresent.map { it.displayName }, outcome.addedItemIds.size)
                    }
                    effects.send(effect)
                }
                is DomainResult.Failure -> when (val error = result.error) {
                    is DomainError.InvalidSelection -> editDraft(error.masterItemId) { it.copy(error = error.errors.first().toUiText()) }
                    else -> effects.send(AddItemsEffect.Error(error.toUiText()))
                }
            }
        }
    }

    private fun Draft.toRow() = MasterItemRowUi(item.id, item.displayName, selected = true, quantityText, unit, error)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val SEARCH_DEBOUNCE_MS = 150L
    }
}
