package com.dataloom.checklist.presentation.photos

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.createComposeRule
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
import com.dataloom.checklist.testing.screenshot.ScreenshotVariant
import com.dataloom.checklist.testing.screenshot.captureScene
import com.dataloom.checklist.testing.screenshot.setScreenshotContent
import com.dataloom.checklist.testing.ui.PhotoFixtures
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A PDF page with photos under the items, drawn onto a bitmap by the same code that writes the PDF file and
 * shown through the screenshot harness (CL-214, CL-216).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h4000dp-hdpi", application = Application::class)
class PdfPhotosPageScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `a pdf page with photos under the items`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = temp.newFolder()
        val detail = checklistWithPhotos(dir)
        val writer = AndroidChecklistPdfWriter(context, Clock { NOW }, DirectoryPhotoStore(dir))

        val withPhotos = render(writer, detail, PdfOptions(includePhotos = true))
        val without = render(writer, detail, PdfOptions(includePhotos = false))

        compose.setScreenshotContent(ScreenshotVariant.Light) { PageImage(withPhotos.first()) }
        compose.captureScene("pdf_page_with_photos", ScreenshotVariant.Light)
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
