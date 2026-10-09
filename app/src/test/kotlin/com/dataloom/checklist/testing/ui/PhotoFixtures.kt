package com.dataloom.checklist.testing.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.dataloom.checklist.domain.model.ChecklistItemId
import com.dataloom.checklist.domain.model.PhotoId
import com.dataloom.checklist.domain.model.Quantity
import com.dataloom.checklist.presentation.checklist.detail.ItemUi
import com.dataloom.checklist.presentation.photos.FormPhotoUi
import com.dataloom.checklist.presentation.photos.PhotoUi
import java.io.File

/**
 * Real picture files for UI and screenshot tests: a coloured sky, a sun and a hill, so a
 * screenshot shows that the image (and its crop) is really drawn. Needs Robolectric's native
 * graphics (`@GraphicsMode(GraphicsMode.Mode.NATIVE)`).
 */
object PhotoFixtures {
    private val skies = intArrayOf(0xFF4FA3E0.toInt(), 0xFFF29E4C.toInt(), 0xFF7BC47F.toInt())

    /** Writes `<name>.jpg` (full size, [width] x [height]) and `<name>_t.jpg` (thumbnail) into [dir]. */
    fun photo(
        dir: File,
        name: String,
        number: Int,
        caption: String? = null,
        width: Int = 1200,
        height: Int = 900,
    ): PhotoUi {
        val image = File(dir, "$name.jpg")
        val thumbnail = File(dir, "${name}_t.jpg")
        write(image, width, height, number)
        write(thumbnail, THUMBNAIL_EDGE, THUMBNAIL_EDGE * height / width, number)
        return PhotoUi(PhotoId(name), image, thumbnail, caption)
    }

    fun photos(dir: File, count: Int): List<PhotoUi> =
        List(count) { photo(dir, "photo-${it + 1}", it, caption = if (it == 0) "Front of the shop" else null) }

    fun formPhotos(dir: File, count: Int): List<FormPhotoUi> =
        photos(dir, count).map { FormPhotoUi(it.id.value, it.thumbnail) }

    fun item(photos: List<PhotoUi>, name: String = "Rice") = ItemUi(
        id = ChecklistItemId("item-$name"),
        name = name,
        quantity = Quantity.of(2),
        unit = null,
        notes = "Basmati",
        isCompleted = false,
        canMoveUp = true,
        canMoveDown = true,
        photos = photos,
    )

    private fun write(file: File, width: Int, height: Int, number: Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(skies[number % skies.size])
        paint.color = Color.YELLOW
        canvas.drawCircle(width * 0.75f, height * 0.25f, height * 0.12f, paint)
        paint.color = 0xFF2E5E3A.toInt()
        canvas.drawCircle(width * 0.3f, height * 1.05f, height * 0.5f, paint)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        bitmap.recycle()
    }

    private const val THUMBNAIL_EDGE = 320
    private const val JPEG_QUALITY = 80
}
