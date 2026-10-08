package com.dataloom.checklist.presentation.settings.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dataloom.checklist.R
import com.dataloom.checklist.presentation.common.UiText
import com.dataloom.checklist.presentation.common.asString
import com.dataloom.checklist.presentation.common.resolve
import com.dataloom.checklist.presentation.components.AppTopBar
import com.dataloom.checklist.presentation.components.BackButton
import com.dataloom.checklist.presentation.components.ConfirmDialog
import com.dataloom.checklist.presentation.components.optionalText

/** Settings > Your profile: optional name and contact details, kept encrypted on this phone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onBack: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val onAction = viewModel::onAction

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            val message = when (effect) {
                ProfileEffect.Saved -> resources.getString(R.string.profile_saved)
                ProfileEffect.Cleared -> resources.getString(R.string.profile_cleared)
                is ProfileEffect.Error -> effect.message.resolve(resources)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.profile_title),
                navigationIcon = { BackButton(onBack) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state.status) {
                ProfileStatus.RESET -> Notice(
                    text = stringResource(R.string.profile_reset_notice),
                    actionLabel = stringResource(R.string.profile_reset_dismiss),
                    onAction = { onAction(ProfileAction.AcknowledgeReset) },
                )
                ProfileStatus.UNAVAILABLE -> Notice(text = stringResource(R.string.profile_unavailable))
                ProfileStatus.LOADING, ProfileStatus.NOT_SET, ProfileStatus.AVAILABLE -> Unit
            }
            Text(stringResource(R.string.profile_intro), style = MaterialTheme.typography.bodyLarge)

            ProfileField(
                value = state.name,
                onValueChange = { onAction(ProfileAction.NameChanged(it)) },
                label = stringResource(R.string.profile_name),
                error = state.nameError,
                enabled = state.canEdit,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            )
            ProfileField(
                value = state.email,
                onValueChange = { onAction(ProfileAction.EmailChanged(it)) },
                label = stringResource(R.string.profile_email),
                error = state.emailError,
                enabled = state.canEdit,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            )
            ProfileField(
                value = state.phone,
                onValueChange = { onAction(ProfileAction.PhoneChanged(it)) },
                label = stringResource(R.string.profile_phone),
                error = state.phoneError,
                enabled = state.canEdit,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
            )
            ProfileField(
                value = state.address,
                onValueChange = { onAction(ProfileAction.AddressChanged(it)) },
                label = stringResource(R.string.profile_address),
                error = state.addressError,
                enabled = state.canEdit,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Default,
                ),
                singleLine = false,
            )

            Button(
                onClick = { onAction(ProfileAction.Save) },
                enabled = state.canEdit && !state.isSaving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text(stringResource(R.string.action_save))
            }
            if (state.canClear) {
                OutlinedButton(
                    onClick = { onAction(ProfileAction.RequestClear) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.profile_clear))
                }
            }
        }
    }

    if (state.showClearConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.profile_clear_title),
            message = stringResource(R.string.profile_clear_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { onAction(ProfileAction.ConfirmClear) },
            onDismiss = { onAction(ProfileAction.DismissClear) },
        )
    }
}

/** A highlighted message; announced by TalkBack when it appears. */
@Composable
private fun Notice(text: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            if (actionLabel != null) {
                TextButton(
                    onClick = onAction,
                    modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp),
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun ProfileField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: UiText?,
    enabled: Boolean,
    keyboardOptions: KeyboardOptions,
    singleLine: Boolean = true,
) {
    val errorText = error?.asString()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        enabled = enabled,
        isError = errorText != null,
        supportingText = optionalText(errorText),
        keyboardOptions = keyboardOptions,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { if (errorText != null) error(errorText) },
    )
}
