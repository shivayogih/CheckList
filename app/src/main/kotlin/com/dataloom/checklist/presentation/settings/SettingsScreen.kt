package com.dataloom.checklist.presentation.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dataloom.checklist.BuildConfig
import com.dataloom.checklist.R
import com.dataloom.checklist.domain.localization.LanguagePreference
import com.dataloom.checklist.localization.AppLocales
import com.dataloom.checklist.presentation.components.AppLogo
import com.dataloom.checklist.presentation.components.AppTopBar
import com.dataloom.checklist.presentation.components.BackButton
import com.dataloom.checklist.presentation.components.SectionHeader
import com.dataloom.checklist.presentation.components.SettingsGroup
import com.dataloom.checklist.presentation.components.SettingsRow
import com.dataloom.checklist.presentation.components.SettingsSwitchRow
import com.dataloom.checklist.presentation.theme.Dimens
import com.dataloom.checklist.presentation.transfer.ExportOptionsDialog
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
    var showExportOptions by rememberSaveable { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(TransferDocuments.createJson()) { uri ->
        if (uri != null) transferViewModel.exportTo(uri)
    }
    val exportZipLauncher = rememberLauncherForActivityResult(TransferDocuments.createZip()) { uri ->
        if (uri != null) transferViewModel.exportTo(uri, includePhotos = true)
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
            AppTopBar(
                title = stringResource(R.string.settings_title),
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
                .padding(horizontal = Dimens.ScreenPadding)
                .padding(bottom = Dimens.Space24),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space4),
        ) {
            SectionHeader(stringResource(R.string.settings_section_general))
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.settings_language),
                    icon = painterResource(R.drawable.ic_translate),
                    value = languageSummary,
                    onClick = onOpenLanguage,
                )
                SettingsRow(
                    title = stringResource(R.string.settings_profile),
                    icon = painterResource(R.drawable.ic_person),
                    value = stringResource(R.string.settings_profile_summary),
                    onClick = onOpenProfile,
                )
                SettingsRow(
                    title = stringResource(R.string.settings_show_tutorial),
                    icon = painterResource(R.drawable.ic_info),
                    value = stringResource(R.string.settings_show_tutorial_summary),
                    onClick = onShowTutorial,
                    showDivider = false,
                )
            }

            SectionHeader(stringResource(R.string.settings_section_data))
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.settings_export_all),
                    icon = painterResource(R.drawable.ic_file_upload),
                    value = stringResource(R.string.settings_export_all_summary),
                    onClick = if (transferState.busy) {
                        null
                    } else {
                        { showExportOptions = true }
                    },
                )
                SettingsRow(
                    title = stringResource(R.string.settings_import),
                    icon = painterResource(R.drawable.ic_file_download),
                    value = stringResource(R.string.settings_import_summary),
                    onClick = if (transferState.busy) {
                        null
                    } else {
                        { importLauncher.launch(TransferDocuments.OPEN_MIME_TYPES) }
                    },
                )
                // Where item photos live and when they leave the phone (CL-215). Information only.
                SettingsRow(
                    title = stringResource(R.string.settings_privacy_photos_title),
                    icon = painterResource(R.drawable.ic_shield),
                    value = stringResource(R.string.settings_privacy_photos_body),
                    showDivider = false,
                )
            }

            SectionHeader(stringResource(R.string.settings_section_ai))
            SettingsGroup {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_ai_enabled),
                    subtitle = stringResource(R.string.settings_ai_enabled_summary),
                    icon = painterResource(R.drawable.ic_auto_awesome),
                    checked = aiState.enabled,
                    enabled = aiState.isLoaded,
                    onCheckedChange = aiSettingsViewModel::setEnabled,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_ai_auto_add),
                    subtitle = stringResource(R.string.settings_ai_auto_add_summary),
                    icon = painterResource(R.drawable.ic_playlist_add),
                    checked = aiState.autoAdd,
                    enabled = aiState.autoAddAvailable,
                    onCheckedChange = aiSettingsViewModel::setAutoAdd,
                    showDivider = false,
                )
            }

            SectionHeader(stringResource(R.string.settings_about))
            SettingsGroup {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) { }
                        .padding(vertical = Dimens.Space12),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Space16),
                ) {
                    AppLogo(size = Dimens.SettingsAboutLogo)
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.pdf_tagline),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showExportOptions) {
        ExportOptionsDialog(
            onExport = { includePhotos ->
                showExportOptions = false
                if (includePhotos) {
                    exportZipLauncher.launch(TransferDocuments.exportZipFileName())
                } else {
                    exportLauncher.launch(TransferDocuments.exportFileName())
                }
            },
            onDismiss = { showExportOptions = false },
        )
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
