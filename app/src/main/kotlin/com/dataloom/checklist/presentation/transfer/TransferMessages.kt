package com.dataloom.checklist.presentation.transfer

import com.dataloom.checklist.R
import com.dataloom.checklist.domain.transfer.ExportResult
import com.dataloom.checklist.domain.transfer.ImportRejection
import com.dataloom.checklist.domain.transfer.ImportSummary
import com.dataloom.checklist.presentation.common.UiText

/** Why a file was not imported, in words the user can act on. Nothing was written in any case. */
fun ImportRejection.toUiText(): UiText = when (this) {
    ImportRejection.FileTooLarge -> UiText(R.string.import_error_too_large)
    ImportRejection.Unreadable -> UiText(R.string.import_error_unreadable)
    ImportRejection.Malformed -> UiText(R.string.import_error_malformed)
    is ImportRejection.UnsupportedVersion ->
        UiText(if (requiresNewerApp) R.string.import_error_newer_app else R.string.import_error_unsupported)
    is ImportRejection.LimitExceeded -> UiText(R.string.import_error_too_large)
    is ImportRejection.UnsafeArchive -> UiText(R.string.import_error_unsafe_archive)
    ImportRejection.NothingToImport -> UiText(R.string.import_error_nothing)
    is ImportRejection.Invalid -> UiText(R.string.import_error_invalid, listOf(totalIssues))
}

fun ExportResult.toUiText(): UiText = when (this) {
    is ExportResult.Exported -> when {
        photoCount == 0 && skippedPhotoCount == 0 -> UiText(R.string.export_done, listOf(checklistCount, itemCount))
        skippedPhotoCount == 0 -> UiText(R.string.export_done_photos, listOf(checklistCount, itemCount, photoCount))
        else -> UiText(R.string.export_done_photos_skipped, listOf(checklistCount, itemCount, photoCount, skippedPhotoCount))
    }
    ExportResult.NothingToExport -> UiText(R.string.export_nothing)
    is ExportResult.TooLarge -> UiText(R.string.export_too_large)
}

fun ImportSummary.toUiText(): UiText = when {
    photoCount == 0 && skippedPhotoCount == 0 -> UiText(R.string.import_done, listOf(checklistIds.size, itemCount))
    skippedPhotoCount == 0 -> UiText(R.string.import_done_photos, listOf(checklistIds.size, itemCount, photoCount))
    else -> UiText(R.string.import_done_photos_skipped, listOf(checklistIds.size, itemCount, photoCount, skippedPhotoCount))
}
