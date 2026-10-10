package com.dataloom.checklist.presentation.photos

import com.dataloom.checklist.R
import com.dataloom.checklist.domain.model.ChecklistItemId
import com.dataloom.checklist.domain.model.ItemPhoto
import com.dataloom.checklist.domain.model.PhotoId
import com.dataloom.checklist.domain.photo.ImageSource
import com.dataloom.checklist.domain.photo.PhotoLimits
import com.dataloom.checklist.domain.photo.PhotoStore
import com.dataloom.checklist.domain.photo.SavePhotoResult
import com.dataloom.checklist.domain.photo.StoredPhoto
import com.dataloom.checklist.domain.usecase.AddItemPhotosUseCase
import com.dataloom.checklist.domain.usecase.AttachStoredPhotosUseCase
import com.dataloom.checklist.domain.usecase.CustomItemOutcome
import com.dataloom.checklist.domain.usecase.DomainError
import com.dataloom.checklist.domain.usecase.DomainResult
import com.dataloom.checklist.domain.usecase.RemoveItemPhotoUseCase
import com.dataloom.checklist.domain.usecase.ReorderItemPhotoUseCase
import com.dataloom.checklist.presentation.common.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the photo part of an item form shows. */
data class PhotoFormState(
    /** Up to 3 photos in display order. */
    val photos: List<FormPhotoUi> = emptyList(),
    /** Photos being copied and re-encoded right now; each shows as a progress tile. */
    val processing: Int = 0,
    /** "Couldn't add this photo" or "Only 3 photos fit", shown next to the tiles. */
    val message: UiText? = null,
) {
    /** Photos that can still be added, counting the ones in progress. */
    val freeSlots: Int get() = (PhotoLimits.MAX_PER_ITEM - photos.size - processing).coerceAtLeast(0)
}

/** What the user can do with the photos of a form. */
sealed interface PhotoAction {
    /** Pictures chosen in the picker or taken with the camera; [onFinished] runs when they are processed. */
    data class Add(val sources: List<ImageSource>, val onFinished: () -> Unit = {}) : PhotoAction
    data class Remove(val key: String) : PhotoAction

    /** [delta] is -1 for "move left" and +1 for "move right". */
    data class Move(val key: String, val delta: Int) : PhotoAction
}

/**
 * The photos of one item form. Editing an existing item ([itemId] set) changes its photo rows at
 * once; for a new item the pictures are saved in the store without a row and attached by
 * [handOver] when the item has been created, or deleted by [discard] when the form is abandoned.
 * All store and database work is serialised so quick taps cannot interleave.
 */
class ItemPhotoDraft(
    private val itemId: ChecklistItemId?,
    private val store: PhotoStore,
    private val addToItem: AddItemPhotosUseCase,
    private val attach: AttachStoredPhotosUseCase,
    private val removeFromItem: RemoveItemPhotoUseCase,
    private val reorderInItem: ReorderItemPhotoUseCase,
    private val scope: CoroutineScope,
    /** The item was deleted elsewhere; the form closes. */
    private val onGone: suspend () -> Unit,
) {
    /** One photo of the form. [photoId] is set once it has a database row. */
    private class Entry(val fileName: String, val photoId: PhotoId?, val stored: StoredPhoto?)

    private val mutableState = MutableStateFlow(PhotoFormState())
    val state: StateFlow<PhotoFormState> = mutableState

    private var entries: List<Entry> = emptyList()
    private val work = Mutex()
    private var handedOver = false

    /** Shows the photos an existing item already has. */
    fun load(existing: List<ItemPhoto>) {
        entries = existing.map { Entry(it.fileName, it.id, stored = null) }
        publish()
    }

    fun handle(action: PhotoAction) {
        when (action) {
            is PhotoAction.Add -> add(action.sources, action.onFinished)
            is PhotoAction.Remove -> remove(action.key)
            is PhotoAction.Move -> move(action.key, action.delta)
        }
    }

    private fun publish(message: UiText? = mutableState.value.message) {
        val photos = entries.map { FormPhotoUi(it.fileName, store.thumbnailFile(it.fileName)) }
        mutableState.update { it.copy(photos = photos, message = message) }
    }

    /** More pictures than free slots are cut off and counted, whatever the picker allowed. */
    private fun add(sources: List<ImageSource>, onFinished: () -> Unit) {
        val accepted = sources.take(mutableState.value.freeSlots)
        val skipped = sources.size - accepted.size
        if (accepted.isEmpty()) {
            if (skipped > 0) mutableState.update { it.copy(message = limitMessage()) }
            onFinished()
            return
        }
        mutableState.update { it.copy(processing = it.processing + accepted.size, message = null) }
        scope.launch {
            var failed = 0
            try {
                work.withLock { failed = if (itemId != null) addToExisting(itemId, accepted) else addToNew(accepted) }
            } finally {
                val message = when {
                    failed > 0 -> UiText(R.string.photos_add_failed)
                    skipped > 0 -> limitMessage()
                    else -> null
                }
                mutableState.update { it.copy(processing = it.processing - accepted.size, message = message) }
                onFinished()
            }
        }
    }

    private fun limitMessage() = UiText(R.string.photos_limit_reached, listOf(PhotoLimits.MAX_PER_ITEM))

    /** Returns how many pictures could not be added. */
    private suspend fun addToExisting(id: ChecklistItemId, sources: List<ImageSource>): Int =
        when (val result = addToItem(id, sources)) {
            is DomainResult.Success -> {
                entries = entries + result.value.added.map { Entry(it.fileName, it.id, stored = null) }
                publish()
                result.value.failed
            }
            is DomainResult.Failure -> {
                if (result.error == DomainError.NotFound) onGone()
                sources.size
            }
        }

    /** Returns how many pictures could not be added. */
    private suspend fun addToNew(sources: List<ImageSource>): Int {
        var failed = 0
        for (source in sources) {
            when (val saved = store.save(source)) {
                is SavePhotoResult.Saved -> {
                    entries = entries + Entry(saved.photo.fileName, photoId = null, stored = saved.photo)
                    publish()
                }
                is SavePhotoResult.Failed -> failed++
            }
        }
        return failed
    }

    private fun remove(key: String) {
        val entry = entries.firstOrNull { it.fileName == key } ?: return
        // Gone from the form at once; the row and the files follow.
        entries = entries - entry
        publish(message = null)
        scope.launch {
            work.withLock {
                if (entry.photoId != null) removeFromItem(entry.photoId) else store.delete(entry.fileName)
            }
        }
    }

    private fun move(key: String, delta: Int) {
        val from = entries.indexOfFirst { it.fileName == key }
        val to = from + delta
        if (from < 0 || to !in entries.indices) return
        val entry = entries[from]
        entries = entries.toMutableList().apply {
            removeAt(from)
            add(to, entry)
        }
        publish()
        if (entry.photoId != null) scope.launch { work.withLock { reorderInItem(entry.photoId, to) } }
    }

    /** A new item was just created: attach the pictures saved while the form was open, or delete them. */
    suspend fun handOver(outcome: CustomItemOutcome) {
        if (itemId != null) return
        val stored = entries.mapNotNull { it.stored }
        handedOver = true
        if (outcome is CustomItemOutcome.Added) {
            if (stored.isNotEmpty()) attach(outcome.itemId, stored)
        } else {
            stored.forEach { store.delete(it.fileName) }
        }
    }

    /** Files saved for an item that was never created; the owner deletes them when the form is abandoned. */
    fun abandonedFiles(): List<String> =
        if (itemId != null || handedOver) emptyList() else entries.mapNotNull { it.stored?.fileName }
}
