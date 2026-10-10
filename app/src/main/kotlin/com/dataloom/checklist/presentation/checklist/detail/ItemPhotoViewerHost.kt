package com.dataloom.checklist.presentation.checklist.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.dataloom.checklist.presentation.photos.PhotoViewerDialog
import com.dataloom.checklist.presentation.photos.PhotoViewerViewModel

/**
 * Opens the photo viewer on the live photos of the item with id [itemId], or closes when the item
 * is gone (deleted, or its last photo removed) so the viewer never shows stale data.
 */
@Composable
internal fun ItemPhotoViewerHost(
    itemId: String,
    sections: List<SectionUi>,
    onClose: () -> Unit,
    viewModel: PhotoViewerViewModel = hiltViewModel(),
) {
    val item = sections.firstNotNullOfOrNull { section -> section.items.firstOrNull { it.id.value == itemId } }
    if (item == null || item.photos.isEmpty()) {
        LaunchedEffect(itemId) { onClose() }
        return
    }
    PhotoViewerDialog(
        itemName = item.name,
        photos = item.photos,
        startIndex = 0,
        onCaption = { photo, text -> viewModel.saveCaption(photo.id, text) },
        onDelete = { photo -> viewModel.delete(photo.id) },
        onClose = onClose,
    )
}
