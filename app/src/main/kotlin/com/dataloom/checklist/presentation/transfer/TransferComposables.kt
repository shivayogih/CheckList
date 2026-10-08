package com.dataloom.checklist.presentation.transfer

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.R
import com.dataloom.checklist.domain.model.UnitCode
import com.dataloom.checklist.domain.transfer.ImportPreview
import com.dataloom.checklist.presentation.common.UiText
import com.dataloom.checklist.presentation.common.builtInUnitLabel
import com.dataloom.checklist.presentation.common.resolve
import com.dataloom.checklist.transfer.TransferEffect
import com.dataloom.checklist.transfer.TransferViewModel
import com.dataloom.checklist.transfer.pdf.UnitLabels
import java.util.Locale

/**
 * Shows the outcome of export, import and PDF actions as snackbars, and starts the Sharesheet.
 * Every screen that hosts a [TransferViewModel] calls this once.
 */
@Composable
fun TransferEffects(viewModel: TransferViewModel, snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            val message: UiText? = when (effect) {
                is TransferEffect.Exported -> effect.result.toUiText()
                is TransferEffect.ImportRejected -> effect.rejection.toUiText()
                is TransferEffect.Imported -> effect.summary.toUiText()
                is TransferEffect.Share -> try {
                    context.startActivity(effect.intent)
                    null
                } catch (_: ActivityNotFoundException) {
                    UiText(R.string.share_no_app)
                }
                TransferEffect.PdfSaved -> UiText(R.string.pdf_saved)
                TransferEffect.ChecklistMissing -> UiText(R.string.error_not_found)
                TransferEffect.WriteFailed -> UiText(R.string.transfer_write_failed)
            }
            if (message != null) snackbarHostState.showSnackbar(message.resolve(resources))
        }
    }
}

/** "Import 2 checklists?" with what will be created; nothing is written until Import is tapped. */
@Composable
fun ImportPreviewDialog(preview: ImportPreview, busy: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_preview_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val style = MaterialTheme.typography.bodyLarge
                Text(stringResource(R.string.import_preview_checklists, preview.checklistCount), style = style)
                Text(
                    stringResource(R.string.import_preview_items, preview.itemCount, preview.completedItemCount),
                    style = style,
                )
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
