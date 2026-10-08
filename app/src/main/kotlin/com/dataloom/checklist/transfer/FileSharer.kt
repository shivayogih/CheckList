package com.dataloom.checklist.transfer

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.dataloom.checklist.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/** Builds the Android Sharesheet intent for a file the app wrote. */
interface FileSharer {
    /** A chooser intent for the caller to start from an activity. */
    fun shareIntent(file: File, mimeType: String, subject: String?): Intent
}

/**
 * Shares through `ACTION_SEND` with a FileProvider Uri and a temporary read grant (section 19.3):
 * the receiving app can read this one file for as long as its activity lives, and nothing else.
 * No app-specific API (such as a WhatsApp integration) is used.
 */
class SharesheetFileSharer @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : FileSharer {

    override fun shareIntent(file: File, mimeType: String, subject: String?): Intent {
        val uri = FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            subject?.let {
                putExtra(Intent.EXTRA_SUBJECT, it)
                putExtra(Intent.EXTRA_TITLE, it)
            }
            // ClipData carries the grant to the chosen app on every Android version.
            clipData = ClipData.newRawUri(subject ?: file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val title = LocalizedResources.of(context).getString(R.string.share_checklist_chooser_title)
        return Intent.createChooser(send, title)
    }

    companion object {
        /** Matches the provider declared in AndroidManifest.xml. */
        const val AUTHORITY_SUFFIX = ".fileprovider"
    }
}

/**
 * Files made for sharing live in cacheDir/exports (the only directory the FileProvider exposes)
 * and are deleted once they are older than [MAX_AGE_MILLIS], each time a new one is made, so
 * shared copies never pile up. The system may also clear the cache at any time.
 */
class ExportFiles @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val directory: File get() = File(context.cacheDir, DIRECTORY).apply { mkdirs() }

    fun newFile(fileName: String): File {
        deleteStale()
        return File(directory, fileName)
    }

    fun deleteStale(now: Long = System.currentTimeMillis()) {
        directory.listFiles()?.filter { now - it.lastModified() > MAX_AGE_MILLIS }?.forEach { it.delete() }
    }

    companion object {
        const val DIRECTORY = "exports"
        const val MAX_AGE_MILLIS = 60 * 60 * 1000L
    }
}
