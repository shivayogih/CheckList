package com.dataloom.checklist.presentation.checklist.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dataloom.checklist.R
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.ChecklistItemId
import com.dataloom.checklist.domain.model.SectionId
import com.dataloom.checklist.domain.validation.FieldLimits
import com.dataloom.checklist.presentation.ai.AiCommandAction
import com.dataloom.checklist.presentation.ai.AiCommandPanel
import com.dataloom.checklist.presentation.ai.AiCommandUiState
import com.dataloom.checklist.presentation.ai.AiCommandViewModel
import com.dataloom.checklist.presentation.ai.AiReviewSheet
import com.dataloom.checklist.presentation.common.asString
import com.dataloom.checklist.presentation.common.dismissKeyboardOnOutsideInteraction
import com.dataloom.checklist.presentation.common.keyboardAwareScreen
import com.dataloom.checklist.presentation.common.progressText
import com.dataloom.checklist.presentation.common.quantityText
import com.dataloom.checklist.presentation.common.resolve
import com.dataloom.checklist.presentation.components.AppTopBar
import com.dataloom.checklist.presentation.components.BackButton
import com.dataloom.checklist.presentation.components.ConfirmDialog
import com.dataloom.checklist.presentation.components.MenuAction
import com.dataloom.checklist.presentation.components.OverflowMenu
import com.dataloom.checklist.presentation.components.TextInputDialog
import com.dataloom.checklist.presentation.photos.ItemPhotoSlot
import com.dataloom.checklist.presentation.transfer.PdfOptionsDialog
import com.dataloom.checklist.presentation.transfer.TransferEffects
import com.dataloom.checklist.presentation.transfer.rememberUnitLabels
import com.dataloom.checklist.transfer.TransferDocuments
import com.dataloom.checklist.transfer.TransferViewModel
import com.dataloom.checklist.transfer.pdf.PdfOptions
import kotlinx.coroutines.launch

/** Navigation out of the detail screen. */
class ChecklistDetailNavigation(
    val onBack: () -> Unit,
    val onAddCategories: () -> Unit,
    val onAddItem: (SectionId) -> Unit,
    val onEditItem: (SectionId, ChecklistItemId) -> Unit,
)

@Composable
fun ChecklistDetailScreen(
    checklistId: String,
    navigation: ChecklistDetailNavigation,
    viewModel: ChecklistDetailViewModel = hiltViewModel<ChecklistDetailViewModel, ChecklistDetailViewModel.Factory>(
        creationCallback = { factory -> factory.create(checklistId) },
    ),
    transferViewModel: TransferViewModel = hiltViewModel(),
    aiViewModel: AiCommandViewModel = hiltViewModel<AiCommandViewModel, AiCommandViewModel.Factory>(
        creationCallback = { factory -> factory.create(checklistId) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val aiState by aiViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val onAction = remember(viewModel) { viewModel::onAction }

    // PDF share and save (section 20.4). Unit names follow the app language, as on screen.
    TransferEffects(transferViewModel, snackbarHostState)
    val transferState by transferViewModel.state.collectAsStateWithLifecycle()
    // Recomputed only when the sections change, not on every recomposition (rename dialog, snackbar...).
    val customUnitLabels = remember(state.sections) {
        state.sections
            .flatMap { section -> section.items.mapNotNull { it.unit } }
            .filter { it.customLabel != null }
            .associate { it.code to it.customLabel.orEmpty() }
    }
    val unitLabels = rememberUnitLabels(customUnitLabels)
    val id = remember(checklistId) { ChecklistId(checklistId) }
    // Asked only when the checklist has photos: the PDF is bigger with them (CL-214).
    var includePdfPhotos by rememberSaveable { mutableStateOf(false) }
    var pdfOptionsFor by rememberSaveable { mutableStateOf<PdfExport?>(null) }
    val savePdfLauncher = rememberLauncherForActivityResult(TransferDocuments.createPdf()) { uri ->
        if (uri != null) transferViewModel.savePdfTo(uri, id, PdfOptions(includePhotos = includePdfPhotos), unitLabels)
    }
    // The menu asks the ViewModel first, which commits deletions still waiting for Undo (CL-241).
    val pdfActions = listOf(
        MenuAction(stringResource(R.string.detail_share_pdf), enabled = !transferState.busy) {
            onAction(ChecklistDetailAction.ExportPdf(PdfExport.SHARE))
        },
        MenuAction(stringResource(R.string.detail_save_pdf), enabled = !transferState.busy) {
            onAction(ChecklistDetailAction.ExportPdf(PdfExport.SAVE))
        },
    )
    val exportPdf by rememberUpdatedState { export: PdfExport ->
        when (export) {
            PdfExport.SHARE -> transferViewModel.sharePdf(id, PdfOptions(includePhotos = includePdfPhotos), unitLabels)
            PdfExport.SAVE -> savePdfLauncher.launch(TransferDocuments.pdfFileName(state.title))
        }
    }

    // An Undo snackbar lost to a rotation or to leaving the screen cannot be answered any more.
    LaunchedEffect(viewModel) { onAction(ChecklistDetailAction.CommitPendingDeletes) }

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ChecklistDetailEffect.ItemDeleted -> {
                    // A new deletion closes the previous Undo snackbar, which commits that deletion.
                    snackbarHostState.currentSnackbarData?.dismiss()
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = resources.getString(R.string.detail_item_deleted, effect.name),
                            actionLabel = resources.getString(R.string.action_undo),
                            duration = SnackbarDuration.Long,
                        )
                        onAction(
                            if (result == SnackbarResult.ActionPerformed) {
                                ChecklistDetailAction.UndoDelete(effect.id)
                            } else {
                                ChecklistDetailAction.CommitDelete(effect.id)
                            },
                        )
                    }
                }
                ChecklistDetailEffect.ChecklistGone -> navigation.onBack()
                is ChecklistDetailEffect.PdfReady -> {
                    // The deletion is final now; its Undo snackbar would offer something it cannot do.
                    snackbarHostState.currentSnackbarData?.dismiss()
                    if (state.hasPhotos) pdfOptionsFor = effect.export else exportPdf(effect.export)
                }
                is ChecklistDetailEffect.Error -> scope.launch {
                    snackbarHostState.showSnackbar(effect.message.resolve(resources))
                }
            }
        }
    }

    DetailContent(state, snackbarHostState, onAction, navigation, pdfActions, aiState, aiViewModel::onAction)

    pdfOptionsFor?.let { export ->
        PdfOptionsDialog(
            confirmLabel = stringResource(
                if (export == PdfExport.SHARE) R.string.detail_share_pdf else R.string.detail_save_pdf,
            ),
            onConfirm = { includePhotos ->
                includePdfPhotos = includePhotos
                pdfOptionsFor = null
                exportPdf(export)
            },
            onDismiss = { pdfOptionsFor = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailContent(
    state: ChecklistDetailUiState,
    snackbarHostState: SnackbarHostState,
    onAction: (ChecklistDetailAction) -> Unit,
    navigation: ChecklistDetailNavigation,
    pdfActions: List<MenuAction>,
    aiState: AiCommandUiState,
    onAiAction: (AiCommandAction) -> Unit,
) {
    // The item whose photos are open in the viewer (id only, so it survives rotation).
    var viewerItemId by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        modifier = Modifier.keyboardAwareScreen(),
        topBar = {
            AppTopBar(
                title = state.title,
                navigationIcon = { BackButton(navigation.onBack) },
                actions = {
                    OverflowMenu(
                        contentDescription = stringResource(R.string.checklist_more_options, state.title),
                        actions = listOf(
                            MenuAction(stringResource(R.string.detail_rename)) { onAction(ChecklistDetailAction.StartRename) },
                            MenuAction(stringResource(R.string.add_categories_title), onClick = navigation.onAddCategories),
                        ) + pdfActions,
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .dismissKeyboardOnOutsideInteraction(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (!state.isLoading) {
                item(key = "progress", contentType = "progress") { ProgressHeader(state) }
            }
            // Only while the assistant is on in Settings; the screen works the same without it.
            if (!state.isLoading && aiState.isAvailable) {
                item(key = "ai-command", contentType = "ai-command") { AiCommandPanel(aiState, onAiAction) }
            }
            state.sections.forEach { section ->
                item(key = "header-${section.id.value}", contentType = "section-header") {
                    SectionHeader(section, onAction)
                }
                if (section.items.isEmpty()) {
                    item(key = "empty-${section.id.value}", contentType = "section-empty") {
                        Text(
                            stringResource(R.string.detail_section_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                items(section.items, key = { it.id.value }, contentType = { "item" }) { item ->
                    ItemRow(
                        item = item,
                        onAction = onAction,
                        onEdit = { navigation.onEditItem(section.id, item.id) },
                        onViewPhotos = { viewerItemId = item.id.value },
                    )
                }
                item(key = "add-${section.id.value}", contentType = "add-item") {
                    AddItemButton(section.name) { navigation.onAddItem(section.id) }
                    HorizontalDivider()
                }
            }
            if (!state.isLoading) {
                item(key = "footer", contentType = "footer") {
                    AddCategoriesFooter(state.sections.isEmpty(), navigation.onAddCategories)
                }
            }
        }
    }

    viewerItemId?.let { id -> ItemPhotoViewerHost(id, state.sections, onClose = { viewerItemId = null }) }

    aiState.review?.takeIf { aiState.isAvailable }?.let { review -> AiReviewSheet(review, onAiAction) }

    state.pendingSectionRemoval?.let { section ->
        ConfirmDialog(
            title = stringResource(R.string.detail_remove_section_title, section.name),
            message = stringResource(R.string.detail_remove_section_message),
            confirmLabel = stringResource(R.string.action_remove),
            onConfirm = { onAction(ChecklistDetailAction.ConfirmRemoveSection) },
            onDismiss = { onAction(ChecklistDetailAction.DismissRemoveSection) },
        )
    }

    state.rename?.let { dialog ->
        TextInputDialog(
            title = stringResource(R.string.detail_rename),
            label = stringResource(R.string.create_title_label),
            value = dialog.title,
            errorText = dialog.error?.asString(),
            confirmLabel = stringResource(R.string.action_save),
            enabled = dialog.canConfirm,
            maxLength = FieldLimits.TITLE_MAX,
            onValueChange = { onAction(ChecklistDetailAction.RenameChanged(it)) },
            onConfirm = { onAction(ChecklistDetailAction.ConfirmRename) },
            onDismiss = { onAction(ChecklistDetailAction.DismissRename) },
        )
    }
}

@Composable
private fun ProgressHeader(state: ChecklistDetailUiState) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        Text(
            text = if (state.totalItems == 0) {
                stringResource(R.string.progress_no_items)
            } else {
                progressText(state.completedItems, state.totalItems)
            },
            style = MaterialTheme.typography.titleMedium,
        )
        if (state.totalItems > 0) {
            // Exposes progressBarRangeInfo to TalkBack; the text above is the readable equivalent.
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SectionHeader(section: SectionUi, onAction: (ChecklistDetailAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(section.icon, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clearAndSetSemantics { })
        Text(
            section.name,
            style = MaterialTheme.typography.titleLarge,
            // Headings let TalkBack users jump between categories.
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        OverflowMenu(
            contentDescription = stringResource(R.string.detail_section_options, section.name),
            actions = listOf(
                MenuAction(stringResource(R.string.action_move_up), section.canMoveUp) {
                    onAction(ChecklistDetailAction.MoveSectionUp(section.id))
                },
                MenuAction(stringResource(R.string.action_move_down), section.canMoveDown) {
                    onAction(ChecklistDetailAction.MoveSectionDown(section.id))
                },
                MenuAction(stringResource(R.string.action_remove)) { onAction(ChecklistDetailAction.RequestRemoveSection(section)) },
            ),
        )
    }
}

@Composable
internal fun ItemRow(
    item: ItemUi,
    onAction: (ChecklistDetailAction) -> Unit,
    onEdit: () -> Unit,
    onViewPhotos: () -> Unit,
) {
    val state = stringResource(if (item.isCompleted) R.string.state_completed else R.string.state_not_completed)
    val quantity = quantityText(item.quantity, item.unit)
    val editLabel = stringResource(R.string.action_edit)
    val moveUpLabel = stringResource(R.string.action_move_up)
    val moveDownLabel = stringResource(R.string.action_move_down)
    val deleteLabel = stringResource(R.string.action_delete)
    val viewPhotosLabel = stringResource(R.string.photo_view_action)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
                // The whole row toggles, not just the box (section 10).
                .toggleable(
                    value = item.isCompleted,
                    role = Role.Checkbox,
                    onValueChange = { onAction(ChecklistDetailAction.ToggleItem(item.id, it)) },
                )
                .semantics(mergeDescendants = true) {
                    stateDescription = state
                    // TalkBack's actions menu offers what the visible "⋮" menu offers, without gestures.
                    customActions = buildList {
                        add(accessibilityAction(editLabel, onEdit))
                        if (item.photos.isNotEmpty()) add(accessibilityAction(viewPhotosLabel, onViewPhotos))
                        if (item.canMoveUp) add(accessibilityAction(moveUpLabel) { onAction(ChecklistDetailAction.MoveItemUp(item.id)) })
                        if (item.canMoveDown) {
                            add(accessibilityAction(moveDownLabel) { onAction(ChecklistDetailAction.MoveItemDown(item.id)) })
                        }
                        add(accessibilityAction(deleteLabel) { onAction(ChecklistDetailAction.DeleteItem(item)) })
                    }
                }
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Checkbox(checked = item.isCompleted, onCheckedChange = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    // Not colour alone: a tick, a strikethrough and the state description.
                    textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
                )
                if (quantity != null) Text(quantity, style = MaterialTheme.typography.bodyMedium)
                item.notes?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        ItemPhotoSlot(item.photos, item.name, onOpen = onViewPhotos)
        OverflowMenu(
            contentDescription = stringResource(R.string.item_more_options, item.name),
            actions = listOf(
                MenuAction(editLabel, onClick = onEdit),
                MenuAction(moveUpLabel, item.canMoveUp) { onAction(ChecklistDetailAction.MoveItemUp(item.id)) },
                MenuAction(moveDownLabel, item.canMoveDown) { onAction(ChecklistDetailAction.MoveItemDown(item.id)) },
                MenuAction(deleteLabel) { onAction(ChecklistDetailAction.DeleteItem(item)) },
            ),
        )
    }
}

@Composable
private fun AddItemButton(sectionName: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .heightIn(min = 48.dp),
    ) {
        Icon(painterResource(R.drawable.ic_add), contentDescription = null)
        // Names the category: there is one "Add item" button per section on the same screen.
        Text(stringResource(R.string.detail_add_item_to, sectionName), modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun AddCategoriesFooter(noSections: Boolean, onAddCategories: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (noSections) {
            Text(stringResource(R.string.detail_no_sections), style = MaterialTheme.typography.bodyLarge)
            Button(
                onClick = onAddCategories,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.add_categories_title)) }
        } else {
            OutlinedButton(
                onClick = onAddCategories,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.add_categories_title)) }
        }
    }
}

private fun accessibilityAction(label: String, action: () -> Unit) = CustomAccessibilityAction(label) {
    action()
    true
}
