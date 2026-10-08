package com.dataloom.checklist.domain.photo

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files

/**
 * Where an image comes from (the Photo Picker, the camera's temp file, an entry of an import file).
 * [openStream] may be called more than once; the caller closes each stream. It may throw
 * [IOException] or [SecurityException], which the store reports as [PhotoFailure.UNREADABLE].
 */
fun interface ImageSource {
    fun openStream(): InputStream
}

/** A photo that has been re-encoded into app-private storage. [fileName] is `<uuid>.jpg`. */
data class StoredPhoto(
    val fileName: String,
    val width: Int,
    val height: Int,
    val byteSize: Long,
)

/** Why an image could not be added. The UI shows one friendly message for all of them. */
enum class PhotoFailure {
    /** The source could not be opened or read (permission revoked, provider gone, I/O error). */
    UNREADABLE,

    /** The bytes are not an image the device can decode. */
    NOT_AN_IMAGE,

    /** More than [PhotoLimits.MAX_SOURCE_PIXELS] pixels. */
    TOO_MANY_PIXELS,

    /** The result could not be written (storage full). */
    WRITE_FAILED,
}

sealed interface SavePhotoResult {
    data class Saved(val photo: StoredPhoto) : SavePhotoResult

    data class Failed(val reason: PhotoFailure) : SavePhotoResult
}

/** A re-encoded photo waiting in the staging area; invisible to the app until [PhotoStore.commit]. */
data class StagedPhoto(val photo: StoredPhoto)

sealed interface StagePhotoResult {
    data class Staged(val staged: StagedPhoto) : StagePhotoResult

    data class Failed(val reason: PhotoFailure) : StagePhotoResult
}

/** A file in the photo directory, for the orphan sweep. */
data class StoredFileInfo(val fileName: String, val lastModifiedMillis: Long)

/**
 * App-private storage for item photos (`filesDir/item_photos/<uuid>.jpg` plus `<uuid>_t.jpg`).
 *
 * Implemented in :data on Context.filesDir (a temp directory in tests). All decoding and file work
 * happens on an injected dispatcher, never on the caller's thread. The picker's content:// URI is
 * never stored; [save] copies the pixels and re-encodes them, which also removes EXIF data such as
 * the GPS location.
 */
interface PhotoStore {

    /**
     * Decodes [source] with downsampling, applies its EXIF orientation, scales the long edge to at
     * most [PhotoLimits.LONG_EDGE_PX], re-encodes it as JPEG and writes a thumbnail next to it.
     * Equivalent to [stage] followed by [commit].
     */
    suspend fun save(source: ImageSource): SavePhotoResult

    /** Like [save] but writes into the staging area; call [commit] after the database accepted the row, or [discard]. */
    suspend fun stage(source: ImageSource): StagePhotoResult

    /** Moves a staged photo (and its thumbnail) into place. Returns false if the staged files are gone. */
    suspend fun commit(staged: StagedPhoto): Boolean

    /** Deletes a staged photo that will not be used. */
    suspend fun discard(staged: StagedPhoto)

    /** Deletes the image and its thumbnail. A name that does not exist is not an error. */
    suspend fun delete(fileName: String)

    /** Copies the image and its thumbnail to a new uuid name. Null when the source file is missing. */
    suspend fun copy(fileName: String): String?

    /** Opens the stored image, or null when the file is missing. The caller closes the stream. */
    fun open(fileName: String): InputStream?

    fun openThumbnail(fileName: String): InputStream?

    /** Size in bytes of the stored image, or null when the file is missing. */
    suspend fun byteSize(fileName: String): Long?

    /** The stored image as a file (it may not exist: a missing file is a normal state shown as a placeholder). */
    fun file(fileName: String): File

    fun thumbnailFile(fileName: String): File

    /** Every file in the photo directory (images and thumbnails), for the orphan sweep. */
    suspend fun listStored(): List<StoredFileInfo>

    /**
     * Deletes staging leftovers (staged photos and import scratch directories) that were last
     * modified more than [olderThanMillis] before [nowMillis].
     */
    suspend fun deleteStaleStaging(nowMillis: Long, olderThanMillis: Long)

    /** A new empty directory for raw bytes of an import file; remove it with [deleteScratchDirectory]. */
    suspend fun newScratchDirectory(): File

    suspend fun deleteScratchDirectory(directory: File)
}

/**
 * A store that holds nothing. Used where no store is wired (plain JVM tests of other features);
 * every file is missing and nothing can be saved.
 */
object NoPhotoStore : PhotoStore {
    override suspend fun save(source: ImageSource): SavePhotoResult = SavePhotoResult.Failed(PhotoFailure.WRITE_FAILED)

    override suspend fun stage(source: ImageSource): StagePhotoResult = StagePhotoResult.Failed(PhotoFailure.WRITE_FAILED)

    override suspend fun commit(staged: StagedPhoto): Boolean = false

    override suspend fun discard(staged: StagedPhoto) = Unit

    override suspend fun delete(fileName: String) = Unit

    override suspend fun copy(fileName: String): String? = null

    override fun open(fileName: String): InputStream? = null

    override fun openThumbnail(fileName: String): InputStream? = null

    override suspend fun byteSize(fileName: String): Long? = null

    override fun file(fileName: String): File = File("/nonexistent/$fileName")

    override fun thumbnailFile(fileName: String): File = File("/nonexistent/${PhotoNames.thumbnailName(fileName)}")

    override suspend fun listStored(): List<StoredFileInfo> = emptyList()

    override suspend fun deleteStaleStaging(nowMillis: Long, olderThanMillis: Long) = Unit

    override suspend fun newScratchDirectory(): File = Files.createTempDirectory("checklist-scratch").toFile()

    override suspend fun deleteScratchDirectory(directory: File) {
        directory.deleteRecursively()
    }
}
