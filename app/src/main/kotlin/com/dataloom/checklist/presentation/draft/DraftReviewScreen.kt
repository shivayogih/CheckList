package com.dataloom.checklist.presentation.draft

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dataloom.checklist.R
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.validation.FieldLimits
import com.dataloom.checklist.presentation.common.UiText
import com.dataloom.checklist.presentation.common.asString
import com.dataloom.checklist.presentation.common.dismissKeyboardOnOutsideInteraction
import com.dataloom.checklist.presentation.common.keyboardAwareScreen
import com.dataloom.checklist.presentation.components.AppIconButton
import com.dataloom.checklist.presentation.components.AppTopBar
import com.dataloom.checklist.presentation.components.BackButton
import com.dataloom.checklist.presentation.components.BottomActionBar
import com.dataloom.checklist.presentation.components.CheckRow
import com.dataloom.checklist.presentation.components.ConfirmDialog
import com.dataloom.checklist.presentation.components.FormField
import com.dataloom.checklist.presentation.components.OutlinedActionButton
import com.dataloom.checklist.presentation.components.PrimaryButton
import com.dataloom.checklist.presentation.components.TextInputDialog

/**
 * "Review draft" (CL-400): the checklist the assistant proposes for a Home request. Rename it, untick
 * items, remove a group or add your own items; Approve saves it and opens it. Leaving discards it
 * (after asking, once anything exists), and nothing was saved.
 */
@Composable
fun DraftReviewScreen(
    prompt: String,
    onBack: () -> Unit,
    onSaved: (ChecklistId) -> Unit,
    onCreateManually: () -> Unit,
    viewModel: DraftReviewViewModel = hiltViewModel<DraftReviewViewModel, DraftReviewViewModel.Factory>(
        creationCallback = { factory -> factory.create(prompt) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is DraftReviewEffect.Saved -> onSaved(effect.id)
            }
        }
    }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val leave = { if (state.hasDraft && !state.isSaving) confirmDiscard = true else onBack() }
    BackHandler(onBack = leave)
    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.draft_discard_title),
            message = stringResource(R.string.draft_discard_message),
            confirmLabel = stringResource(R.string.draft_discard_confirm),
            onConfirm = {
                confirmDiscard = false
                onBack()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
    DraftReviewContent(state, viewModel::onAction, onBack = leave, onCreateManually = onCreateManually)
}

@Composable
private fun DraftReviewContent(
    state: DraftReviewUiState,
    onAction: (DraftReviewAction) -> Unit,
    onBack: () -> Unit,
    onCreateManually: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.keyboardAwareScreen(),
        topBar = { AppTopBar(title = stringResource(R.string.draft_title), navigationIcon = { BackButton(onBack) }) },
        bottomBar = {
            if (state.hasDraft) {
                BottomActionBar {
                    state.saveError?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
                    PrimaryButton(
                        text = stringResource(R.string.draft_approve),
                        onClick = { onAction(DraftReviewAction.Approve) },
                        enabled = state.canApprove,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Working(Modifier.padding(padding))
            state.problem != null -> NotUnderstood(state, onBack, onCreateManually, Modifier.padding(padding))
            else -> DraftList(state, onAction, Modifier.padding(padding))
        }
    }
    state.addItem?.let { dialog ->
        TextInputDialog(
            title = stringResource(R.string.draft_add_item, dialog.sectionName),
            label = stringResource(R.string.draft_add_item_label),
            value = dialog.name,
            errorText = dialog.error?.asString(),
            confirmLabel = stringResource(R.string.draft_add_confirm),
            enabled = dialog.name.isNotBlank(),
            onValueChange = { onAction(DraftReviewAction.AddItemNameChanged(it)) },
            onConfirm = { onAction(DraftReviewAction.ConfirmAddItem) },
            onDismiss = { onAction(DraftReviewAction.DismissAddItem) },
            maxLength = FieldLimits.ITEM_NAME_MAX,
        )
    }
}

@Composable
private fun Working(modifier: Modifier) {
    val label = stringResource(R.string.draft_working)
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.semantics { contentDescription = label })
    }
}

/** Honest about what the offline assistant can do, with the manual way one tap away. */
@Composable
private fun NotUnderstood(
    state: DraftReviewUiState,
    onBack: () -> Unit,
    onCreateManually: () -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "problem") {
            Text(
                state.problem?.asString().orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        item(key = "help") { Text(stringResource(R.string.draft_help), style = MaterialTheme.typography.bodyLarge) }
        item(key = "manual") {
            PrimaryButton(
                stringResource(R.string.draft_create_manually),
                onClick = onCreateManually,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "retry") {
            OutlinedActionButton(
                stringResource(R.string.draft_try_again),
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DraftList(state: DraftReviewUiState, onAction: (DraftReviewAction) -> Unit, modifier: Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .dismissKeyboardOnOutsideInteraction(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item(key = "note", contentType = "note") {
            Text(
                stringResource(R.string.draft_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        item(key = "title", contentType = "title") {
            FormField(
                label = stringResource(R.string.draft_title_label),
                value = state.title,
                onValueChange = { onAction(DraftReviewAction.TitleChanged(it)) },
                errorText = state.titleError?.asString()
                    ?: stringResource(R.string.error_title_blank).takeIf { state.title.isBlank() },
                maxLength = FieldLimits.TITLE_MAX,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item(key = "count", contentType = "count") {
            Text(
                UiText(R.string.draft_selected_count, listOf(state.includedCount)).asString(),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        state.sections.forEach { section ->
            item(key = "section:${section.id}", contentType = "section") { SectionHeader(section, onAction) }
            items(section.items, key = { "item:${section.id}:${it.key}" }, contentType = { "item" }) { item ->
                CheckRow(
                    label = item.name,
                    checked = item.included,
                    onCheckedChange = { onAction(DraftReviewAction.ToggleItem(section.id, item.key, it)) },
                )
            }
        }
    }
}

/** Icon, name, then add and remove as icon-only buttons; the row wraps the name, never the buttons. */
@Composable
private fun SectionHeader(section: DraftSectionUi, onAction: (DraftReviewAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(section.icon, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clearAndSetSemantics { })
        Column(Modifier.weight(1f)) {
            Text(
                section.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
        }
        AppIconButton(
            icon = painterResource(R.drawable.ic_add),
            contentDescription = stringResource(R.string.draft_add_item, section.name),
            onClick = { onAction(DraftReviewAction.OpenAddItem(section.id)) },
        )
        AppIconButton(
            icon = painterResource(R.drawable.ic_delete),
            contentDescription = stringResource(R.string.draft_remove_section, section.name),
            onClick = { onAction(DraftReviewAction.RemoveSection(section.id)) },
        )
    }
}
