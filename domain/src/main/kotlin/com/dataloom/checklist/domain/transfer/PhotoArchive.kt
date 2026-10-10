package com.dataloom.checklist.domain.transfer

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

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

    /** Blocking; call on an I/O dispatcher. The caller deletes [scratch] on a rejection. */
    fun read(input: InputStream, scratch: File): ArchiveReadResult {
        val session = Session(scratch)
        return try {
            session.readAll(input)
            val json = session.json ?: throw Refused(ImportRejection.UnsafeArchive(ArchiveProblem.NOT_AN_ARCHIVE))
            ArchiveReadResult.Read(json, ImportArchive(scratch, session.photos))
        } catch (refused: Refused) {
            ArchiveReadResult.Rejected(refused.rejection)
        } catch (_: ZipException) {
            ArchiveReadResult.Rejected(ImportRejection.UnsafeArchive(ArchiveProblem.NOT_AN_ARCHIVE))
        } catch (_: EOFException) {
            ArchiveReadResult.Rejected(ImportRejection.UnsafeArchive(ArchiveProblem.NOT_AN_ARCHIVE))
        } catch (_: IOException) {
            ArchiveReadResult.Rejected(ImportRejection.Unreadable)
        }
    }

    /** Control flow for "this archive is refused", so each check can be a plain function. */
    private class Refused(val rejection: ImportRejection) : RuntimeException(null, null, false, false)

    private fun unsafe(problem: ArchiveProblem) = Refused(ImportRejection.UnsafeArchive(problem))

    private class Session(private val scratch: File) {
        var json: ByteArray? = null
            private set
        val photos = LinkedHashMap<String, File>()
        private val seen = HashSet<String>()
        private var entries = 0
        private var total = 0L
        private val buffer = ByteArray(BUFFER_SIZE)

        fun readAll(input: InputStream) {
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    readEntry(zip, entry.name)
                    entry = zip.nextEntry
                }
            }
        }

        private fun readEntry(zip: ZipInputStream, name: String) {
            if (++entries > TransferLimits.MAX_ARCHIVE_ENTRIES) {
                val max = TransferLimits.MAX_ARCHIVE_ENTRIES.toLong()
                throw Refused(ImportRejection.LimitExceeded(TransferLimit.PHOTOS, max))
            }
            when (val classified = ArchivePaths.classify(name)) {
                is ArchivePaths.Classified.Refused -> throw unsafe(classified.problem)
                is ArchivePaths.Classified.Ok -> when (val entry = classified.entry) {
                    ArchivePaths.Entry.Json -> {
                        markSeen(TransferFormat.ARCHIVE_JSON_ENTRY)
                        json = readJson(zip)
                    }
                    is ArchivePaths.Entry.Photo -> {
                        markSeen(entry.key)
                        photos[entry.key] = readPhoto(zip, entry)
                    }
                }
            }
        }

        private fun markSeen(key: String) {
            if (!seen.add(key)) throw unsafe(ArchiveProblem.DUPLICATE_ENTRY)
        }

        private fun readJson(zip: ZipInputStream): ByteArray {
            val out = ByteArrayOutputStream()
            copy(zip, TransferLimits.MAX_FILE_BYTES, TransferLimit.FILE_SIZE) { bytes, length ->
                out.write(bytes, 0, length)
            }
            return out.toByteArray()
        }

        private fun readPhoto(zip: ZipInputStream, entry: ArchivePaths.Entry.Photo): File {
            val target = File(scratch, entry.fileName.lowercase(Locale.ROOT))
            val head = ByteArray(ArchivePaths.MAGIC_BYTES)
            var headLength = 0
            target.outputStream().use { file ->
                copy(zip, TransferLimits.MAX_PHOTO_BYTES, TransferLimit.PHOTO_SIZE) { bytes, length ->
                    val take = minOf(length, head.size - headLength)
                    System.arraycopy(bytes, 0, head, headLength, take)
                    headLength += take
                    file.write(bytes, 0, length)
                }
            }
            if (!ArchivePaths.looksLikeImage(entry.extension, head, headLength)) {
                throw unsafe(ArchiveProblem.NOT_AN_IMAGE)
            }
            return target
        }

        /** Copies the current entry in chunks, counting what is really read against the entry and archive limits. */
        private inline fun copy(
            zip: ZipInputStream,
            entryMax: Long,
            entryLimit: TransferLimit,
            sink: (ByteArray, Int) -> Unit,
        ) {
            var entryBytes = 0L
            var read = zip.read(buffer)
            while (read >= 0) {
                entryBytes += read
                total += read
                if (entryBytes > entryMax) throw Refused(ImportRejection.LimitExceeded(entryLimit, entryMax))
                if (total > TransferLimits.MAX_ARCHIVE_BYTES) {
                    val max = TransferLimits.MAX_ARCHIVE_BYTES
                    throw Refused(ImportRejection.LimitExceeded(TransferLimit.ARCHIVE_SIZE, max))
                }
                sink(buffer, read)
                read = zip.read(buffer)
            }
        }
    }

    private const val BUFFER_SIZE = 32 * 1024
}
