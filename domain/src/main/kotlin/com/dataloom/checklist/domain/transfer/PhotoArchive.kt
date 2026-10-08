package com.dataloom.checklist.domain.transfer

import com.dataloom.checklist.domain.photo.PhotoStore
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/**
 * Names allowed inside a photo archive (docs/import-export-format.md, "Photo archive"). The same
 * rules judge zip entry names while reading and `items[].photos[].file` while validating, so a
 * hostile name is refused the same way wherever it appears.
 */
internal object ArchivePaths {

    sealed interface Entry {
        data object Json : Entry

        /** [key] is the lower-case `photos/<name>.<ext>`; [extension] is lower-case without the dot. */
        data class Photo(val key: String, val fileName: String, val extension: String) : Entry
    }

    sealed interface Classified {
        data class Ok(val entry: Entry) : Classified

        data class Refused(val problem: ArchiveProblem) : Classified
    }

    private val FILE_NAME = Regex("[A-Za-z0-9_][A-Za-z0-9_.-]{0,63}")

    fun classify(name: String): Classified {
        if (name.isEmpty()) return refused(ArchiveProblem.UNSAFE_PATH)
        // Absolute paths, Windows separators, drive letters and control characters never belong in a name.
        if (name.startsWith("/") || name.contains('\\') || name.contains(':') || name.any { it.isISOControl() }) {
            return refused(ArchiveProblem.UNSAFE_PATH)
        }
        val segments = name.split('/')
        if (segments.any { it == ".." || it == "." }) return refused(ArchiveProblem.UNSAFE_PATH)
        if (name.endsWith("/")) return refused(ArchiveProblem.UNEXPECTED_ENTRY)
        if (name == TransferFormat.ARCHIVE_JSON_ENTRY) return Classified.Ok(Entry.Json)
        if (segments.size > 2) return refused(ArchiveProblem.UNSAFE_PATH)
        if (segments.size != 2 || segments[0] != TransferFormat.ARCHIVE_PHOTO_DIRECTORY.removeSuffix("/")) {
            return refused(ArchiveProblem.UNEXPECTED_ENTRY)
        }
        val file = segments[1]
        if (!FILE_NAME.matches(file)) return refused(ArchiveProblem.UNSAFE_PATH)
        val extension = file.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)
        if (extension !in TransferFormat.PHOTO_EXTENSIONS) return refused(ArchiveProblem.DISALLOWED_EXTENSION)
        return Classified.Ok(Entry.Photo(key = name.lowercase(Locale.ROOT), fileName = file, extension = extension))
    }

    /** The lower-case lookup key of a photo path, or null when [path] is not a plain `photos/<name>.<ext>`. */
    fun photoKey(path: String): String? =
        ((classify(path) as? Classified.Ok)?.entry as? Entry.Photo)?.key

    private fun refused(problem: ArchiveProblem) = Classified.Refused(problem)

    /** True when [head] starts like a JPEG, PNG or WebP file of the family [extension] belongs to. */
    fun looksLikeImage(extension: String, head: ByteArray, length: Int): Boolean = when (extension) {
        "jpg", "jpeg" -> length >= 3 && head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() && head[2] == 0xFF.toByte()
        "png" -> length >= 8 && PNG_SIGNATURE.indices.all { head[it] == PNG_SIGNATURE[it] }
        "webp" -> length >= 12 &&
            String(head, 0, 4, Charsets.ISO_8859_1) == "RIFF" &&
            String(head, 8, 4, Charsets.ISO_8859_1) == "WEBP"
        else -> false
    }

    const val MAGIC_BYTES = 12

    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
}

/** The photos of an opened archive, as raw files in a scratch directory. Nothing here is trusted as an image yet. */
internal class ImportArchive(val directory: File, val photos: Map<String, File>)

internal sealed interface ArchiveReadResult {
    class Read(val json: ByteArray, val archive: ImportArchive) : ArchiveReadResult

    class Rejected(val rejection: ImportRejection) : ArchiveReadResult
}

/**
 * Reads a photo archive as a stream. Safe against hostile archives:
 * - sizes are counted from the bytes actually read; the sizes in zip headers are ignored,
 * - every entry name is checked by [ArchivePaths] before anything is written, and files in the
 *   scratch directory are named from the checked name only,
 * - duplicates (ignoring case), directories, other file types, wrong extensions and entries whose
 *   first bytes are not an image are refused,
 * - at most [TransferLimits.MAX_ARCHIVE_ENTRIES] entries, [TransferLimits.MAX_PHOTO_BYTES] per
 *   photo, [TransferLimits.MAX_FILE_BYTES] for the JSON and [TransferLimits.MAX_ARCHIVE_BYTES] in total.
 * Symbolic links need no special case: this streaming API cannot read the unix mode, entries are
 * only ever copied as bytes into a file with a name we chose, and a link payload (a path as text)
 * fails the image check.
 */
internal object ZipArchiveReader {

    private const val BUFFER_SIZE = 32 * 1024

    /** Blocking; call on an I/O dispatcher. The caller deletes [scratch] on a rejection. */
    fun read(input: InputStream, scratch: File): ArchiveReadResult {
        var json: ByteArray? = null
        val photos = LinkedHashMap<String, File>()
        val seen = HashSet<String>()
        var entries = 0
        var total = 0L
        try {
            ZipInputStream(input).use { zip ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++entries > TransferLimits.MAX_ARCHIVE_ENTRIES) {
                        return reject(ImportRejection.LimitExceeded(TransferLimit.PHOTOS, TransferLimits.MAX_ARCHIVE_ENTRIES.toLong()))
                    }
                    val classified = ArchivePaths.classify(entry.name)
                    if (classified is ArchivePaths.Classified.Refused) return unsafe(classified.problem)
                    val parsed = (classified as ArchivePaths.Classified.Ok).entry
                    val key = if (parsed is ArchivePaths.Entry.Photo) parsed.key else TransferFormat.ARCHIVE_JSON_ENTRY
                    if (!seen.add(key)) return unsafe(ArchiveProblem.DUPLICATE_ENTRY)

                    when (parsed) {
                        ArchivePaths.Entry.Json -> {
                            val out = java.io.ByteArrayOutputStream()
                            val outcome = copyBounded(zip, buffer, TransferLimits.MAX_FILE_BYTES, total, out::write)
                            if (outcome.exceeded != null) return reject(limit(outcome.exceeded, TransferLimit.FILE_SIZE))
                            total = outcome.total
                            json = out.toByteArray()
                        }
                        is ArchivePaths.Entry.Photo -> {
                            val target = File(scratch, parsed.fileName.lowercase(Locale.ROOT))
                            val head = ByteArray(ArchivePaths.MAGIC_BYTES)
                            var headLength = 0
                            val outcome = target.outputStream().use { file ->
                                copyBounded(zip, buffer, TransferLimits.MAX_PHOTO_BYTES, total) { bytes, offset, length ->
                                    if (headLength < head.size) {
                                        val take = minOf(length, head.size - headLength)
                                        System.arraycopy(bytes, offset, head, headLength, take)
                                        headLength += take
                                    }
                                    file.write(bytes, offset, length)
                                }
                            }
                            if (outcome.exceeded != null) return reject(limit(outcome.exceeded, TransferLimit.PHOTO_SIZE))
                            total = outcome.total
                            if (!ArchivePaths.looksLikeImage(parsed.extension, head, headLength)) return unsafe(ArchiveProblem.NOT_AN_IMAGE)
                            photos[parsed.key] = target
                        }
                    }
                }
            }
        } catch (_: ZipException) {
            return unsafe(ArchiveProblem.NOT_AN_ARCHIVE)
        } catch (_: EOFException) {
            return unsafe(ArchiveProblem.NOT_AN_ARCHIVE)
        } catch (_: IOException) {
            return reject(ImportRejection.Unreadable)
        }
        val bytes = json ?: return unsafe(ArchiveProblem.NOT_AN_ARCHIVE)
        return ArchiveReadResult.Read(bytes, ImportArchive(scratch, photos))
    }

    private class CopyOutcome(val total: Long, val exceeded: Long?)

    /**
     * Copies the current entry, counting what is read. [exceeded] is the limit that was passed (the
     * entry limit [entryMax] or the archive limit), or null.
     */
    private inline fun copyBounded(
        zip: ZipInputStream,
        buffer: ByteArray,
        entryMax: Long,
        totalSoFar: Long,
        write: (ByteArray, Int, Int) -> Unit,
    ): CopyOutcome {
        var entryBytes = 0L
        var total = totalSoFar
        while (true) {
            val read = zip.read(buffer)
            if (read < 0) break
            entryBytes += read
            total += read
            if (entryBytes > entryMax) return CopyOutcome(total, entryMax)
            if (total > TransferLimits.MAX_ARCHIVE_BYTES) return CopyOutcome(total, TransferLimits.MAX_ARCHIVE_BYTES)
            write(buffer, 0, read)
        }
        return CopyOutcome(total, null)
    }

    private fun limit(exceeded: Long, entryLimit: TransferLimit): ImportRejection.LimitExceeded =
        if (exceeded == TransferLimits.MAX_ARCHIVE_BYTES) {
            ImportRejection.LimitExceeded(TransferLimit.ARCHIVE_SIZE, exceeded)
        } else {
            ImportRejection.LimitExceeded(entryLimit, exceeded)
        }

    private fun reject(rejection: ImportRejection) = ArchiveReadResult.Rejected(rejection)

    private fun unsafe(problem: ArchiveProblem) = ArchiveReadResult.Rejected(ImportRejection.UnsafeArchive(problem))
}

/**
 * Remaining pieces of an archive import kept between the preview and the confirmation. Deleting
 * the scratch directory is [discard]'s job; [ValidatedImport.discard] calls it.
 */
internal class ArchiveHandle(val archive: ImportArchive, private val store: PhotoStore) {
    suspend fun discard() = store.deleteScratchDirectory(archive.directory)
}
