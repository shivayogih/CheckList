package com.dataloom.checklist.transfer

import com.dataloom.checklist.domain.common.Clock
import com.dataloom.checklist.transfer.pdf.PdfPageLayout
import com.dataloom.checklist.transfer.pdf.PdfPaginator
import com.dataloom.checklist.transfer.pdf.PdfPaginator.Block
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.atan2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferHelpersTest {

    @Test
    fun `blocks fill a page and continue on the next`() {
        val blocks = List(5) { Block(30f) }
        assertEquals(listOf(listOf(0, 1, 2), listOf(3, 4)), PdfPaginator.paginate(blocks, pageHeight = 100f))
    }

    @Test
    fun `a heading moves to the next page with its first item`() {
        val blocks = listOf(Block(60f), Block(20f, keepWithNext = true), Block(30f), Block(30f))
        assertEquals(listOf(listOf(0), listOf(1, 2, 3)), PdfPaginator.paginate(blocks, pageHeight = 100f))
    }

    @Test
    fun `a block taller than a page gets a page of its own`() {
        val blocks = listOf(Block(10f), Block(250f), Block(10f))
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), PdfPaginator.paginate(blocks, pageHeight = 100f))
    }

    @Test
    fun `there is always at least one page`() {
        assertEquals(listOf(emptyList<Int>()), PdfPaginator.paginate(emptyList(), pageHeight = 100f))
    }

    @Test
    fun `file names keep every script and drop path characters`() {
        assertEquals("ದೀಪಾವಳಿ ಶಾಪಿಂಗ್", TransferDocuments.safeFileName("ದೀಪಾವಳಿ ಶಾಪಿಂಗ್"))
        assertEquals("दिवाली की खरीदारी", TransferDocuments.safeFileName("दिवाली की खरीदारी"))
        assertEquals("____etc_passwd", TransferDocuments.safeFileName("/../etc/passwd"))
        assertEquals("CheckList", TransferDocuments.safeFileName("  "))
        assertEquals(60, TransferDocuments.safeFileName("x".repeat(200)).length)
        assertEquals("Goa Trip.pdf", TransferDocuments.pdfFileName("Goa Trip"))
        assertEquals("CheckList-2026-10-08.json", TransferDocuments.exportFileName(LocalDate.of(2026, 10, 8)))
    }

    @Test
    fun `header, content and footer bands are inside the page and never overlap`() {
        val header = PdfPageLayout.header
        val content = PdfPageLayout.content
        val footer = PdfPageLayout.footer
        assertTrue(header.top >= PdfPageLayout.MARGIN)
        assertTrue(footer.bottom <= PdfPageLayout.PAGE_HEIGHT - PdfPageLayout.MARGIN)
        assertTrue(header.bottom < content.top && content.bottom < footer.top)
        assertFalse(header.overlaps(content))
        assertFalse(content.overlaps(footer))
        assertFalse(header.overlaps(footer))
        assertEquals(PdfPageLayout.HEADER_GAP, content.top - header.bottom, 0.001f)
        assertEquals(PdfPageLayout.FOOTER_GAP, footer.top - content.bottom, 0.001f)
        assertTrue(header.height >= PdfPageLayout.BADGE_SIZE)
    }

    @Test
    fun `placed blocks stay inside the content band on every page`() {
        val blocks = List(120) { i -> Block(height = 18f + (i % 5) * 7f, keepWithNext = i % 10 == 0) }
        val pages = PdfPageLayout.place(blocks)
        assertTrue(pages.size > 1)
        assertEquals(blocks.indices.toList(), pages.flatten().map { it.index })
        pages.forEach { page ->
            assertEquals(PdfPageLayout.content.top, page.first().top, 0.001f)
            page.forEach { placed ->
                val bottom = placed.top + blocks[placed.index].height
                assertTrue(placed.top >= PdfPageLayout.content.top)
                assertTrue("block ${placed.index} runs into the footer", bottom <= PdfPageLayout.content.bottom + 0.001f)
                assertTrue(placed.top >= PdfPageLayout.header.bottom)
            }
        }
    }

    @Test
    fun `pagination uses the content band height, not the whole page`() {
        val height = PdfPageLayout.content.height
        val blocks = listOf(Block(height / 2), Block(height / 2), Block(1f))
        assertEquals(listOf(listOf(0, 1), listOf(2)), PdfPageLayout.paginate(blocks))
        // A heading at the bottom of a page moves with its first item.
        val withHeading = listOf(Block(height - 10f), Block(8f, keepWithNext = true), Block(20f))
        assertEquals(listOf(listOf(0), listOf(1, 2)), PdfPageLayout.paginate(withHeading))
    }

    @Test
    fun `watermark is centred, faint and follows the page diagonal`() {
        assertEquals(PdfPageLayout.PAGE_WIDTH / 2f, PdfPageLayout.watermarkCenterX, 0.001f)
        assertEquals(PdfPageLayout.PAGE_HEIGHT / 2f, PdfPageLayout.watermarkCenterY, 0.001f)
        val diagonal = -Math.toDegrees(atan2(PdfPageLayout.PAGE_HEIGHT.toDouble(), PdfPageLayout.PAGE_WIDTH.toDouble()))
        assertEquals(diagonal.toFloat(), PdfPageLayout.watermarkAngle, 0.001f)
        val opacity = PdfPageLayout.WATERMARK_ALPHA / 255f
        assertTrue("opacity $opacity", opacity in 0.08f..0.10f)
    }

    @Test
    fun `watermark text always fits inside the page`() {
        // Width of "CheckList" per point of text size is about 4.5 in a sans-serif font; also try extremes.
        listOf(0.5f, 4.5f, 12f, 40f).forEach { widthPerPoint ->
            val size = PdfPageLayout.watermarkTextSize(widthPerPoint)
            assertTrue(size > 0f && size <= PdfPageLayout.WATERMARK_MAX_SIZE)
            assertTrue("width $widthPerPoint", PdfPageLayout.watermarkFits(widthPerPoint * size, 1.2f * size))
        }
        assertFalse(PdfPageLayout.watermarkFits(2000f, 100f))
    }

    @Test
    fun `export time comes from the injected clock in the app locale`() {
        val clock = Clock { 1_791_450_300_000L } // 2026-10-08T09:05:00Z
        val utc = PdfPageLayout.exportedAt(clock.nowMillis(), ZoneOffset.UTC, Locale.UK)
        assertEquals(utc, PdfPageLayout.exportedAt(clock.nowMillis(), ZoneOffset.UTC, Locale.UK))
        assertTrue(utc, utc.contains("2026") && utc.contains("Oct") && utc.contains("09:05"))
        val india = PdfPageLayout.exportedAt(clock.nowMillis(), ZoneId.of("Asia/Kolkata"), Locale.UK)
        assertTrue(india, india.contains("14:35"))
        val hindi = PdfPageLayout.exportedAt(clock.nowMillis(), ZoneOffset.UTC, Locale.forLanguageTag("hi"))
        assertNotEquals(utc, hindi)
    }
}
