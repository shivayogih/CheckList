package com.dataloom.checklist.presentation.masteritem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dataloom.checklist.R
import com.dataloom.checklist.ads.AdPolicy
import com.dataloom.checklist.ads.NativeAdSlot
import com.dataloom.checklist.ads.rememberAdsVisible
import com.dataloom.checklist.domain.model.ChecklistItemId
import com.dataloom.checklist.presentation.common.dismissKeyboardOnOutsideInteraction
import com.dataloom.checklist.presentation.common.keyboardAwareScreen
import com.dataloom.checklist.presentation.common.resolve
import com.dataloom.checklist.presentation.components.AppTopBar
import com.dataloom.checklist.presentation.components.BackButton
import com.dataloom.checklist.presentation.components.CheckRow
import com.dataloom.checklist.presentation.components.OutlinedActionButton
import com.dataloom.checklist.presentation.components.SearchField

/**
 * Picking a suggestion adds it to the section and [onOpenItem] opens its details (amount, unit, note,
 * photos), CL-341. [onCreateCustom] opens the custom item form with the typed text as its name (journey J2).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemsScreen(
    checklistId: String,
    sectionId: String,
    onDone: () -> Unit,
    onCreateCustom: (String) -> Unit,
    onOpenItem: (ChecklistItemId) -> Unit,
    viewModel: AddItemsViewModel = hiltViewModel<AddItemsViewModel, AddItemsViewModel.Factory>(
        creationCallback = { factory -> factory.create(checklistId, sectionId) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val onAction = viewModel::onAction

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is AddItemsEffect.Added, AddItemsEffect.SectionGone -> onDone()
                is AddItemsEffect.OpenItem -> onOpenItem(effect.itemId)
                is AddItemsEffect.AlreadyOnList -> snackbarHostState.showSnackbar(
                    resources.getString(R.string.add_items_already_on_list, effect.names.joinToString(", ")),
                )
                is AddItemsEffect.Error -> snackbarHostState.showSnackbar(effect.message.resolve(resources))
            }
        }
    }

    Scaffold(
        modifier = Modifier.keyboardAwareScreen(),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.add_items_title, state.sectionName),
                navigationIcon = { BackButton(onDone) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        // Ads (CL-370): a native row after every 15 suggestions, only while browsing with the keyboard closed.
        val showNativeAds = rememberAdsVisible()
        // Suggestions fill as many columns as the width allows (two on most phones, more on tablets);
        // search, ads, hints and the create button span the full width.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = SuggestionMinWidth),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .dismissKeyboardOnOutsideInteraction(),
        ) {
            item(key = "search", contentType = "search", span = fullLine) { SearchBar(state, onAction) }
            val suggestion: @Composable (MasterItemRowUi) -> Unit = { row ->
                CheckRow(
                    label = row.name,
                    checked = false,
                    onCheckedChange = { if (!state.isSaving) onAction(AddItemsAction.PickItem(row.id)) },
                )
            }
            if (showNativeAds) {
                state.rows.forEachIndexed { index, row ->
                    item(key = row.id.value, contentType = "option") { suggestion(row) }
                    if (AdPolicy.hasNativeAdAfter(index, state.rows.size)) {
                        item(key = "native-ad-$index", contentType = "native-ad", span = fullLine) { NativeAdSlot() }
                    }
                }
            } else {
                items(state.rows, key = { it.id.value }, contentType = { "option" }) { suggestion(it) }
            }
            if (state.rows.isEmpty()) {
                item(key = "hint", contentType = "hint", span = fullLine) {
                    Text(
                        stringResource(if (state.query.isBlank()) R.string.add_items_type_to_search else R.string.add_items_no_match),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            item(key = "create", contentType = "create", span = fullLine) {
                val name = state.query.trim()
                OutlinedActionButton(
                    text = if (state.canCreateCustom) {
                        stringResource(R.string.add_items_create_named, name)
                    } else {
                        stringResource(R.string.add_items_create_own)
                    },
                    leadingIcon = painterResource(R.drawable.ic_add),
                    onClick = { onCreateCustom(if (state.canCreateCustom) name else "") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            }
        }
    }
}

private val SuggestionMinWidth = 160.dp

private val fullLine: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

@Composable
private fun SearchBar(state: AddItemsUiState, onAction: (AddItemsAction) -> Unit) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchField(
            value = state.query,
            onValueChange = { onAction(AddItemsAction.QueryChanged(it)) },
            placeholder = stringResource(R.string.add_items_search_label),
            clearContentDescription = stringResource(R.string.search_clear),
        )
        FilterChip(
            selected = state.allCategories,
            onClick = { onAction(AddItemsAction.AllCategoriesChanged(!state.allCategories)) },
            label = { Text(stringResource(R.string.add_items_all_categories)) },
            modifier = Modifier.heightIn(min = 48.dp),
        )
    }
}
