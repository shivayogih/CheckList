package com.dataloom.checklist.transfer

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import com.dataloom.checklist.domain.transfer.ExportSink
import com.dataloom.checklist.domain.transfer.ImportSource
import com.dataloom.checklist.domain.transfer.TransferFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

/**
 * Storage Access Framework entry points for import/export (section 20). No storage permission is
 * needed: the user picks the file, and the app only gets access to that one document.
 *
 * Screens register the contracts with `rememberLauncherForActivityResult` and pass the returned
 * Uri to [com.dataloom.checklist.transfer.TransferViewModel]:
 * - export JSON: [createJson], launched with [exportFileName],
 * - import JSON: [openDocument], launched with [OPEN_MIME_TYPES],
 * - save a PDF: [createPdf], launched with [pdfFileName].
 */
object TransferDocuments {

    const val JSON_MIME_TYPE = TransferFormat.MIME_TYPE
    const val PDF_MIME_TYPE = "application/pdf"

    /** Offered in the open dialog: some providers label .json files as text or as plain bytes. */
    val OPEN_MIME_TYPES = arrayOf(JSON_MIME_TYPE, "text/plain", "application/octet-stream")

    fun createJson() = ActivityResultContracts.CreateDocument(JSON_MIME_TYPE)

    fun createPdf() = ActivityResultContracts.CreateDocument(PDF_MIME_TYPE)

    /** Opens the saved PDF in the user's PDF viewer; start it and handle `ActivityNotFoundException`. */
    fun openPdfIntent(uri: Uri): Intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, PDF_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    fun openDocument() = ActivityResultContracts.OpenDocument()

    /** "CheckList-2026-10-08.json"; the user can rename it in the picker. */
    fun exportFileName(date: LocalDate = LocalDate.now()): String = "${TransferFormat.APP_NAME}-$date.json"

    fun pdfFileName(title: String): String = "${safeFileName(title)}.pdf"

    /**
     * Keeps letters and digits of every script plus space, '-' and '_'; anything else (path
     * separators, reserved characters, controls) becomes '_'. Never empty, at most 60 characters.
     */
    fun safeFileName(title: String): String {
        val cleaned = buildString {
            title.trim().forEach { ch ->
                append(if (ch.isLetterOrDigit() || ch == ' ' || ch == '-' || ch == '_' || isCombiningMark(ch)) ch else '_')
            }
        }.trim().take(MAX_FILE_NAME)
        return cleaned.ifBlank { TransferFormat.APP_NAME }
    }

    /** Indic vowel signs and viramas are marks, not letters, but are part of the word. */
    private fun isCombiningMark(ch: Char): Boolean = when (Character.getType(ch).toByte()) {
        Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK -> true
        else -> ch == '‌' || ch == '‍'
    }

    private const val MAX_FILE_NAME = 60
}

/** Turns picked document Uris into the domain's source and sink. */
interface DocumentAccess {
    fun importSource(uri: Uri): ImportSource

    fun exportSink(uri: Uri): ExportSink
}

class ContentResolverDocumentAccess @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DocumentAccess {

    override fun importSource(uri: Uri): ImportSource = object : ImportSource {
        // Read lazily: the use case asks on a background thread.
        override val sizeBytes: Long?
            get() = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                val column = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (column >= 0 && cursor.moveToFirst() && !cursor.isNull(column)) cursor.getLong(column) else null
            }

        override fun openStream(): InputStream =
            context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException("No stream for $uri")
    }

    override fun exportSink(uri: Uri): ExportSink = ExportSink {
        // "wt" truncates an existing file; a few providers only accept "w".
        val stream: OutputStream? = try {
            context.contentResolver.openOutputStream(uri, "wt")
        } catch (_: IllegalArgumentException) {
            context.contentResolver.openOutputStream(uri, "w")
        } catch (_: FileNotFoundException) {
            context.contentResolver.openOutputStream(uri, "w")
        }
        stream ?: throw FileNotFoundException("No stream for $uri")
    }
}

/**
 * Resources in the in-app language. On Android 12 and lower AppCompat applies the per-app language
 * to activities only, so text made outside an activity (PDF, share sheet title) uses this.
 */
internal object LocalizedResources {
    fun of(context: Context): Resources {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return context.resources
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(android.os.LocaleList.forLanguageTags(locales.toLanguageTags()))
        return context.createConfigurationContext(configuration).resources
    }

    /** Language tag of the app's current language, for name resolution and file metadata. */
    fun languageTag(context: Context): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        val locale = if (locales.isEmpty) context.resources.configuration.locales[0] else locales[0]
        return (locale ?: Locale.getDefault()).language
    }
}
