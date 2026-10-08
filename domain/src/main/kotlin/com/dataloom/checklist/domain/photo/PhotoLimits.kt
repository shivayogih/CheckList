package com.dataloom.checklist.domain.photo

/** Rules for item photos (docs/item-photos-spec.md). Shared by the UI, import and the image pipeline. */
object PhotoLimits {
    /** A checklist item has 0 to 3 photos. */
    const val MAX_PER_ITEM = 3

    /** Caption: one short optional line, counted in code points like the other text fields. */
    const val CAPTION_MAX = 80

    /** Longest edge of the stored image, in pixels. Smaller images are never scaled up. */
    const val LONG_EDGE_PX = 1600

    /** Longest edge of the thumbnail written next to every image. */
    const val THUMBNAIL_EDGE_PX = 320

    const val JPEG_QUALITY = 80

    /**
     * Source images with more pixels than this are refused before any pixel is decoded. The biggest
     * phone sensors make about 200 megapixels; anything above is a decompression bomb or garbage.
     */
    const val MAX_SOURCE_PIXELS = 250_000_000L

    /** Files without a database row are only deleted once they are this old (an add may be in progress). */
    const val ORPHAN_MIN_AGE_MILLIS = 60L * 60 * 1000

    /** Suffix of the thumbnail written for `<uuid>.jpg`: `<uuid>_t.jpg`. */
    const val THUMBNAIL_SUFFIX = "_t"
}

/** File naming shared by every [PhotoStore]: the thumbnail of `a.jpg` is `a_t.jpg`. */
object PhotoNames {
    const val EXTENSION = ".jpg"

    fun thumbnailName(fileName: String): String {
        val base = fileName.removeSuffix(EXTENSION)
        return base + PhotoLimits.THUMBNAIL_SUFFIX + EXTENSION
    }

    /** The full-size name a thumbnail belongs to, or the name itself when it is not a thumbnail. */
    fun baseName(fileName: String): String {
        val base = fileName.removeSuffix(EXTENSION)
        return if (base.endsWith(PhotoLimits.THUMBNAIL_SUFFIX)) {
            base.removeSuffix(PhotoLimits.THUMBNAIL_SUFFIX) + EXTENSION
        } else {
            fileName
        }
    }

    /** Only plain names of the shape the store writes may be used as a file name (no separators, no leading dot). */
    fun isSafeName(fileName: String): Boolean = SAFE.matches(fileName)

    private val SAFE = Regex("[A-Za-z0-9][A-Za-z0-9_\\-]{0,100}\\.jpg")
}
