package com.dataloom.checklist.domain.repository

import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.ChecklistItemId
import com.dataloom.checklist.domain.model.ItemPhoto
import com.dataloom.checklist.domain.model.PhotoId
import com.dataloom.checklist.domain.model.SectionId
import com.dataloom.checklist.domain.photo.StoredPhoto

/**
 * Rows of the `item_photo` table (CL-210). Files are the [com.dataloom.checklist.domain.photo.PhotoStore]'s
 * business; this interface only knows file names. Room cascades delete rows with their item, so the
 * `fileNamesOf...` reads exist to find the files to delete *before* a delete use case runs.
 *
 * Writes aimed at a row that no longer exists are no-ops, like the rest of the repository contract.
 */
interface PhotoRepository {

    /** Photos of the item in display order, or null when the item does not exist. */
    suspend fun photosOf(itemId: ChecklistItemId): List<ItemPhoto>?

    /** Appends [photos] after the existing ones (sparse positions). Returns the new rows. */
    suspend fun addPhotos(itemId: ChecklistItemId, photos: List<StoredPhoto>): List<ItemPhoto>

    /** Deletes the row and returns it, or null when it did not exist. */
    suspend fun removePhoto(photoId: PhotoId): ItemPhoto?

    /** Moves the photo to the 0-based [toIndex] among its item's photos. */
    suspend fun movePhoto(photoId: PhotoId, toIndex: Int)

    /** [caption] is already validated; null clears it. */
    suspend fun setCaption(photoId: PhotoId, caption: String?)

    suspend fun fileNamesOfItem(itemId: ChecklistItemId): List<String>

    suspend fun fileNamesOfSection(sectionId: SectionId): List<String>

    suspend fun fileNamesOfChecklist(checklistId: ChecklistId): List<String>

    /** Every file name that has a row; anything else in the photo directory is an orphan. */
    suspend fun allFileNames(): Set<String>
}

/** A repository without photos, for tests of other features; nothing is ever stored. */
object NoPhotoRepository : PhotoRepository {
    override suspend fun photosOf(itemId: ChecklistItemId): List<ItemPhoto>? = emptyList()

    override suspend fun addPhotos(itemId: ChecklistItemId, photos: List<StoredPhoto>): List<ItemPhoto> = emptyList()

    override suspend fun removePhoto(photoId: PhotoId): ItemPhoto? = null

    override suspend fun movePhoto(photoId: PhotoId, toIndex: Int) = Unit

    override suspend fun setCaption(photoId: PhotoId, caption: String?) = Unit

    override suspend fun fileNamesOfItem(itemId: ChecklistItemId): List<String> = emptyList()

    override suspend fun fileNamesOfSection(sectionId: SectionId): List<String> = emptyList()

    override suspend fun fileNamesOfChecklist(checklistId: ChecklistId): List<String> = emptyList()

    override suspend fun allFileNames(): Set<String> = emptySet()
}
