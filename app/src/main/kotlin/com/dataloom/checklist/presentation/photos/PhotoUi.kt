package com.dataloom.checklist.presentation.photos

import com.dataloom.checklist.domain.model.ItemPhoto
import com.dataloom.checklist.domain.model.PhotoId
import com.dataloom.checklist.domain.photo.PhotoStore
import java.io.File

/**
 * A photo as the screens draw it: the two private files and the caption. Files may be missing
 * (a restored backup, cleared storage); [PhotoThumbnail] then shows a placeholder.
 */
data class PhotoUi(
    val id: PhotoId,
    val image: File,
    val thumbnail: File,
    val caption: String?,
)

fun ItemPhoto.toUi(store: PhotoStore) = PhotoUi(
    id = id,
    image = store.file(fileName),
    thumbnail = store.thumbnailFile(fileName),
    caption = caption,
)

/** A photo in the item form: a stable [key] for the actions and the small file to draw. */
data class FormPhotoUi(val key: String, val thumbnail: File)
