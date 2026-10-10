package com.dataloom.checklist.domain.fake

import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.ChecklistItemId
import com.dataloom.checklist.domain.model.ItemPhoto
import com.dataloom.checklist.domain.model.PhotoId
import com.dataloom.checklist.domain.model.SectionId
import com.dataloom.checklist.domain.photo.ImageSource
import com.dataloom.checklist.domain.photo.PhotoFailure
import com.dataloom.checklist.domain.photo.PhotoNames
import com.dataloom.checklist.domain.photo.PhotoStore
import com.dataloom.checklist.domain.photo.SavePhotoResult
import com.dataloom.checklist.domain.photo.StagePhotoResult
import com.dataloom.checklist.domain.photo.StagedPhoto
import com.dataloom.checklist.domain.photo.StoredFileInfo
import com.dataloom.checklist.domain.photo.StoredPhoto
import com.dataloom.checklist.domain.repository.PhotoRepository
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files

/** A source whose bytes are [text]; "BAD..." text is refused by [FakePhotoStore] as not an image. */
fun textSource(text: String) = ImageSource { ByteArrayInputStream(text.toByteArray()) }

/** In-memory photo store: files are byte arrays by name, and "BAD" content is not an image. */
class FakePhotoStore(var now: () -> Long = { 1_000L }) : PhotoStore {
    val files = LinkedHashMap<String, ByteArray>()
    val modified = HashMap<String, Long>()
    val staged = LinkedHashMap<String, ByteArray>()
    private var next = 1

    fun put(fileName: String, bytes: String = "img", lastModified: Long = now()) = putBytes(fileName,
        bytes.toByteArray(), lastModified)

    fun putBytes(fileName: String, bytes: ByteArray, lastModified: Long = now()) {
        files[fileName] = bytes
        files[PhotoNames.thumbnailName(fileName)] = "thumb".toByteArray()
        modified[fileName] = lastModified
        modified[PhotoNames.thumbnailName(fileName)] = lastModified
    }

    private fun read(source: ImageSource): ByteArray? = try {
        source.openStream().use { it.readBytes() }
    } catch (_: java.io.IOException) {
        null
    }

    private fun newName() = "photo-${next++}.jpg"

    override suspend fun save(source: ImageSource): SavePhotoResult {
        val bytes = read(source) ?: return SavePhotoResult.Failed(PhotoFailure.UNREADABLE)
        if (String(bytes).startsWith("BAD")) return SavePhotoResult.Failed(PhotoFailure.NOT_AN_IMAGE)
        val name = newName()
        putBytes(name, bytes)
        return SavePhotoResult.Saved(StoredPhoto(name, 100, 50, bytes.size.toLong()))
    }

    override suspend fun stage(source: ImageSource): StagePhotoResult {
        val bytes = read(source) ?: return StagePhotoResult.Failed(PhotoFailure.UNREADABLE)
        if (String(bytes).startsWith("BAD")) return StagePhotoResult.Failed(PhotoFailure.NOT_AN_IMAGE)
        val name = newName()
        staged[name] = bytes
        return StagePhotoResult.Staged(StagedPhoto(StoredPhoto(name, 100, 50, bytes.size.toLong())))
    }

    override suspend fun commit(staged: StagedPhoto): Boolean {
        val bytes = this.staged.remove(staged.photo.fileName) ?: return false
        putBytes(staged.photo.fileName, bytes)
        return true
    }

    override suspend fun discard(staged: StagedPhoto) {
        this.staged.remove(staged.photo.fileName)
    }

    override suspend fun delete(fileName: String) {
        val base = PhotoNames.baseName(fileName)
        files.remove(base)
        files.remove(PhotoNames.thumbnailName(base))
        modified.remove(base)
        modified.remove(PhotoNames.thumbnailName(base))
    }

    override suspend fun copy(fileName: String): String? {
        val bytes = files[fileName] ?: return null
        val name = newName()
        putBytes(name, bytes)
        return name
    }

    override fun open(fileName: String): InputStream? = files[fileName]?.let { ByteArrayInputStream(it) }

    override fun openThumbnail(fileName: String): InputStream? = open(PhotoNames.thumbnailName(fileName))

    override suspend fun byteSize(fileName: String): Long? = files[fileName]?.size?.toLong()

    override fun file(fileName: String): File = File("/fake/$fileName")

    override fun thumbnailFile(fileName: String): File = File("/fake/${PhotoNames.thumbnailName(fileName)}")

    override suspend fun listStored(): List<StoredFileInfo> = files.keys.map { StoredFileInfo(it, modified[it] ?: 0L) }

    var staleStagingCalls = 0

    override suspend fun deleteStaleStaging(nowMillis: Long, olderThanMillis: Long) {
        staleStagingCalls++
    }

    override suspend fun newScratchDirectory(): File = Files.createTempDirectory("fake-scratch").toFile()

    override suspend fun deleteScratchDirectory(directory: File) {
        directory.deleteRecursively()
    }
}

/** In-memory item_photo rows. Items must be registered with [addItem]; unknown items behave as deleted. */
class FakePhotoRepository : PhotoRepository {
    private val items = LinkedHashSet<ChecklistItemId>()
    private val rows = ArrayList<ItemPhoto>()
    private var next = 1

    /** Item -> (section, checklist), so the by-section and by-checklist reads can be answered. */
    private val owners = HashMap<ChecklistItemId, Pair<SectionId, ChecklistId>>()

    fun addItem(id: ChecklistItemId, section: SectionId = SectionId("sec"),
        checklist: ChecklistId = ChecklistId("cl")) {
        items += id
        owners[id] = section to checklist
    }

    fun removeItem(id: ChecklistItemId) {
        items -= id
        rows.removeAll { it.itemId == id }
    }

    fun all(): List<ItemPhoto> = rows.sortedWith(compareBy({ it.itemId.value }, { it.position }))

    override suspend fun photosOf(itemId: ChecklistItemId): List<ItemPhoto>? =
        if (itemId in items) rows.filter { it.itemId == itemId }.sortedBy { it.position } else null

    override suspend fun addPhotos(itemId: ChecklistItemId, photos: List<StoredPhoto>): List<ItemPhoto> {
        if (itemId !in items) return emptyList()
        var position = rows.filter { it.itemId == itemId }.maxOfOrNull { it.position } ?: 0
        return photos.map {
            position += 1000
            ItemPhoto(PhotoId("ph-${next++}"), itemId, it.fileName, it.width, it.height, it.byteSize, position, null,
                1L)
        }.also { rows += it }
    }

    override suspend fun removePhoto(photoId: PhotoId): ItemPhoto? =
        rows.firstOrNull { it.id == photoId }?.also { rows.remove(it) }

    override suspend fun movePhoto(photoId: PhotoId, toIndex: Int) {
        val photo = rows.firstOrNull { it.id == photoId } ?: return
        val siblings = rows.filter { it.itemId == photo.itemId }.sortedBy { it.position }.toMutableList()
        siblings.remove(photo)
        siblings.add(toIndex.coerceIn(0, siblings.size), photo)
        rows.removeAll { it.itemId == photo.itemId }
        rows += siblings.mapIndexed { index, p -> p.copy(position = (index + 1) * 1000) }
    }

    override suspend fun setCaption(photoId: PhotoId, caption: String?) {
        val index = rows.indexOfFirst { it.id == photoId }
        if (index >= 0) rows[index] = rows[index].copy(caption = caption)
    }

    override suspend fun fileNamesOfItem(itemId: ChecklistItemId): List<String> =
        rows.filter { it.itemId == itemId }.map { it.fileName }

    override suspend fun fileNamesOfSection(sectionId: SectionId): List<String> =
        rows.filter { owners[it.itemId]?.first == sectionId }.map { it.fileName }

    override suspend fun fileNamesOfChecklist(checklistId: ChecklistId): List<String> =
        rows.filter { owners[it.itemId]?.second == checklistId }.map { it.fileName }

    override suspend fun allFileNames(): Set<String> = rows.mapTo(HashSet()) { it.fileName }
}
