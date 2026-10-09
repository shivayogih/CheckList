package com.dataloom.checklist.presentation.category

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dataloom.checklist.R
import com.dataloom.checklist.presentation.common.countText
import com.dataloom.checklist.presentation.common.dismissKeyboardOnOutsideInteraction
import com.dataloom.checklist.presentation.common.keyboardAwareScreen
import com.dataloom.checklist.presentation.common.resolve
import com.dataloom.checklist.presentation.components.AppTopBar
import com.dataloom.checklist.presentation.components.BackButton
import com.dataloom.checklist.presentation.components.PrimaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCategoriesScreen(
    checklistId: String,
    onDone: () -> Unit,
    viewModel: AddCategoriesViewModel = hiltViewModel<AddCategoriesViewModel, AddCategoriesViewModel.Factory>(
        creationCallback = { factory -> factory.create(checklistId) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val onAction = viewModel::onAction

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is AddCategoriesEffect.Added, AddCategoriesEffect.ChecklistGone -> onDone()
                is AddCategoriesEffect.Error -> snackbarHostState.showSnackbar(effect.message.resolve(resources))
            }
        }
    }

    Scaffold(
        modifier = Modifier.keyboardAwareScreen(),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.add_categories_title),
                navigationIcon = { BackButton(onDone) },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                PrimaryButton(
                    text = countText(R.plurals.add_selected_categories, state.selectedCount),
                    onClick = { onAction(AddCategoriesAction.Save) },
                    enabled = !state.isSaving && state.selectedCount > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .dismissKeyboardOnOutsideInteraction(),
        ) {
            if (!state.isLoading && state.categories.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.add_categories_all_added),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            categoryOptions(state.categories) { onAction(AddCategoriesAction.ToggleCategory(it)) }
            item {
                CreateCategoryButton(
                    onClick = { onAction(AddCategoriesAction.OpenNewCategory) },
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
    }

    state.newCategory?.let { dialog ->
        NewCategoryDialog(
            state = dialog,
            onNameChange = { onAction(AddCategoriesAction.NewCategoryNameChanged(it)) },
            onConfirm = { onAction(AddCategoriesAction.ConfirmNewCategory) },
            onDismiss = { onAction(AddCategoriesAction.DismissNewCategory) },
        )
    }
}
