package com.dataloom.checklist.data.photo

import com.dataloom.checklist.domain.common.IdGenerator
import com.dataloom.checklist.domain.photo.ImageSource
import com.dataloom.checklist.domain.photo.PhotoFailure
import com.dataloom.checklist.domain.photo.PhotoNames
import com.dataloom.checklist.domain.photo.PhotoStore
import com.dataloom.checklist.domain.photo.SavePhotoResult
import com.dataloom.checklist.domain.photo.StagePhotoResult
import com.dataloom.checklist.domain.photo.StagedPhoto
import com.dataloom.checklist.domain.photo.StoredFileInfo
import com.dataloom.checklist.domain.photo.StoredPhoto
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * [PhotoStore] on a plain directory (`filesDir/item_photos` in the app, a temp directory in tests).
 * Every file operation and all decoding run on [io], never on the caller's thread.
 *
 * Layout: `<root>/<id>.jpg` and `<root>/<id>_t.jpg`; new images are written to `<root>/staging/`
 * first and moved into place by [commit], so a half-written file is never visible and an import that
 * fails leaves nothing behind. File names given to this class are checked with
 * [PhotoNames.isSafeName]; anything else is treated as a missing file, so a hostile name cannot
 * reach outside the directory.
 */
@Suppress("TooManyFunctions") // Implements the whole PhotoStore port; each function is a small file operation.
class FilePhotoStore(
    private val root: File,
    private val io: CoroutineDispatcher,
    private val ids: IdGenerator,
) : PhotoStore {

    private val staging = File(root, STAGING)

    override suspend fun save(source: ImageSource): SavePhotoResult = withContext(io) {
        when (val result = stageBlocking(source)) {
            is StagePhotoResult.Failed -> SavePhotoResult.Failed(result.reason)
            is StagePhotoResult.Staged ->
                if (commitBlocking(result.staged)) {
                    SavePhotoResult.Saved(result.staged.photo)
                } else {
                    discardBlocking(result.staged)
                    SavePhotoResult.Failed(PhotoFailure.WRITE_FAILED)
                }
        }
    }

    override suspend fun stage(source: ImageSource): StagePhotoResult = withContext(io) { stageBlocking(source) }

    override suspend fun commit(staged: StagedPhoto): Boolean = withContext(io) { commitBlocking(staged) }

    override suspend fun discard(staged: StagedPhoto) = withContext(io) { discardBlocking(staged) }

    override suspend fun delete(fileName: String) = withContext(io) {
        if (!PhotoNames.isSafeName(fileName)) return@withContext
        val base = PhotoNames.baseName(fileName)
        File(root, base).delete()
        File(root, PhotoNames.thumbnailName(base)).delete()
        Unit
    }

    override suspend fun copy(fileName: String): String? = withContext(io) {
        if (!PhotoNames.isSafeName(fileName)) return@withContext null
        val source = File(root, fileName)
        if (!source.isFile) return@withContext null
        val newName = newFileName()
        val target = File(root, newName)
        val targetThumb = File(root, PhotoNames.thumbnailName(newName))
        try {
            Files.copy(source.toPath(), target.toPath())
            val sourceThumb = File(root, PhotoNames.thumbnailName(fileName))
            if (sourceThumb.isFile) Files.copy(sourceThumb.toPath(), targetThumb.toPath())
            newName
        } catch (_: IOException) {
            target.delete()
            targetThumb.delete()
            null
        }
    }

    override fun open(fileName: String): InputStream? = openOrNull(file(fileName))

    override fun openThumbnail(fileName: String): InputStream? = openOrNull(thumbnailFile(fileName))

    override suspend fun byteSize(fileName: String): Long? = withContext(io) {
        file(fileName).takeIf { it.isFile }?.length()
    }

    override fun file(fileName: String): File =
        File(root, if (PhotoNames.isSafeName(fileName)) fileName else INVALID_NAME)

    override fun thumbnailFile(fileName: String): File =
        File(root, if (PhotoNames.isSafeName(fileName)) PhotoNames.thumbnailName(fileName) else INVALID_NAME)

    override suspend fun listStored(): List<StoredFileInfo> = withContext(io) {
        root.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(PhotoNames.EXTENSION) }
            .map { StoredFileInfo(it.name, it.lastModified()) }
    }

    override suspend fun deleteStaleStaging(nowMillis: Long, olderThanMillis: Long) = withContext(io) {
        staging.listFiles().orEmpty()
            .filter { nowMillis - it.lastModified() > olderThanMillis }
            .forEach { it.deleteRecursively() }
    }

    override suspend fun newScratchDirectory(): File = withContext(io) {
        File(staging, "$SCRATCH_PREFIX${ids.newId()}").also { it.mkdirs() }
    }

    override suspend fun deleteScratchDirectory(directory: File) = withContext(io) {
        // Only directories this store handed out may be removed.
        if (directory.parentFile?.canonicalFile == staging.canonicalFile && directory.name.startsWith(SCRATCH_PREFIX)) {
            directory.deleteRecursively()
        }
        Unit
    }

    private fun stageBlocking(source: ImageSource): StagePhotoResult {
        val encoded = when (val result = ImageEncoder.encode(source)) {
            is EncodeResult.Failed -> return StagePhotoResult.Failed(result.reason)
            is EncodeResult.Ok -> result.image
        }
        val name = newFileName()
        val main = File(staging, name)
        val thumb = File(staging, PhotoNames.thumbnailName(name))
        return try {
            staging.mkdirs()
            main.writeBytes(encoded.main)
            thumb.writeBytes(encoded.thumbnail)
            StagePhotoResult.Staged(
            StagedPhoto(StoredPhoto(name, encoded.width, encoded.height, encoded.main.size.toLong())),
        )
        } catch (_: IOException) {
            main.delete()
            thumb.delete()
            StagePhotoResult.Failed(PhotoFailure.WRITE_FAILED)
        }
    }

    private fun commitBlocking(staged: StagedPhoto): Boolean {
        val name = staged.photo.fileName
        if (!PhotoNames.isSafeName(name)) return false
        val main = File(staging, name)
        val thumb = File(staging, PhotoNames.thumbnailName(name))
        if (!main.isFile || !thumb.isFile) return false
        return try {
            root.mkdirs()
            // Thumbnail first: a visible image always has its thumbnail.
            move(thumb, File(root, thumb.name))
            move(main, File(root, name))
            true
        } catch (_: IOException) {
            File(root, thumb.name).delete()
            File(root, name).delete()
            false
        }
    }

    private fun discardBlocking(staged: StagedPhoto) {
        val name = staged.photo.fileName
        if (!PhotoNames.isSafeName(name)) return
        File(staging, name).delete()
        File(staging, PhotoNames.thumbnailName(name)).delete()
    }

    private fun move(from: File, to: File) {
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun newFileName(): String = "${ids.newId()}${PhotoNames.EXTENSION}"

    private fun openOrNull(file: File): InputStream? = try {
        if (file.isFile) FileInputStream(file) else null
    } catch (_: IOException) {
        null
    }

    private companion object {
        const val STAGING = "staging"
        const val SCRATCH_PREFIX = "raw-"

        /** A name that never exists, returned for names that fail the safety check. */
        const val INVALID_NAME = "invalid-name"
    }
}
