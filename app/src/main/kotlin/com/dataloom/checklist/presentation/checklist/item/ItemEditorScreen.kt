package com.dataloom.checklist.presentation.checklist.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dataloom.checklist.R
import com.dataloom.checklist.domain.validation.FieldLimits
import com.dataloom.checklist.presentation.common.UiText
import com.dataloom.checklist.presentation.common.asString
import com.dataloom.checklist.presentation.common.keyboardAwareScreen
import com.dataloom.checklist.presentation.common.resolve
import com.dataloom.checklist.presentation.common.scrollableForm
import com.dataloom.checklist.presentation.components.AppTopBar
import com.dataloom.checklist.presentation.components.BackButton
import com.dataloom.checklist.presentation.components.FormField
import com.dataloom.checklist.presentation.components.PrimaryButton
import com.dataloom.checklist.presentation.components.TextInputDialog
import com.dataloom.checklist.presentation.components.UnitButton
import com.dataloom.checklist.presentation.components.UnitPickerDialog
import com.dataloom.checklist.presentation.photos.PhotoAction
import com.dataloom.checklist.presentation.photos.PhotoFormState
import com.dataloom.checklist.presentation.photos.PhotoSourceSheet
import com.dataloom.checklist.presentation.photos.PhotosFormSection
import com.dataloom.checklist.presentation.photos.rememberPhotoAdder

/** [onSaved] returns to the checklist, also when the form was opened from "Add item". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditorScreen(
    checklistId: String,
    sectionId: String,
    itemId: String?,
    initialName: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ItemEditorViewModel = hiltViewModel<ItemEditorViewModel, ItemEditorViewModel.Factory>(
        creationCallback = { factory -> factory.create(checklistId, sectionId, itemId, initialName) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val onAction = viewModel::onAction
    var unitPickerOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                ItemEditorEffect.Saved -> onSaved()
                ItemEditorEffect.Gone -> onBack()
                is ItemEditorEffect.AlreadyOnList ->
                    snackbarHostState.showSnackbar(resources.getString(R.string.item_already_on_list, effect.name))
                is ItemEditorEffect.Error -> snackbarHostState.showSnackbar(effect.message.resolve(resources))
            }
        }
    }

    Scaffold(
        modifier = Modifier.keyboardAwareScreen(),
        topBar = {
            AppTopBar(
                title = if (state.isEditing) {
                    stringResource(R.string.item_edit_title)
                } else {
                    stringResource(R.string.item_new_title, state.sectionName)
                },
                navigationIcon = { BackButton(onBack) },
                roomyTitle = !state.isEditing,
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                PrimaryButton(
                    text = stringResource(if (state.isEditing) R.string.action_save else R.string.detail_add_item),
                    onClick = { onAction(ItemEditorAction.Save) },
                    enabled = state.canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .scrollableForm()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EditorField(
                value = state.name,
                onValueChange = { onAction(ItemEditorAction.NameChanged(it)) },
                label = stringResource(R.string.item_name_label),
                error = state.nameError,
                maxLength = FieldLimits.ITEM_NAME_MAX,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            )
            EditorField(
                value = state.quantityText,
                onValueChange = { onAction(ItemEditorAction.QuantityChanged(it)) },
                label = stringResource(R.string.item_quantity_optional),
                error = state.quantityError,
                supporting = stringResource(R.string.item_quantity_hint),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            )
            UnitButton(unit = state.selectedUnit, onClick = { unitPickerOpen = true })
            state.unitError?.let {
                // Not attached to a text field, so it is announced when it appears.
                Text(
                    it.asString(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            EditorField(
                value = state.notes,
                onValueChange = { onAction(ItemEditorAction.NotesChanged(it)) },
                label = stringResource(R.string.item_notes_label),
                error = state.notesError,
                maxLength = FieldLimits.NOTES_MAX,
                singleLine = false,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            ItemPhotos(state.photoForm, onAction)
            if (!state.isEditing) {
                SaveToSuggestionsRow(state.saveToSuggestions, state.sectionName) {
                    onAction(ItemEditorAction.SaveToSuggestionsChanged(it))
                }
            }
        }
    }

    if (unitPickerOpen) {
        UnitPickerDialog(
            units = state.units,
            selected = state.unit,
            onSelect = {
                onAction(ItemEditorAction.UnitChanged(it))
                unitPickerOpen = false
            },
            onDismiss = { unitPickerOpen = false },
            onCreateUnit = {
                unitPickerOpen = false
                onAction(ItemEditorAction.OpenNewUnit)
            },
        )
    }

    state.newUnit?.let { dialog ->
        TextInputDialog(
            title = stringResource(R.string.unit_create),
            label = stringResource(R.string.unit_label),
            value = dialog.label,
            errorText = dialog.error?.asString(),
            confirmLabel = stringResource(R.string.action_create),
            enabled = dialog.canConfirm,
            maxLength = FieldLimits.UNIT_LABEL_MAX,
            onValueChange = { onAction(ItemEditorAction.NewUnitLabelChanged(it)) },
            onConfirm = { onAction(ItemEditorAction.ConfirmNewUnit) },
            onDismiss = { onAction(ItemEditorAction.DismissNewUnit) },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(
                        value = dialog.allowsDecimal,
                        role = Role.Checkbox,
                        onValueChange = { onAction(ItemEditorAction.NewUnitDecimalChanged(it)) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = dialog.allowsDecimal, onCheckedChange = null)
                Text(stringResource(R.string.unit_allows_decimal), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun EditorField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: UiText?,
    keyboardOptions: KeyboardOptions,
    supporting: String? = null,
    maxLength: Int? = null,
    singleLine: Boolean = true,
) {
    // The mockups' field: label above a rounded box (components/FormField.kt).
    FormField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        errorText = error?.asString(),
        helperText = supporting,
        multiLine = !singleLine,
        maxLength = maxLength,
        keyboardOptions = keyboardOptions,
    )
}

@Composable
private fun SaveToSuggestionsRow(checked: Boolean, sectionName: String, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.item_save_to_suggestions, sectionName),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** The "Photos (optional)" section with the picker and camera behind its "Add photo" tile. */
@Composable
private fun ItemPhotos(photos: PhotoFormState, onAction: (ItemEditorAction) -> Unit) {
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    val adder = rememberPhotoAdder(photos.freeSlots) { sources, onFinished ->
        onAction(ItemEditorAction.Photos(PhotoAction.Add(sources, onFinished)))
    }
    PhotosFormSection(
        photos = photos.photos,
        processing = photos.processing,
        message = photos.message,
        onAdd = { sheetOpen = true },
        onRemove = { onAction(ItemEditorAction.Photos(PhotoAction.Remove(it))) },
        onMove = { key, delta -> onAction(ItemEditorAction.Photos(PhotoAction.Move(key, delta))) },
    )
    if (sheetOpen) {
        PhotoSourceSheet(
            onTake = adder.takePhoto?.let { take ->
                {
                    sheetOpen = false
                    take()
                }
            },
            onPick = {
                sheetOpen = false
                adder.pickFromGallery()
            },
            onDismiss = { sheetOpen = false },
        )
    }
}
