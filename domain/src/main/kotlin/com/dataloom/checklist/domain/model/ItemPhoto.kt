package com.dataloom.checklist.domain.model

/**
 * A photo attached to a checklist item (CL-210). The image lives in the app's private storage as
 * [fileName] (`<uuid>.jpg`, long edge at most 1600 px) with a 320 px thumbnail next to it; the
 * database keeps only this description. Photos are ordered by [position] (sparse, steps of 1000).
 *
 * Photos stay on the phone: they are never part of the AI context (docs/security.md).
 */
data class ItemPhoto(
    val id: PhotoId,
    val itemId: ChecklistItemId,
    /** Name of the full-size file inside the photo directory; never a path. */
    val fileName: String,
    val width: Int,
    val height: Int,
    val byteSize: Long,
    val position: Int,
    /** One short optional line, at most [com.dataloom.checklist.domain.photo.PhotoLimits.CAPTION_MAX] characters. */
    val caption: String?,
    val createdAt: Long,
)
