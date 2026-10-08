package com.dataloom.checklist.domain.usecase

import com.dataloom.checklist.domain.common.Clock
import com.dataloom.checklist.domain.model.ChecklistItemId
import com.dataloom.checklist.domain.model.ItemPhoto
import com.dataloom.checklist.domain.model.PhotoId
import com.dataloom.checklist.domain.photo.ImageSource
import com.dataloom.checklist.domain.photo.PhotoFailure
import com.dataloom.checklist.domain.photo.PhotoLimits
import com.dataloom.checklist.domain.photo.PhotoNames
import com.dataloom.checklist.domain.photo.PhotoStore
import com.dataloom.checklist.domain.photo.SavePhotoResult
import com.dataloom.checklist.domain.photo.StoredPhoto
import com.dataloom.checklist.domain.repository.PhotoRepository
import com.dataloom.checklist.domain.validation.ValidationError
import com.dataloom.checklist.domain.validation.optionalText
import javax.inject.Inject

/**
 * What [AddItemPhotosUseCase] did. [skippedForLimit] counts sources left out because the item
 * already has [PhotoLimits.MAX_PER_ITEM] photos; [failed] counts sources that could not be read or
 * decoded ([lastFailure] says why for the last one).
 */
data class AddPhotosOutcome(
    val added: List<ItemPhoto>,
    val skippedForLimit: Int,
    val failed: Int,
    val lastFailure: PhotoFailure? = null,
)

/**
 * Copies images into private storage and attaches them to the item, up to 3 in total. Sources
 * beyond the free slots are skipped and counted, never stored. Each source is decoded one at a time
 * so memory stays flat. An item that no longer exists gives [DomainError.NotFound] and stores nothing.
 */
class AddItemPhotosUseCase @Inject constructor(
    private val photos: PhotoRepository,
    private val store: PhotoStore,
) {
    suspend operator fun invoke(itemId: ChecklistItemId, sources: List<ImageSource>): DomainResult<AddPhotosOutcome> {
        val existing = photos.photosOf(itemId) ?: return failure(DomainError.NotFound)
        val free = (PhotoLimits.MAX_PER_ITEM - existing.size).coerceAtLeast(0)
        val accepted = sources.take(free)
        val skipped = sources.size - accepted.size

        val saved = ArrayList<StoredPhoto>(accepted.size)
        var failed = 0
        var lastFailure: PhotoFailure? = null
        for (source in accepted) {
            when (val result = store.save(source)) {
                is SavePhotoResult.Saved -> saved += result.photo
                is SavePhotoResult.Failed -> {
                    failed++
                    lastFailure = result.reason
                }
            }
        }
        if (saved.isEmpty()) return success(AddPhotosOutcome(emptyList(), skipped, failed, lastFailure))
        return persist(itemId, saved, AddPhotosOutcome(emptyList(), skipped, failed, lastFailure))
    }

    private suspend fun persist(
        itemId: ChecklistItemId,
        saved: List<StoredPhoto>,
        counts: AddPhotosOutcome,
    ): DomainResult<AddPhotosOutcome> = when (val attached = attach(itemId, saved)) {
        is DomainResult.Success -> success(counts.copy(added = attached.value))
        is DomainResult.Failure -> attached
    }

    private val attach = AttachStoredPhotosUseCase(photos, store)
}

/**
 * Attaches photos that are already in the store (saved while an item was still being created) to
 * the item. Files whose row could not be written, for example because the item was deleted in the
 * meantime, are removed so no stray files are left behind. An item that is gone gives [DomainError.NotFound].
 */
class AttachStoredPhotosUseCase @Inject constructor(
    private val photos: PhotoRepository,
    private val store: PhotoStore,
) {
    suspend operator fun invoke(itemId: ChecklistItemId, saved: List<StoredPhoto>): DomainResult<List<ItemPhoto>> {
        val rows = photos.addPhotos(itemId, saved.take(PhotoLimits.MAX_PER_ITEM))
        val kept = rows.mapTo(HashSet()) { it.fileName }
        saved.filter { it.fileName !in kept }.forEach { store.delete(it.fileName) }
        return if (rows.isEmpty() && saved.isNotEmpty()) failure(DomainError.NotFound) else success(rows)
    }
}

/** Deletes the row, then its files. Removing a photo that is already gone succeeds. */
class RemoveItemPhotoUseCase @Inject constructor(
    private val photos: PhotoRepository,
    private val store: PhotoStore,
) {
    suspend operator fun invoke(photoId: PhotoId): DomainResult<Unit> {
        photos.removePhoto(photoId)?.let { store.delete(it.fileName) }
        return success(Unit)
    }
}

/**
 * Moves a photo to [toIndex] (0-based) among its item's photos. The UI offers visible
 * move-left and move-right buttons, which pass the neighbour's index; no drag is needed.
 */
class ReorderItemPhotoUseCase @Inject constructor(private val photos: PhotoRepository) {
    suspend operator fun invoke(photoId: PhotoId, toIndex: Int): DomainResult<Unit> {
        if (toIndex < 0) return failure(DomainError.Invalid(listOf(ValidationError.NEGATIVE_POSITION)))
        return success(photos.movePhoto(photoId, toIndex))
    }
}

/** Sets or clears the one-line caption. Line breaks become spaces; blank clears it. */
class SetPhotoCaptionUseCase @Inject constructor(private val photos: PhotoRepository) {
    suspend operator fun invoke(photoId: PhotoId, caption: String?): DomainResult<Unit> {
        val oneLine = caption?.replace(LINE_BREAKS, " ")
        val (text, error) = optionalText(oneLine, PhotoLimits.CAPTION_MAX, ValidationError.CAPTION_TOO_LONG)
        if (error != null) return failure(DomainError.Invalid(listOf(error)))
        photos.setCaption(photoId, text)
        return success(Unit)
    }

    private companion object {
        val LINE_BREAKS = Regex("[\\r\\n\\u2028\\u2029]+")
    }
}

/**
 * Deletes photo files that no row refers to and that are older than
 * [PhotoLimits.ORPHAN_MIN_AGE_MILLIS] (an add in progress has a fresh file and no row yet), plus old
 * staging leftovers. Runs in the background at app start so a crash can never leak storage.
 * Returns how many images were deleted.
 */
class SweepOrphanPhotosUseCase @Inject constructor(
    private val photos: PhotoRepository,
    private val store: PhotoStore,
    private val clock: Clock,
) {
    suspend operator fun invoke(): Int {
        val now = clock.nowMillis()
        val known = photos.allFileNames()
        val orphans = store.listStored()
            .filter { now - it.lastModifiedMillis > PhotoLimits.ORPHAN_MIN_AGE_MILLIS }
            .map { PhotoNames.baseName(it.fileName) }
            .filter { it !in known }
            .distinct()
        orphans.forEach { store.delete(it) }
        store.deleteStaleStaging(now, PhotoLimits.ORPHAN_MIN_AGE_MILLIS)
        return orphans.size
    }
}
