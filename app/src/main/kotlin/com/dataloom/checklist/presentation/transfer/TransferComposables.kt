package com.dataloom.checklist.presentation.transfer

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.R
import com.dataloom.checklist.domain.model.UnitCode
import com.dataloom.checklist.domain.transfer.ImportPreview
import com.dataloom.checklist.presentation.common.UiText
import com.dataloom.checklist.presentation.common.builtInUnitLabel
import com.dataloom.checklist.presentation.common.formatCount
import com.dataloom.checklist.presentation.common.resolve
import com.dataloom.checklist.presentation.components.DialogTitle
import com.dataloom.checklist.transfer.TransferDocuments
import com.dataloom.checklist.transfer.TransferEffect
import com.dataloom.checklist.transfer.TransferViewModel
import com.dataloom.checklist.transfer.pdf.UnitLabels
import java.util.Locale

/**
 * Shows the outcome of export, import and PDF actions as snackbars, and starts the Sharesheet.
 * Every screen that hosts a [TransferViewModel] calls this once. [onPdfExported] runs after a PDF was
 * handed to the Sharesheet (true) or saved (false); the checklist screen uses it for the ad rules (CL-370).
 */
@Composable
fun TransferEffects(
    viewModel: TransferViewModel,
    snackbarHostState: SnackbarHostState,
    onPdfExported: (afterShare: Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            var action: UiText? = null
            var onAction: () -> UiText? = { null }
            val message: UiText? = when (effect) {
                is TransferEffect.Exported -> effect.result.toUiText()
                is TransferEffect.ImportRejected -> effect.rejection.toUiText()
                is TransferEffect.Imported -> effect.summary.toUiText()
                is TransferEffect.Share -> startShare(context, effect, onPdfExported)
                is TransferEffect.PdfSaved -> {
                    action = UiText(R.string.pdf_open)
                    onAction = {
                        try {
                            context.startActivity(TransferDocuments.openPdfIntent(effect.uri))
                            null
                        } catch (_: ActivityNotFoundException) {
                            UiText(R.string.pdf_open_no_app)
                        }
                    }
                    onPdfExported(false)
                    UiText(R.string.pdf_saved)
                }
                TransferEffect.ChecklistMissing -> UiText(R.string.error_not_found)
                TransferEffect.WriteFailed -> UiText(R.string.transfer_write_failed)
            }
            if (message != null) {
                val result = snackbarHostState.showSnackbar(
                    message = message.resolve(resources),
                    actionLabel = action?.resolve(resources),
                    duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    onAction()?.let { snackbarHostState.showSnackbar(it.resolve(resources)) }
                }
            }
        }
    }
}

/** Opens the Sharesheet; returns the message to show when no app can take the file. */
private fun startShare(
    context: Context,
    effect: TransferEffect.Share,
    onPdfExported: (afterShare: Boolean) -> Unit,
): UiText? =
    try {
        context.startActivity(effect.intent)
        if (effect.isPdf) onPdfExported(true)
        null
    } catch (_: ActivityNotFoundException) {
        UiText(R.string.share_no_app)
    }

/** "Import 2 checklists?" with what will be created; nothing is written until Import is tapped. */
@Composable
fun ImportPreviewDialog(preview: ImportPreview, busy: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { DialogTitle(stringResource(R.string.import_preview_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val style = MaterialTheme.typography.bodyLarge
                Text(
                    stringResource(R.string.import_preview_checklists, formatCount(preview.checklistCount)),
                    style = style,
                )
                Text(
                    stringResource(
                        R.string.import_preview_items,
                        formatCount(preview.itemCount),
                        formatCount(preview.completedItemCount),
                    ),
                    style = style,
                )
                if (preview.photoCount > 0) {
                    Text(stringResource(R.string.import_preview_photos, formatCount(preview.photoCount)), style = style)
                }
                if (preview.newCategories.isNotEmpty()) {
                    Text(
                        stringResource(R.string.import_preview_new_categories, preview.newCategories.joinToString(", ")),
                        style = style,
                    )
                }
                if (preview.newUnits.isNotEmpty()) {
                    Text(stringResource(R.string.import_preview_new_units, preview.newUnits.joinToString(", ")), style = style)
                }
                preview.renamedChecklists.forEach { rename ->
                    Text(
                        stringResource(R.string.import_preview_renamed, rename.originalTitle, rename.importedTitle),
                        style = style,
                    )
                }
                if (preview.profileSkipped) {
                    Text(stringResource(R.string.import_preview_profile_skipped), style = style)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) { Text(stringResource(R.string.action_import)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * "Export all checklists" options: whether photos travel with the file. Photos make a .zip, so the
 * caller picks the matching document type from [TransferDocuments] for the answer.
 */
@Composable
fun ExportOptionsDialog(onExport: (includePhotos: Boolean) -> Unit, onDismiss: () -> Unit) {
    IncludePhotosDialog(
        title = stringResource(R.string.settings_export_all),
        summary = stringResource(R.string.export_include_photos_summary),
        confirmLabel = stringResource(R.string.action_export),
        onConfirm = onExport,
        onDismiss = onDismiss,
    )
}

/**
 * Shown before a PDF is shared or saved when the checklist has photos or a reminder. "Include photos"
 * (off by default, it makes the file bigger) appears when [offerPhotos]; "Include reminder" (on by
 * default) appears when the list has a reminder. [confirmLabel] names the chosen action ("Share as
 * PDF" or "Save as PDF").
 */
@Composable
fun PdfOptionsDialog(
    confirmLabel: String,
    offerPhotos: Boolean,
    offerReminder: Boolean,
    onConfirm: (includePhotos: Boolean, includeReminder: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var includePhotos by rememberSaveable { mutableStateOf(false) }
    var includeReminder by rememberSaveable { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pdf_options_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (offerPhotos) {
                    OptionSwitchRow(
                        title = stringResource(R.string.export_include_photos),
                        summary = stringResource(R.string.pdf_include_photos_summary),
                        checked = includePhotos,
                        onChange = { includePhotos = it },
                    )
                }
                if (offerReminder) {
                    OptionSwitchRow(
                        title = stringResource(R.string.pdf_include_reminder),
                        summary = stringResource(R.string.pdf_include_reminder_summary),
                        checked = includeReminder,
                        onChange = { includeReminder = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(offerPhotos && includePhotos, offerReminder && includeReminder) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun IncludePhotosDialog(
    title: String,
    summary: String,
    confirmLabel: String,
    onConfirm: (includePhotos: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var includePhotos by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OptionSwitchRow(
                title = stringResource(R.string.export_include_photos),
                summary = summary,
                checked = includePhotos,
                onChange = { includePhotos = it },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(includePhotos) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun OptionSwitchRow(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(
            value = checked,
            role = Role.Switch,
            onValueChange = onChange,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null, modifier = Modifier.padding(start = 16.dp))
    }
}

/**
 * Unit names for the PDF in the app language: built-in units from their translated strings,
 * custom units from [customLabels], anything else as its lower-case code.
 */
@Composable
fun rememberUnitLabels(customLabels: Map<UnitCode, String>): UnitLabels {
    val resources = LocalResources.current
    return remember(resources, customLabels) {
        UnitLabels { code ->
            val res = builtInUnitLabel(code)
            when {
                res != null -> resources.getString(res)
                else -> customLabels[code] ?: code.value.lowercase(Locale.ROOT)
            }
        }
    }
}
