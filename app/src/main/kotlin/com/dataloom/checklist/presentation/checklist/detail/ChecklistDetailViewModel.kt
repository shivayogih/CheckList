package com.dataloom.checklist.presentation.checklist.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dataloom.checklist.di.ApplicationScope
import com.dataloom.checklist.domain.model.ChecklistDetail
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.ChecklistItem
import com.dataloom.checklist.domain.model.ChecklistItemId
import com.dataloom.checklist.domain.model.ChecklistSection
import com.dataloom.checklist.domain.model.Quantity
import com.dataloom.checklist.domain.model.SectionId
import com.dataloom.checklist.domain.model.UnitCode
import com.dataloom.checklist.domain.model.UnitDef
import com.dataloom.checklist.domain.usecase.DeleteChecklistItemUseCase
import com.dataloom.checklist.domain.usecase.DomainError
import com.dataloom.checklist.domain.usecase.DomainResult
import com.dataloom.checklist.domain.usecase.MoveItemUseCase
import com.dataloom.checklist.domain.usecase.MoveSectionUseCase
import com.dataloom.checklist.domain.usecase.ObserveChecklistDetailUseCase
import com.dataloom.checklist.domain.usecase.ObserveUnitsUseCase
import com.dataloom.checklist.domain.usecase.RemoveSectionUseCase
import com.dataloom.checklist.domain.usecase.SetItemCompletedUseCase
import com.dataloom.checklist.domain.usecase.UpdateChecklistUseCase
import com.dataloom.checklist.domain.validation.ChecklistValidator
import com.dataloom.checklist.domain.validation.Field
import com.dataloom.checklist.domain.validation.FieldLimits
import com.dataloom.checklist.domain.validation.InputText
import com.dataloom.checklist.localization.AppLanguageProvider
import com.dataloom.checklist.presentation.common.UiText
import com.dataloom.checklist.presentation.common.isAccepted
import com.dataloom.checklist.presentation.common.liveError
import com.dataloom.checklist.presentation.common.toUiText
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ItemUi(
    val id: ChecklistItemId,
    val name: String,
    val quantity: Quantity?,
    /** Resolved unit definition (custom units carry their label); null when the item has no unit. */
    val unit: UnitDef?,
    val notes: String?,
    val isCompleted: Boolean,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
)

data class SectionUi(
    val id: SectionId,
    val name: String,
    val icon: String,
    val items: List<ItemUi>,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
)

data class RenameDialogUi(val title: String, val error: UiText? = null, val isSaving: Boolean = false) {
    /** Save is offered only for a title the domain would accept (CL-280). */
    val canConfirm: Boolean get() = !isSaving && isAccepted(title, ChecklistValidator::validateTitle)
}

data class ChecklistDetailUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val description: String? = null,
    val completedItems: Int = 0,
    val totalItems: Int = 0,
    val sections: List<SectionUi> = emptyList(),
    val rename: RenameDialogUi? = null,
    /** Set while "Remove 'Groceries' and its items?" is open. */
    val pendingSectionRemoval: SectionUi? = null,
) {
    val progress: Float get() = if (totalItems == 0) 0f else completedItems.toFloat() / totalItems
}

/** The two PDF menu actions. */
enum class PdfExport { SHARE, SAVE }

sealed interface ChecklistDetailAction {
    data class ToggleItem(val id: ChecklistItemId, val completed: Boolean) : ChecklistDetailAction

    /** Hides the item at once; it is deleted when the Undo snackbar closes ([CommitDelete]). */
    data class DeleteItem(val item: ItemUi) : ChecklistDetailAction
    data class UndoDelete(val id: ChecklistItemId) : ChecklistDetailAction
    data class CommitDelete(val id: ChecklistItemId) : ChecklistDetailAction

    /** Deletes every item still waiting for Undo, e.g. after the snackbar was lost to a rotation. */
    data object CommitPendingDeletes : ChecklistDetailAction

    /**
     * Share or save as PDF was tapped. Deletions still waiting for Undo are committed first, so the
     * PDF never contains an item the user just deleted (CL-241); then [ChecklistDetailEffect.PdfReady].
     */
    data class ExportPdf(val export: PdfExport) : ChecklistDetailAction
    data class MoveItemUp(val id: ChecklistItemId) : ChecklistDetailAction
    data class MoveItemDown(val id: ChecklistItemId) : ChecklistDetailAction
    data class MoveSectionUp(val id: SectionId) : ChecklistDetailAction
    data class MoveSectionDown(val id: SectionId) : ChecklistDetailAction
    data class RequestRemoveSection(val section: SectionUi) : ChecklistDetailAction
    data object ConfirmRemoveSection : ChecklistDetailAction
    data object DismissRemoveSection : ChecklistDetailAction
    data object StartRename : ChecklistDetailAction
    data class RenameChanged(val title: String) : ChecklistDetailAction
    data object ConfirmRename : ChecklistDetailAction
    data object DismissRename : ChecklistDetailAction
}

sealed interface ChecklistDetailEffect {
    data class ItemDeleted(val id: ChecklistItemId, val name: String) : ChecklistDetailEffect

    /** The checklist was deleted (here or elsewhere): the screen closes. */
    data object ChecklistGone : ChecklistDetailEffect

    /** Every pending deletion is now in the database: the PDF can be built. */
    data class PdfReady(val export: PdfExport) : ChecklistDetailEffect
    data class Error(val message: UiText) : ChecklistDetailEffect
}

/**
 * Checklist detail (FR-02, FR-06 to FR-08): sections with items, progress, ticking, reordering with
 * explicit move up/down actions, removing sections and deleting items with Undo. Everything is read
 * from one live query, so a write shows up through Room rather than through local bookkeeping.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = ChecklistDetailViewModel.Factory::class)
class ChecklistDetailViewModel @AssistedInject constructor(
    @Assisted checklistId: String,
    observeDetail: ObserveChecklistDetailUseCase,
    observeUnits: ObserveUnitsUseCase,
    private val setItemCompleted: SetItemCompletedUseCase,
    private val deleteItem: DeleteChecklistItemUseCase,
    private val moveItem: MoveItemUseCase,
    private val moveSection: MoveSectionUseCase,
    private val removeSection: RemoveSectionUseCase,
    private val updateChecklist: UpdateChecklistUseCase,
    languageProvider: AppLanguageProvider,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(checklistId: String): ChecklistDetailViewModel
    }

    private sealed interface Load {
        data object Loading : Load
        data class Loaded(val detail: ChecklistDetail?) : Load
    }

    private val id = ChecklistId(checklistId)
    private val pendingDeletes = MutableStateFlow<Set<ChecklistItemId>>(emptySet())

    /** Serializes delete commits, so a PDF export waits for a commit that is already running. */
    private val deleteCommits = Mutex()

    // An open rename dialog and its text survive process death. Pending item deletions do not: they are
    // committed when the screen closes, and a killed process simply keeps the items.
    private val rename = MutableStateFlow(savedState.get<String>(KEY_RENAME)?.let { RenameDialogUi(title = it) })
    private val pendingSectionRemoval = MutableStateFlow<SectionUi?>(null)
    private val effects = Channel<ChecklistDetailEffect>(Channel.BUFFERED)

    val effect: Flow<ChecklistDetailEffect> = effects.receiveAsFlow()

    private val detail: StateFlow<Load> = languageProvider.language
        .flatMapLatest { observeDetail(id, it) }
        .onEach { if (it == null) effects.trySend(ChecklistDetailEffect.ChecklistGone) }
        .map<ChecklistDetail?, Load> { Load.Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), Load.Loading)

    val uiState: StateFlow<ChecklistDetailUiState> = combine(
        detail,
        observeUnits(),
        pendingDeletes,
        rename,
        pendingSectionRemoval,
    ) { load, units, pending, rename, removal ->
        val detail = (load as? Load.Loaded)?.detail ?: return@combine ChecklistDetailUiState(isLoading = load is Load.Loading)
        val unitsByCode = units.associateBy { it.code }
        val sections = detail.sections.mapIndexed { index, section ->
            section.toUi(index, detail.sections.size, pending, unitsByCode)
        }
        val visibleItems = sections.flatMap { it.items }
        ChecklistDetailUiState(
            isLoading = false,
            title = detail.checklist.title,
            description = detail.checklist.description,
            completedItems = visibleItems.count { it.isCompleted },
            totalItems = visibleItems.size,
            sections = sections,
            rename = rename,
            pendingSectionRemoval = removal,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ChecklistDetailUiState())

    fun onAction(action: ChecklistDetailAction) {
        when (action) {
            is ChecklistDetailAction.ToggleItem -> launchWrite { setItemCompleted(action.id, action.completed) }
            is ChecklistDetailAction.DeleteItem -> {
                pendingDeletes.update { it + action.item.id }
                effects.trySend(ChecklistDetailEffect.ItemDeleted(action.item.id, action.item.name))
            }
            is ChecklistDetailAction.UndoDelete -> pendingDeletes.update { it - action.id }
            is ChecklistDetailAction.CommitDelete -> commitDeletes(setOf(action.id))
            ChecklistDetailAction.CommitPendingDeletes -> commitDeletes(pendingDeletes.value)
            is ChecklistDetailAction.ExportPdf -> launchWrite {
                commitDeletesNow(pendingDeletes.value)
                effects.send(ChecklistDetailEffect.PdfReady(action.export))
            }
            is ChecklistDetailAction.MoveItemUp -> moveItemBy(action.id, -1)
            is ChecklistDetailAction.MoveItemDown -> moveItemBy(action.id, +1)
            is ChecklistDetailAction.MoveSectionUp -> moveSectionBy(action.id, -1)
            is ChecklistDetailAction.MoveSectionDown -> moveSectionBy(action.id, +1)
            is ChecklistDetailAction.RequestRemoveSection -> pendingSectionRemoval.value = action.section
            ChecklistDetailAction.DismissRemoveSection -> pendingSectionRemoval.value = null
            ChecklistDetailAction.ConfirmRemoveSection -> {
                val section = pendingSectionRemoval.value ?: return
                pendingSectionRemoval.value = null
                launchWrite { removeSection(section.id) }
            }
            ChecklistDetailAction.StartRename ->
                setRename(RenameDialogUi(title = currentDetail()?.checklist?.title.orEmpty()))
            is ChecklistDetailAction.RenameChanged -> {
                val title = InputText.forField(action.title, FieldLimits.TITLE_MAX)
                val error = liveError(title, ChecklistValidator::validateTitle)
                setRename(rename.value?.copy(title = title, error = error))
            }
            ChecklistDetailAction.DismissRename -> setRename(null)
            ChecklistDetailAction.ConfirmRename -> confirmRename()
        }
    }

    /** Deletions the user did not undo must still happen when the screen closes. */
    override fun onCleared() {
        val pending = pendingDeletes.value
        if (pending.isNotEmpty()) applicationScope.launch { pending.forEach { deleteItem(it) } }
        super.onCleared()
    }

    private fun commitDeletes(ids: Set<ChecklistItemId>) {
        if ((ids intersect pendingDeletes.value).isEmpty()) return
        launchWrite { commitDeletesNow(ids) }
    }

    /**
     * Deletes those of [ids] still waiting for Undo and returns once they are gone from the database.
     * Under [deleteCommits], so an item whose commit is already running is not deleted twice and a
     * caller that needs the result (PDF export) waits for it.
     */
    private suspend fun commitDeletesNow(ids: Set<ChecklistItemId>) = deleteCommits.withLock {
        val toDelete = ids intersect pendingDeletes.value
        if (toDelete.isEmpty()) return@withLock
        toDelete.forEach { deleteItem(it) }
        // Un-hide only after the delete, so the row does not flash back for one frame.
        pendingDeletes.update { it - toDelete }
    }

    private fun moveItemBy(itemId: ChecklistItemId, step: Int) {
        val section = currentDetail()?.sections?.firstOrNull { s -> s.items.any { it.id == itemId } } ?: return
        val pending = pendingDeletes.value
        val visible = section.items.filterNot { it.id in pending }
        val neighbour = visible.getOrNull(visible.indexOfFirst { it.id == itemId } + step) ?: return
        // Positions count hidden (pending delete) items too, so target the neighbour's real index.
        val target = section.items.indexOfFirst { it.id == neighbour.id }
        launchWrite { moveItem(itemId, target).reportFailure() }
    }

    private fun moveSectionBy(sectionId: SectionId, step: Int) {
        val sections = currentDetail()?.sections ?: return
        val index = sections.indexOfFirst { it.id == sectionId }
        if (index < 0) return
        val target = index + step
        if (target !in sections.indices) return
        launchWrite { moveSection(sectionId, target).reportFailure() }
    }

    private fun confirmRename() {
        val dialog = rename.value ?: return
        if (dialog.isSaving) return
        val detail = currentDetail() ?: return
        rename.value = dialog.copy(isSaving = true)
        launchWrite {
            when (val result = updateChecklist(id, dialog.title, detail.checklist.description)) {
                is DomainResult.Success -> setRename(null)
                is DomainResult.Failure -> {
                    val error = result.error
                    val message = if (error is DomainError.Invalid) {
                        (error.errors.firstOrNull { it.field == Field.TITLE } ?: error.errors.first()).toUiText()
                    } else {
                        error.toUiText()
                    }
                    rename.value = dialog.copy(isSaving = false, error = message)
                }
            }
        }
    }

    /** Shows, changes or closes the rename dialog and mirrors its text into saved state. */
    private fun setRename(dialog: RenameDialogUi?) {
        rename.value = dialog
        savedState[KEY_RENAME] = dialog?.title
    }

    private fun currentDetail(): ChecklistDetail? = (detail.value as? Load.Loaded)?.detail

    private fun launchWrite(block: suspend CoroutineScope.() -> Unit) {
        viewModelScope.launch(block = block)
    }

    private suspend fun DomainResult<*>.reportFailure() {
        if (this is DomainResult.Failure) effects.send(ChecklistDetailEffect.Error(error.toUiText()))
    }

    private fun ChecklistSection.toUi(
        index: Int,
        count: Int,
        pending: Set<ChecklistItemId>,
        units: Map<UnitCode, UnitDef>,
    ): SectionUi {
        val visible = items.filterNot { it.id in pending }
        return SectionUi(
            id = id,
            name = category.displayName,
            icon = category.iconKey,
            items = visible.mapIndexed { i, item -> item.toUi(units, canMoveUp = i > 0, canMoveDown = i < visible.lastIndex) },
            canMoveUp = index > 0,
            canMoveDown = index < count - 1,
        )
    }

    private fun ChecklistItem.toUi(units: Map<UnitCode, UnitDef>, canMoveUp: Boolean, canMoveDown: Boolean) = ItemUi(
        id = id,
        name = displayName,
        quantity = quantity,
        // An unknown code still shows (as its code) rather than silently dropping the unit.
        unit = unit?.let { units[it] ?: UnitDef(it, allowsDecimal = true) },
        notes = notes,
        isCompleted = isCompleted,
        canMoveUp = canMoveUp,
        canMoveDown = canMoveDown,
    )

    internal companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val KEY_RENAME = "detail_rename_title"
    }
}
