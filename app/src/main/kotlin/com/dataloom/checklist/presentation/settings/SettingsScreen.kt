package com.dataloom.checklist.presentation.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dataloom.checklist.BuildConfig
import com.dataloom.checklist.R
import com.dataloom.checklist.domain.localization.LanguagePreference
import com.dataloom.checklist.localization.AppLocales
import com.dataloom.checklist.presentation.components.AppLogo
import com.dataloom.checklist.presentation.components.BackButton
import com.dataloom.checklist.presentation.transfer.ImportPreviewDialog
import com.dataloom.checklist.presentation.transfer.TransferEffects
import com.dataloom.checklist.transfer.TransferDocuments
import com.dataloom.checklist.transfer.TransferViewModel

/**
 * Language, Your profile, export and import, the AI assistant switches, and About. Export and import go through the Storage
 * Access Framework: the user picks the file, so no storage permission is needed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenProfile: () -> Unit,
    onShowTutorial: () -> Unit,
    transferViewModel: TransferViewModel = hiltViewModel(),
    aiSettingsViewModel: AiSettingsViewModel = hiltViewModel(),
) {
    val transferState by transferViewModel.state.collectAsStateWithLifecycle()
    val aiState by aiSettingsViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    TransferEffects(transferViewModel, snackbarHostState)
    val exportLauncher = rememberLauncherForActivityResult(TransferDocuments.createJson()) { uri ->
        if (uri != null) transferViewModel.exportTo(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(TransferDocuments.openDocument()) { uri ->
        if (uri != null) transferViewModel.previewImport(uri)
    }
    val languageSummary = when (val preference = remember { AppLocales.current() }) {
        LanguagePreference.SystemDefault -> stringResource(R.string.settings_language_system)
        is LanguagePreference.Specific -> preference.language.nativeName
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_language)) },
                supportingContent = { Text(languageSummary) },
                modifier = Modifier
                    .heightIn(min = 64.dp)
                    .clickable(role = Role.Button, onClick = onOpenLanguage),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_profile)) },
                supportingContent = { Text(stringResource(R.string.settings_profile_summary)) },
                modifier = Modifier
                    .heightIn(min = 64.dp)
                    .clickable(role = Role.Button, onClick = onOpenProfile),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_export_all)) },
                supportingContent = { Text(stringResource(R.string.settings_export_all_summary)) },
                modifier = Modifier
                    .heightIn(min = 64.dp)
                    .clickable(
                        enabled = !transferState.busy,
                        role = Role.Button,
                        onClick = { exportLauncher.launch(TransferDocuments.exportFileName()) },
                    ),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_import)) },
                supportingContent = { Text(stringResource(R.string.settings_import_summary)) },
                modifier = Modifier
                    .heightIn(min = 64.dp)
                    .clickable(
                        enabled = !transferState.busy,
                        role = Role.Button,
                        onClick = { importLauncher.launch(TransferDocuments.OPEN_MIME_TYPES) },
                    ),
            )
            HorizontalDivider()
            SwitchRow(
                title = stringResource(R.string.settings_ai_enabled),
                summary = stringResource(R.string.settings_ai_enabled_summary),
                checked = aiState.enabled,
                enabled = aiState.isLoaded,
                onCheckedChange = aiSettingsViewModel::setEnabled,
            )
            SwitchRow(
                title = stringResource(R.string.settings_ai_auto_add),
                summary = stringResource(R.string.settings_ai_auto_add_summary),
                checked = aiState.autoAdd,
                enabled = aiState.autoAddAvailable,
                onCheckedChange = aiSettingsViewModel::setAutoAdd,
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_about)) },
                supportingContent = { Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)) },
                leadingContent = { AppLogo(size = 56.dp) },
                modifier = Modifier.heightIn(min = 64.dp),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_show_tutorial)) },
                supportingContent = { Text(stringResource(R.string.settings_show_tutorial_summary)) },
                modifier = Modifier
                    .heightIn(min = 64.dp)
                    .clickable(role = Role.Button, onClick = onShowTutorial),
            )
        }
    }

    transferState.preview?.let { preview ->
        ImportPreviewDialog(
            preview = preview,
            busy = transferState.busy,
            onConfirm = transferViewModel::confirmImport,
            onDismiss = transferViewModel::dismissImport,
        )
    }
}

/** A whole-row switch: TalkBack reads the title, the summary and "on"/"off" as one control. */
@Composable
private fun SwitchRow(title: String, summary: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        modifier = Modifier
            .heightIn(min = 64.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
    )
}
