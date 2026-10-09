package com.dataloom.checklist.presentation.checklist.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dataloom.checklist.R
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.validation.FieldLimits
import com.dataloom.checklist.presentation.category.CreateCategoryButton
import com.dataloom.checklist.presentation.category.NewCategoryDialog
import com.dataloom.checklist.presentation.category.categoryOptions
import com.dataloom.checklist.presentation.common.asString
import com.dataloom.checklist.presentation.common.dismissKeyboardOnOutsideInteraction
import com.dataloom.checklist.presentation.common.keyboardAwareScreen
import com.dataloom.checklist.presentation.common.resolve
import com.dataloom.checklist.presentation.components.AppTopBar
import com.dataloom.checklist.presentation.components.BackButton
import com.dataloom.checklist.presentation.components.FormField
import com.dataloom.checklist.presentation.components.PrimaryButton

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
        modifier = Modifier.keyboardAwareScreen(),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.create_title),
                navigationIcon = { BackButton(onBack) },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                PrimaryButton(
                    text = stringResource(R.string.action_create),
                    onClick = { onAction(CreateChecklistAction.Create) },
                    enabled = state.canCreate,
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
            item {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TitleField(state, onAction)
                    FormField(
                        label = stringResource(R.string.create_description_label),
                        value = state.description,
                        onValueChange = { onAction(CreateChecklistAction.DescriptionChanged(it)) },
                        errorText = state.descriptionError?.asString(),
                        multiLine = true,
                        maxLength = FieldLimits.DESCRIPTION_MAX,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
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
    FormField(
        label = stringResource(R.string.create_title_label),
        value = state.title,
        onValueChange = { onAction(CreateChecklistAction.TitleChanged(it)) },
        errorText = state.titleError?.asString(),
        helperText = if (state.titleAlreadyUsed) stringResource(R.string.create_title_already_used) else null,
        maxLength = FieldLimits.TITLE_MAX,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Next,
        ),
    )
}
