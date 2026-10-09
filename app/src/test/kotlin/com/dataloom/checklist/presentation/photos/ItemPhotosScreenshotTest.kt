package com.dataloom.checklist.presentation.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.dataloom.checklist.domain.common.Clock
import com.dataloom.checklist.domain.model.Category
import com.dataloom.checklist.domain.model.CategoryId
import com.dataloom.checklist.domain.model.Checklist
import com.dataloom.checklist.domain.model.ChecklistDetail
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.ChecklistItem
import com.dataloom.checklist.domain.model.ChecklistItemId
import com.dataloom.checklist.domain.model.ChecklistSection
import com.dataloom.checklist.domain.model.ItemPhoto
import com.dataloom.checklist.domain.model.PhotoId
import com.dataloom.checklist.domain.model.Quantity
import com.dataloom.checklist.domain.model.SectionId
import com.dataloom.checklist.domain.photo.NoPhotoStore
import com.dataloom.checklist.domain.photo.PhotoNames
import com.dataloom.checklist.domain.photo.PhotoStore
import com.dataloom.checklist.presentation.checklist.detail.ItemRow
import com.dataloom.checklist.testing.ui.PhotoFixtures
import com.dataloom.checklist.testing.ui.SCREENSHOT_DIR
import com.dataloom.checklist.testing.ui.ScreenshotVariant
import com.dataloom.checklist.testing.ui.capture
import com.dataloom.checklist.testing.ui.captureAll
import com.dataloom.checklist.transfer.pdf.AndroidChecklistPdfWriter
import com.dataloom.checklist.transfer.pdf.PdfOptions
import com.dataloom.checklist.transfer.pdf.UnitLabels
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the photo features (CL-216): the item row, the form section, the viewer and a PDF
 * page, each in light, dark, 200 % font and Tamil. CI uploads them as the "screenshots" artifact.
 * They are recorded, not compared: there are no golden files to keep in the repository.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ItemPhotosScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val temp = TemporaryFolder()

    private fun recorded(name: String, variant: ScreenshotVariant) {
        val file = File("$SCREENSHOT_DIR/$name-${variant.name}.png")
        assertTrue("${file.path} was not written", file.isFile && file.length() > 0)
    }

    @Test
    fun `item rows with no, one and three photos`() {
        val dir = temp.newFolder()
        compose.captureAll("photos-item-row") {
            Column {
                ItemRow(PhotoFixtures.item(emptyList(), "Salt"), {}, {}, {})
                HorizontalDivider()
                ItemRow(PhotoFixtures.item(PhotoFixtures.photos(dir, 1), "Rice"), {}, {}, {})
                HorizontalDivider()
                ItemRow(PhotoFixtures.item(PhotoFixtures.photos(temp.newFolder(), 3), "Sugar"), {}, {}, {})
            }
        }
        ScreenshotVariant.STANDARD.forEach { recorded("photos-item-row", it) }
    }

    @Test
    fun `the photos section of the item form`() {
        val photos = PhotoFixtures.formPhotos(temp.newFolder(), 2)
        compose.captureAll("photos-form") {
            Column(Modifier.padding(16.dp)) {
                PhotosFormSection(
                    photos, processing = 1, message = null, onAdd = {}, onRemove = {}, onMove = { _, _ -> },
                )
            }
        }
        ScreenshotVariant.STANDARD.forEach { recorded("photos-form", it) }
    }

    @Test
    fun `the full screen viewer`() {
        val photos = PhotoFixtures.photos(temp.newFolder(), 3)
        compose.captureAll("photos-viewer") {
            PhotoViewerContent(
                "Rice", photos, index = 0, onIndexChange = {}, onCaption = { _, _ -> }, onDelete = {}, onClose = {},
            )
        }
        ScreenshotVariant.STANDARD.forEach { recorded("photos-viewer", it) }
    }

    @Test
    fun `a pdf page with photos under the items`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = temp.newFolder()
        val detail = checklistWithPhotos(dir)
        val writer = AndroidChecklistPdfWriter(context, Clock { NOW }, DirectoryPhotoStore(dir))

        val withPhotos = render(writer, detail, PdfOptions(includePhotos = true))
        val without = render(writer, detail, PdfOptions(includePhotos = false))

        compose.capture("pdf-page-with-photos", ScreenshotVariant.LIGHT) { PageImage(withPhotos.first()) }
        compose.waitForIdle()
        recorded("pdf-page-with-photos", ScreenshotVariant.LIGHT)
        assertTrue("A page was drawn", without.isNotEmpty())
    }

    @Composable
    private fun PageImage(page: Bitmap) {
        Image(
            bitmap = page.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    /** Draws the pages onto bitmaps with the same code that writes the PDF file. */
    private fun render(
        writer: AndroidChecklistPdfWriter,
        detail: ChecklistDetail,
        options: PdfOptions,
    ): List<Bitmap> {
        val pages = ArrayList<Bitmap>()
        var bitmap: Bitmap? = null
        writer.drawPages(
            detail,
            options,
            UnitLabels { it.value },
            object : AndroidChecklistPdfWriter.PageSurface {
                override fun start(pageNumber: Int, width: Int, height: Int): Canvas {
                    val page = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.eraseColor(Color.WHITE)
                    bitmap = page
                    return Canvas(page)
                }

                override fun finish() {
                    bitmap?.let { pages += it }
                }
            },
        )
        return pages
    }

    private fun checklistWithPhotos(dir: File): ChecklistDetail {
        val checklistId = ChecklistId("cl-1")
        val sectionId = SectionId("sec-1")
        val names = listOf("Rice" to 3, "Sugar" to 1, "Salt" to 0)
        val items = names.mapIndexed { index, (name, count) ->
            val id = ChecklistItemId("item-$index")
            val photos = List(count) { n ->
                val file = PhotoFixtures.photo(dir, "$name-${n + 1}", n)
                ItemPhoto(PhotoId(file.id.value), id, file.image.name, 1200, 900, 1L, (n + 1) * 1000, null, 1L)
            }
            ChecklistItem(
                id, sectionId, null, null, name, "en", Quantity.of(2 + index), null, "Basmati".takeIf { index == 0 },
                index == 2, (index + 1) * 1000, 1L, 1L, photos,
            )
        }
        val category = Category(
            CategoryId("cat-1"), "groceries", null, "Groceries", "cart", isCustom = false, isHidden = false,
        )
        return ChecklistDetail(
            Checklist(checklistId, "Weekly shopping", "For the week", 1L, 1L, isArchived = false),
            listOf(ChecklistSection(sectionId, checklistId, category, 0, items)),
        )
    }

    /** Thumbnails live in [dir]; nothing else is needed to draw a PDF. */
    private class DirectoryPhotoStore(private val dir: File) : PhotoStore by NoPhotoStore {
        override fun file(fileName: String): File = File(dir, fileName)

        override fun thumbnailFile(fileName: String): File = File(dir, PhotoNames.thumbnailName(fileName))
    }

    private companion object {
        const val NOW = 1_791_432_000_000L
    }
}
