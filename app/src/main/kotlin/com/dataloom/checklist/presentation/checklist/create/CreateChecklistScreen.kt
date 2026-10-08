package com.dataloom.checklist.presentation.checklist.create

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dataloom.checklist.R
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.presentation.category.CreateCategoryButton
import com.dataloom.checklist.presentation.category.NewCategoryDialog
import com.dataloom.checklist.presentation.category.categoryOptions
import com.dataloom.checklist.presentation.common.asString
import com.dataloom.checklist.presentation.common.resolve
import com.dataloom.checklist.presentation.components.AppTopBar
import com.dataloom.checklist.presentation.components.BackButton
import com.dataloom.checklist.presentation.components.optionalText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateChecklistScreen(
    onBack: () -> Unit,
    onCreated: (ChecklistId) -> Unit,
    viewModel: CreateChecklistViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val onAction = viewModel::onAction

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is CreateChecklistEffect.Created -> onCreated(effect.id)
                is CreateChecklistEffect.Error -> snackbarHostState.showSnackbar(effect.message.resolve(resources))
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.create_title),
                navigationIcon = { BackButton(onBack) },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = { onAction(CreateChecklistAction.Create) },
                    enabled = !state.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                        .heightIn(min = 56.dp),
                ) {
                    Text(stringResource(R.string.action_create))
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
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    TitleField(state, onAction)
                    OutlinedTextField(
                        value = state.description,
                        onValueChange = { onAction(CreateChecklistAction.DescriptionChanged(it)) },
                        label = { Text(stringResource(R.string.create_description_label)) },
                        isError = state.descriptionError != null,
                        supportingText = optionalText(state.descriptionError?.asString()),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            }
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.create_categories_heading),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(stringResource(R.string.create_categories_hint), style = MaterialTheme.typography.bodyMedium)
                }
            }
            categoryOptions(state.categories) { onAction(CreateChecklistAction.ToggleCategory(it)) }
            item {
                CreateCategoryButton(
                    onClick = { onAction(CreateChecklistAction.OpenNewCategory) },
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
    }

    state.newCategory?.let { dialog ->
        NewCategoryDialog(
            state = dialog,
            onNameChange = { onAction(CreateChecklistAction.NewCategoryNameChanged(it)) },
            onConfirm = { onAction(CreateChecklistAction.ConfirmNewCategory) },
            onDismiss = { onAction(CreateChecklistAction.DismissNewCategory) },
        )
    }
}

@Composable
private fun TitleField(state: CreateChecklistUiState, onAction: (CreateChecklistAction) -> Unit) {
    val errorText = state.titleError?.asString()
    val hint = when {
        errorText != null -> errorText
        state.titleAlreadyUsed -> stringResource(R.string.create_title_already_used)
        else -> null
    }
    OutlinedTextField(
        value = state.title,
        onValueChange = { onAction(CreateChecklistAction.TitleChanged(it)) },
        label = { Text(stringResource(R.string.create_title_label)) },
        singleLine = true,
        isError = errorText != null,
        supportingText = optionalText(hint),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Next,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { if (errorText != null) error(errorText) },
    )
}
