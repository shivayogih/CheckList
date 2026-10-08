package com.dataloom.checklist.transfer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dataloom.checklist.BuildConfig
import com.dataloom.checklist.R
import com.dataloom.checklist.domain.model.ChecklistDetail
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.UnitCode
import com.dataloom.checklist.domain.transfer.ApplyImportUseCase
import com.dataloom.checklist.domain.transfer.ExportChecklistsUseCase
import com.dataloom.checklist.domain.transfer.ExportRequest
import com.dataloom.checklist.domain.transfer.ExportResult
import com.dataloom.checklist.domain.transfer.ImportPreview
import com.dataloom.checklist.domain.transfer.ImportPreviewResult
import com.dataloom.checklist.domain.transfer.ImportRejection
import com.dataloom.checklist.domain.transfer.ImportResult
import com.dataloom.checklist.domain.transfer.ImportSummary
import com.dataloom.checklist.domain.transfer.PreviewImportUseCase
import com.dataloom.checklist.domain.usecase.ObserveChecklistDetailUseCase
import com.dataloom.checklist.domain.usecase.ObserveUnitsUseCase
import com.dataloom.checklist.transfer.pdf.ChecklistPdfWriter
import com.dataloom.checklist.transfer.pdf.PdfOptions
import com.dataloom.checklist.transfer.pdf.UnitLabels
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.io.OutputStream
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** State for whichever screen hosts import/export (the screens themselves are Phase 3 work). */
data class TransferUiState(
    val busy: Boolean = false,
    /** Set after a successful preview until the user confirms or dismisses it. */
    val preview: ImportPreview? = null,
)

/** One-shot results for the hosting screen to show or act on. */
sealed interface TransferEffect {
    data class Exported(val result: ExportResult) : TransferEffect

    data class ImportRejected(val rejection: ImportRejection) : TransferEffect

    data class Imported(val summary: ImportSummary) : TransferEffect

    /** Start this chooser intent from the activity (`context.startActivity(intent)`). */
    data class Share(val intent: Intent) : TransferEffect

    data object PdfSaved : TransferEffect

    /** The checklist to print or share no longer exists. */
    data object ChecklistMissing : TransferEffect

    /** Writing the destination failed (storage full, provider gone, permission revoked). */
    data object WriteFailed : TransferEffect
}

/**
 * ViewModel hooks for import/export and PDF (section 20). A screen wires the Storage Access
 * Framework contracts from [TransferDocuments] to these functions and renders [state] and
 * [effects]; it never touches files or the database itself.
 *
 * - export: [exportTo] with the Uri from [TransferDocuments.createJson], or [shareExport] to send
 *   the .json file through the Sharesheet with a message and import steps
 * - import: [previewImport] with the Uri from [TransferDocuments.openDocument], then
 *   [confirmImport] or [dismissImport]
 * - PDF: [sharePdf] (Sharesheet) or [savePdfTo] with the Uri from [TransferDocuments.createPdf]
 *
 * Unit names in the PDF come from [UnitLabels]: screens pass labels built from their localized unit
 * strings; without them, custom units show their label and built-in units their lower-case code.
 */
@HiltViewModel
class TransferViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val exportChecklists: ExportChecklistsUseCase,
    private val previewImportUseCase: PreviewImportUseCase,
    private val applyImport: ApplyImportUseCase,
    private val observeDetail: ObserveChecklistDetailUseCase,
    private val observeUnits: ObserveUnitsUseCase,
    private val documents: DocumentAccess,
    private val pdfWriter: ChecklistPdfWriter,
    private val exportFiles: ExportFiles,
    private val sharer: FileSharer,
) : ViewModel() {

    private val _state = MutableStateFlow(TransferUiState())
    val state: StateFlow<TransferUiState> = _state.asStateFlow()

    private val _effects = Channel<TransferEffect>(Channel.BUFFERED)
    val effects: Flow<TransferEffect> = _effects.receiveAsFlow()

    private val locale: String get() = LocalizedResources.languageTag(context)

    /** Exports [checklistIds] (null: all, archived included) to a document the user created. */
    fun exportTo(uri: Uri, checklistIds: List<ChecklistId>? = null) = runBusy {
        val request = ExportRequest(checklistIds, locale = locale, appVersion = BuildConfig.VERSION_NAME)
        _effects.send(TransferEffect.Exported(exportChecklists(request, documents.exportSink(uri))))
    }

    /**
     * Exports [checklistIds] (null: all) to the app's share folder and emits a Sharesheet intent
     * whose subject and text say what the file is, link the store when `PLAY_STORE_URL` is set and
     * list the import steps. Emits [TransferEffect.Exported] when there was nothing to export.
     */
    fun shareExport(checklistIds: List<ChecklistId>? = null) = runBusy {
        val request = ExportRequest(checklistIds, locale = locale, appVersion = BuildConfig.VERSION_NAME)
        val file = exportFiles.newFile(TransferDocuments.exportFileName())
        val result = exportChecklists(request) { file.outputStream() }
        if (result !is ExportResult.Exported) {
            file.delete()
            return@runBusy _effects.send(TransferEffect.Exported(result))
        }
        val message = ExportShareText.build(exportShareStrings(), StoreLink.of(BuildConfig.PLAY_STORE_URL))
        _effects.send(TransferEffect.Share(sharer.shareIntent(file, TransferDocuments.JSON_MIME_TYPE, message.subject, message.text)))
    }

    private fun exportShareStrings(): ExportShareStrings {
        val resources = LocalizedResources.of(context)
        val app = resources.getString(R.string.app_name)
        return ExportShareStrings(
            subject = resources.getString(R.string.share_export_subject, app),
            intro = resources.getString(R.string.share_export_intro, app),
            getApp = { url -> resources.getString(R.string.share_export_get_app, app, url) },
            stepsTitle = resources.getString(R.string.share_export_steps_title),
            steps = listOf(
                resources.getString(R.string.share_export_step_install, app),
                resources.getString(R.string.share_export_step_open),
                resources.getString(R.string.share_export_step_choose),
                resources.getString(R.string.share_export_step_confirm),
            ),
        )
    }

    /** Reads and checks a picked file; nothing is written until [confirmImport]. */
    fun previewImport(uri: Uri) = runBusy {
        when (val result = previewImportUseCase(documents.importSource(uri), locale)) {
            is ImportPreviewResult.Ready -> _state.update { it.copy(preview = result.preview) }
            is ImportPreviewResult.Rejected -> _effects.send(TransferEffect.ImportRejected(result.rejection))
        }
    }

    fun confirmImport() {
        val preview = _state.value.preview ?: return
        runBusy {
            _state.update { it.copy(preview = null) }
            when (val result = applyImport(preview.validated, locale)) {
                is ImportResult.Imported -> _effects.send(TransferEffect.Imported(result.summary))
                is ImportResult.Rejected -> _effects.send(TransferEffect.ImportRejected(result.rejection))
            }
        }
    }

    fun dismissImport() {
        _state.update { it.copy(preview = null) }
    }

    /** Writes the PDF to the app's share folder and emits a Sharesheet intent for it. */
    fun sharePdf(checklistId: ChecklistId, options: PdfOptions = PdfOptions(), unitLabels: UnitLabels? = null) = runBusy {
        val detail = observeDetail(checklistId, locale).first() ?: return@runBusy _effects.send(TransferEffect.ChecklistMissing)
        val labels = unitLabels ?: defaultUnitLabels()
        val file = exportFiles.newFile(TransferDocuments.pdfFileName(detail.checklist.title))
        writePdf(detail, options, labels) { file.outputStream() }
        _effects.send(TransferEffect.Share(sharer.shareIntent(file, TransferDocuments.PDF_MIME_TYPE, detail.checklist.title)))
    }

    /** Saves the PDF to a document the user created with [TransferDocuments.createPdf]. */
    fun savePdfTo(uri: Uri, checklistId: ChecklistId, options: PdfOptions = PdfOptions(), unitLabels: UnitLabels? = null) = runBusy {
        val detail = observeDetail(checklistId, locale).first() ?: return@runBusy _effects.send(TransferEffect.ChecklistMissing)
        writePdf(detail, options, unitLabels ?: defaultUnitLabels()) { documents.exportSink(uri).openStream() }
        _effects.send(TransferEffect.PdfSaved)
    }

    private suspend fun writePdf(detail: ChecklistDetail, options: PdfOptions, labels: UnitLabels, open: () -> OutputStream) =
        withContext(Dispatchers.IO) { open().use { pdfWriter.write(detail, options, labels, it) } }

    private suspend fun defaultUnitLabels(): UnitLabels {
        val custom = observeUnits().first().filter { it.isCustom }.associate { it.code to it.customLabel }
        return UnitLabels { code: UnitCode -> custom[code] ?: code.value.lowercase(Locale.ROOT) }
    }

    private fun runBusy(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block()
            } catch (_: IOException) {
                _effects.send(TransferEffect.WriteFailed)
            } catch (_: SecurityException) {
                _effects.send(TransferEffect.WriteFailed)
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
