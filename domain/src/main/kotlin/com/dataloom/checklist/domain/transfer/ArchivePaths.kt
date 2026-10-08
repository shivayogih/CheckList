package com.dataloom.checklist.domain.transfer

import java.util.Locale

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
    private const val PHOTO_DIRECTORY = "photos"
    private const val PATH_PARTS = 2

    /** First bytes needed to recognise any supported image type. */
    const val MAGIC_BYTES = 12

    fun classify(name: String): Classified {
        val problem = unsafeProblem(name)
        return when {
            problem != null -> Classified.Refused(problem)
            name == TransferFormat.ARCHIVE_JSON_ENTRY -> Classified.Ok(Entry.Json)
            else -> photoEntry(name)
        }
    }

    /** The lower-case lookup key of a photo path, or null when [path] is not a plain `photos/<name>.<ext>`. */
    fun photoKey(path: String): String? =
        ((classify(path) as? Classified.Ok)?.entry as? Entry.Photo)?.key

    /** Names that could leave the scratch directory or are not files at all. */
    private fun unsafeProblem(name: String): ArchiveProblem? {
        val segments = name.split('/')
        return when {
            name.isEmpty() -> ArchiveProblem.UNSAFE_PATH
            // Absolute paths, Windows separators, drive letters and control characters never belong in a name.
            name.startsWith("/") || name.contains('\\') || name.contains(':') -> ArchiveProblem.UNSAFE_PATH
            name.any { it.isISOControl() } -> ArchiveProblem.UNSAFE_PATH
            segments.any { it == ".." || it == "." } -> ArchiveProblem.UNSAFE_PATH
            name.endsWith("/") -> ArchiveProblem.UNEXPECTED_ENTRY
            else -> null
        }
    }

    private fun photoEntry(name: String): Classified {
        val segments = name.split('/')
        val file = segments.last()
        val extension = file.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)
        val problem = when {
            segments.size > PATH_PARTS -> ArchiveProblem.UNSAFE_PATH
            segments.size != PATH_PARTS || segments.first() != PHOTO_DIRECTORY -> ArchiveProblem.UNEXPECTED_ENTRY
            !FILE_NAME.matches(file) -> ArchiveProblem.UNSAFE_PATH
            extension !in TransferFormat.PHOTO_EXTENSIONS -> ArchiveProblem.DISALLOWED_EXTENSION
            else -> null
        }
        return if (problem != null) {
            Classified.Refused(problem)
        } else {
            Classified.Ok(Entry.Photo(key = name.lowercase(Locale.ROOT), fileName = file, extension = extension))
        }
    }

    /** True when [head] starts like a JPEG, PNG or WebP file of the family [extension] belongs to. */
    fun looksLikeImage(extension: String, head: ByteArray, length: Int): Boolean = when (extension) {
        "jpg", "jpeg" -> head.startsWith(ImageSignatures.JPEG, length)
        "png" -> head.startsWith(ImageSignatures.PNG, length)
        "webp" -> head.startsWith(ImageSignatures.RIFF, length) && head.hasAt(ImageSignatures.WEBP, length)
        else -> false
    }

    private fun ByteArray.startsWith(signature: ByteArray, length: Int): Boolean =
        length >= signature.size && signature.indices.all { this[it] == signature[it] }

    /** WebP is `RIFF`, four size bytes, then `WEBP`. */
    private fun ByteArray.hasAt(signature: ByteArray, length: Int): Boolean {
        val offset = ImageSignatures.RIFF.size + ImageSignatures.RIFF_SIZE_BYTES
        return length >= offset + signature.size && signature.indices.all { this[offset + it] == signature[it] }
    }
}

/** File format signatures: fixed byte values defined by the formats. */
@Suppress("MagicNumber")
private object ImageSignatures {
    val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    val RIFF = "RIFF".toByteArray(Charsets.US_ASCII)
    val WEBP = "WEBP".toByteArray(Charsets.US_ASCII)
    const val RIFF_SIZE_BYTES = 4
}
