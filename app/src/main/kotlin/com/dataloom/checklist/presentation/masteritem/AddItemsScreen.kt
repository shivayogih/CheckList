package com.dataloom.checklist.presentation.masteritem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dataloom.checklist.R
import com.dataloom.checklist.domain.model.MasterItemId
import com.dataloom.checklist.domain.model.UnitDef
import com.dataloom.checklist.presentation.common.asString
import com.dataloom.checklist.presentation.common.countText
import com.dataloom.checklist.presentation.common.resolve
import com.dataloom.checklist.presentation.components.AppTopBar
import com.dataloom.checklist.presentation.components.BackButton
import com.dataloom.checklist.presentation.components.CheckRow
import com.dataloom.checklist.presentation.components.UnitButton
import com.dataloom.checklist.presentation.components.UnitPickerDialog
import com.dataloom.checklist.presentation.components.optionalText

/**
 * [onCreateCustom] opens the custom item form with the typed text as its name (journey J2).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemsScreen(
    checklistId: String,
    sectionId: String,
    onDone: () -> Unit,
    onCreateCustom: (String) -> Unit,
    viewModel: AddItemsViewModel = hiltViewModel<AddItemsViewModel, AddItemsViewModel.Factory>(
        creationCallback = { factory -> factory.create(checklistId, sectionId) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val onAction = viewModel::onAction
    var unitPickerFor by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is AddItemsEffect.Added, AddItemsEffect.SectionGone -> onDone()
                is AddItemsEffect.AlreadyOnList -> snackbarHostState.showSnackbar(
                    resources.getString(R.string.add_items_already_on_list, effect.names.joinToString(", ")),
                )
                is AddItemsEffect.Error -> snackbarHostState.showSnackbar(effect.message.resolve(resources))
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.add_items_title, state.sectionName),
                navigationIcon = { BackButton(onDone) },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = { onAction(AddItemsAction.AddSelected) },
                    enabled = !state.isSaving && state.selectedCount > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                        .heightIn(min = 56.dp),
                ) {
                    Text(countText(R.plurals.add_selected_items, state.selectedCount))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item { SearchBar(state, onAction) }
            items(state.rows, key = { it.id.value }) { row ->
                ItemOption(
                    row = row,
                    unit = state.units.firstOrNull { it.code == row.unit },
                    onAction = onAction,
                    onPickUnit = { unitPickerFor = row.id.value },
                )
            }
            if (state.rows.isEmpty()) {
                item {
                    Text(
                        stringResource(if (state.query.isBlank()) R.string.add_items_type_to_search else R.string.add_items_no_match),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            item {
                val name = state.query.trim()
                OutlinedButton(
                    onClick = { onCreateCustom(if (state.canCreateCustom) name else "") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .heightIn(min = 48.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                    Text(
                        text = if (state.canCreateCustom) {
                            stringResource(R.string.add_items_create_named, name)
                        } else {
                            stringResource(R.string.add_items_create_own)
                        },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }

    val pickerRow = state.rows.firstOrNull { it.id.value == unitPickerFor }
    if (pickerRow != null) {
        UnitPickerDialog(
            units = state.units,
            selected = pickerRow.unit,
            onSelect = {
                onAction(AddItemsAction.UnitChanged(pickerRow.id, it))
                unitPickerFor = null
            },
            onDismiss = { unitPickerFor = null },
        )
    }
}

@Composable
private fun SearchBar(state: AddItemsUiState, onAction: (AddItemsAction) -> Unit) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { onAction(AddItemsAction.QueryChanged(it)) },
            label = { Text(stringResource(R.string.add_items_search_label)) },
            singleLine = true,
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
            trailingIcon = if (state.query.isEmpty()) {
                null
            } else {
                {
                    IconButton(onClick = { onAction(AddItemsAction.QueryChanged("")) }) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.search_clear))
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth(),
        )
        FilterChip(
            selected = state.allCategories,
            onClick = { onAction(AddItemsAction.AllCategoriesChanged(!state.allCategories)) },
            label = { Text(stringResource(R.string.add_items_all_categories)) },
            modifier = Modifier.heightIn(min = 48.dp),
        )
    }
}

@Composable
private fun ItemOption(
    row: MasterItemRowUi,
    unit: UnitDef?,
    onAction: (AddItemsAction) -> Unit,
    onPickUnit: () -> Unit,
) {
    Column {
        CheckRow(label = row.name, checked = row.selected, onCheckedChange = { onAction(AddItemsAction.ToggleItem(row.id)) })
        if (row.selected) {
            QuantityInputs(row.id, row.quantityText, row.error?.asString(), unit, onAction, onPickUnit)
        }
    }
}

@Composable
private fun QuantityInputs(
    id: MasterItemId,
    quantityText: String,
    errorText: String?,
    unit: UnitDef?,
    onAction: (AddItemsAction) -> Unit,
    onPickUnit: () -> Unit,
) {
    // Stacked rather than side by side so both stay usable at 200% font scale.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 56.dp, end = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedTextField(
            value = quantityText,
            onValueChange = { onAction(AddItemsAction.QuantityChanged(id, it)) },
            label = { Text(stringResource(R.string.item_quantity_optional)) },
            singleLine = true,
            isError = errorText != null,
            supportingText = optionalText(errorText),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { if (errorText != null) error(errorText) },
        )
        UnitButton(unit = unit, onClick = onPickUnit)
    }
}
