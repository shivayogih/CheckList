package com.dataloom.checklist.presentation.transfer

import com.dataloom.checklist.R
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.transfer.ExportResult
import com.dataloom.checklist.domain.transfer.ImportRejection
import com.dataloom.checklist.domain.transfer.ImportSummary
import com.dataloom.checklist.domain.transfer.TransferLimit
import com.dataloom.checklist.presentation.common.UiText
import org.junit.Assert.assertEquals
import org.junit.Test

class TransferMessagesTest {

    @Test
    fun `a file from a newer app asks for an update, an unknown version does not`() {
        assertEquals(
            UiText(R.string.import_error_newer_app),
            ImportRejection.UnsupportedVersion(formatVersion = 9, schemaVersion = 9, requiresNewerApp = true).toUiText(),
        )
        assertEquals(
            UiText(R.string.import_error_unsupported),
            ImportRejection.UnsupportedVersion(formatVersion = 0, schemaVersion = 1, requiresNewerApp = false).toUiText(),
        )
    }

    @Test
    fun `content problems report the total count, not just the listed ones`() {
        assertEquals(
            UiText(R.string.import_error_invalid, listOf(57)),
            ImportRejection.Invalid(issues = emptyList(), totalIssues = 57).toUiText(),
        )
    }

    @Test
    fun `limits and sizes share the too-large message`() {
        assertEquals(UiText(R.string.import_error_too_large), ImportRejection.FileTooLarge.toUiText())
        assertEquals(
            UiText(R.string.import_error_too_large),
            ImportRejection.LimitExceeded(TransferLimit.ITEMS, 10_000).toUiText(),
        )
    }

    @Test
    fun `export and import outcomes carry their counts`() {
        assertEquals(
            UiText(R.string.export_done, listOf(3, 42)),
            ExportResult.Exported(checklistCount = 3, itemCount = 42, byteCount = 1_000).toUiText(),
        )
        assertEquals(UiText(R.string.export_nothing), ExportResult.NothingToExport.toUiText())
        val summary = ImportSummary(
            checklistIds = listOf(ChecklistId("a"), ChecklistId("b")),
            itemCount = 7,
            newCategoryCount = 1,
            newUnitCount = 0,
            renamedChecklists = emptyList(),
        )
        assertEquals(UiText(R.string.import_done, listOf(2, 7)), summary.toUiText())
    }
}
