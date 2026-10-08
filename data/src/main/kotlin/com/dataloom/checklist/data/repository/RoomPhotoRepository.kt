package com.dataloom.checklist.data.repository

import androidx.room.withTransaction
import com.dataloom.checklist.data.local.database.CheckListDatabase
import com.dataloom.checklist.data.local.entity.ItemPhotoEntity
import com.dataloom.checklist.data.mapper.toDomain
import com.dataloom.checklist.domain.common.Clock
import com.dataloom.checklist.domain.common.IdGenerator
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.ChecklistItemId
import com.dataloom.checklist.domain.model.ItemPhoto
import com.dataloom.checklist.domain.model.PhotoId
import com.dataloom.checklist.domain.model.SectionId
import com.dataloom.checklist.domain.photo.StoredPhoto
import com.dataloom.checklist.domain.repository.PhotoRepository
import javax.inject.Inject

/**
 * Room implementation of [PhotoRepository]. Every change to a photo moves its checklist to the top
 * of "Recent", like any other change inside a checklist.
 */
class RoomPhotoRepository @Inject constructor(
    private val db: CheckListDatabase,
    private val clock: Clock,
    private val ids: IdGenerator,
) : PhotoRepository {

    private val photoDao = db.itemPhotoDao()
    private val itemDao = db.checklistItemDao()
    private val checklistDao = db.checklistDao()

    override suspend fun photosOf(itemId: ChecklistItemId): List<ItemPhoto>? = db.withTransaction {
        if (!photoDao.itemExists(itemId.value)) null else photoDao.getForItem(itemId.value).map { it.toDomain() }
    }

    override suspend fun addPhotos(itemId: ChecklistItemId, photos: List<StoredPhoto>): List<ItemPhoto> {
        if (photos.isEmpty()) return emptyList()
        val now = clock.nowMillis()
        return db.withTransaction {
            if (!photoDao.itemExists(itemId.value)) return@withTransaction emptyList()
            val positions = SparseOrder.appended(photoDao.maxPosition(itemId.value), photos.size)
            val rows = photos.zip(positions) { photo, position ->
                ItemPhotoEntity(
                    id = ids.newId(),
                    checklistItemId = itemId.value,
                    fileName = photo.fileName,
                    width = photo.width,
                    height = photo.height,
                    byteSize = photo.byteSize,
                    position = position,
                    caption = null,
                    createdAt = now,
                )
            }
            photoDao.insertAll(rows)
            touchChecklistOf(itemId.value, now)
            rows.map { it.toDomain() }
        }
    }

    override suspend fun removePhoto(photoId: PhotoId): ItemPhoto? = db.withTransaction {
        val row = photoDao.getById(photoId.value) ?: return@withTransaction null
        photoDao.delete(row.id)
        touchChecklistOf(row.checklistItemId, clock.nowMillis())
        row.toDomain()
    }

    override suspend fun movePhoto(photoId: PhotoId, toIndex: Int) {
        db.withTransaction {
            val row = photoDao.getById(photoId.value) ?: return@withTransaction
            val siblings = photoDao.getForItem(row.checklistItemId).map { SparseOrder.Entry(it.id, it.position) }
            val changes = SparseOrder.move(siblings, row.id, toIndex)
            if (changes.isEmpty()) return@withTransaction
            changes.forEach { (id, position) -> photoDao.updatePosition(id, position) }
            touchChecklistOf(row.checklistItemId, clock.nowMillis())
        }
    }

    override suspend fun setCaption(photoId: PhotoId, caption: String?) {
        db.withTransaction {
            val row = photoDao.getById(photoId.value) ?: return@withTransaction
            photoDao.updateCaption(row.id, caption)
            touchChecklistOf(row.checklistItemId, clock.nowMillis())
        }
    }

    override suspend fun fileNamesOfItem(itemId: ChecklistItemId): List<String> = photoDao.fileNamesOfItem(itemId.value)

    override suspend fun fileNamesOfSection(sectionId: SectionId): List<String> = photoDao.fileNamesOfSection(sectionId.value)

    override suspend fun fileNamesOfChecklist(checklistId: ChecklistId): List<String> =
        photoDao.fileNamesOfChecklist(checklistId.value)

    override suspend fun allFileNames(): Set<String> = photoDao.allFileNames().toHashSet()

    private suspend fun touchChecklistOf(itemId: String, now: Long) {
        itemDao.checklistIdOf(itemId)?.let { checklistDao.touch(it, now) }
    }
}
