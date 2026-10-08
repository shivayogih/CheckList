package com.dataloom.checklist.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dataloom.checklist.domain.model.ChecklistFilter
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.ChecklistQuery
import com.dataloom.checklist.domain.model.ChecklistSort
import com.dataloom.checklist.domain.model.ChecklistSummary
import com.dataloom.checklist.domain.usecase.ArchiveChecklistUseCase
import com.dataloom.checklist.domain.usecase.DeleteChecklistUseCase
import com.dataloom.checklist.domain.usecase.DomainResult
import com.dataloom.checklist.domain.usecase.DuplicateChecklistUseCase
import com.dataloom.checklist.domain.usecase.ObserveChecklistsUseCase
import com.dataloom.checklist.domain.usecase.UnarchiveChecklistUseCase
import com.dataloom.checklist.domain.validation.FieldLimits
import com.dataloom.checklist.domain.validation.InputText
import com.dataloom.checklist.presentation.common.UiText
import com.dataloom.checklist.presentation.common.toUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One checklist on Home. */
data class ChecklistRowUi(
    val id: ChecklistId,
    val title: String,
    val completedItems: Int,
    val totalItems: Int,
    val progress: Float,
    val isArchived: Boolean,
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val search: String = "",
    val sort: ChecklistSort = ChecklistSort.RECENT,
    val filter: ChecklistFilter = ChecklistFilter.ACTIVE,
    val checklists: List<ChecklistRowUi> = emptyList(),
    /** Set while the "Delete 'Goa Trip'?" dialog is open. */
    val pendingDelete: ChecklistRowUi? = null,
    /** Whether any checklist exists, active or archived (assumed true until the first read). */
    val hasAnyChecklist: Boolean = true,
) {
    /** No checklist at all yet: Home shows the first-use message and a big create button. */
    val isFirstUse: Boolean
        get() = !isLoading && !hasAnyChecklist
}

sealed interface HomeAction {
    data class SearchChanged(val text: String) : HomeAction
    data class SortChanged(val sort: ChecklistSort) : HomeAction
    data class FilterChanged(val filter: ChecklistFilter) : HomeAction

    /** [copyTitle] is the localized "<title> (copy)" built by the screen. */
    data class Duplicate(val id: ChecklistId, val copyTitle: String) : HomeAction
    data class Archive(val row: ChecklistRowUi) : HomeAction
    data class Unarchive(val row: ChecklistRowUi) : HomeAction

    /** The Undo of the "Archived" snackbar. */
    data class UndoArchive(val id: ChecklistId) : HomeAction
    data class RequestDelete(val row: ChecklistRowUi) : HomeAction
    data object ConfirmDelete : HomeAction
    data object DismissDelete : HomeAction
}

sealed interface HomeEffect {
    data class Archived(val id: ChecklistId, val title: String) : HomeEffect
    data class Unarchived(val title: String) : HomeEffect
    data class Duplicated(val id: ChecklistId, val title: String) : HomeEffect
    data class Deleted(val title: String) : HomeEffect
    data class Error(val message: UiText) : HomeEffect
}

/**
 * Home: the user's checklists with search, sort and the Active/Archived filter (FR-01, FR-09). The
 * list is a live Room query, so changes made on any other screen appear here without a refresh.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    observeChecklists: ObserveChecklistsUseCase,
    private val duplicateChecklist: DuplicateChecklistUseCase,
    private val archiveChecklist: ArchiveChecklistUseCase,
    private val unarchiveChecklist: UnarchiveChecklistUseCase,
    private val deleteChecklist: DeleteChecklistUseCase,
) : ViewModel() {

    private val query = MutableStateFlow(ChecklistQuery())
    private val pendingDelete = MutableStateFlow<ChecklistRowUi?>(null)
    private val effects = Channel<HomeEffect>(Channel.BUFFERED)

    val effect: Flow<HomeEffect> = effects.receiveAsFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        query,
        query.flatMapLatest { q -> observeChecklists(q) },
        observeChecklists(ChecklistQuery(filter = ChecklistFilter.ALL)).map { it.isNotEmpty() },
        pendingDelete,
    ) { current, list, hasAny, delete ->
        // The results can lag the query by one database read; showing them beats a flashing spinner.
        HomeUiState(
            isLoading = false,
            search = current.search,
            sort = current.sort,
            filter = current.filter,
            checklists = list.map { it.toRow() },
            pendingDelete = delete,
            hasAnyChecklist = hasAny,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    fun onAction(action: HomeAction) {
        when (action) {
            is HomeAction.SearchChanged -> query.value = query.value.copy(search = InputText.forTyping(action.text, FieldLimits.SEARCH_MAX))
            is HomeAction.SortChanged -> query.value = query.value.copy(sort = action.sort)
            is HomeAction.FilterChanged -> query.value = query.value.copy(filter = action.filter)
            is HomeAction.Duplicate -> launchWrite {
                when (val result = duplicateChecklist(action.id, action.copyTitle)) {
                    is DomainResult.Success -> HomeEffect.Duplicated(result.value.id, action.copyTitle.trim())
                    is DomainResult.Failure -> HomeEffect.Error(result.error.toUiText())
                }
            }
            is HomeAction.Archive -> launchWrite {
                archiveChecklist(action.row.id)
                HomeEffect.Archived(action.row.id, action.row.title)
            }
            is HomeAction.Unarchive -> launchWrite {
                unarchiveChecklist(action.row.id)
                HomeEffect.Unarchived(action.row.title)
            }
            is HomeAction.UndoArchive -> launchWrite {
                unarchiveChecklist(action.id)
                null
            }
            is HomeAction.RequestDelete -> pendingDelete.value = action.row
            HomeAction.DismissDelete -> pendingDelete.value = null
            HomeAction.ConfirmDelete -> {
                val row = pendingDelete.value ?: return
                pendingDelete.value = null
                launchWrite {
                    deleteChecklist(row.id)
                    HomeEffect.Deleted(row.title)
                }
            }
        }
    }

    private fun launchWrite(block: suspend () -> HomeEffect?) {
        viewModelScope.launch { block()?.let { effects.send(it) } }
    }

    private fun ChecklistSummary.toRow() = ChecklistRowUi(
        id = checklist.id,
        title = checklist.title,
        completedItems = completedItems,
        totalItems = totalItems,
        progress = progress,
        isArchived = checklist.isArchived,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
