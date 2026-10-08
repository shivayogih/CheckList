package com.dataloom.checklist.transfer

import com.dataloom.checklist.domain.common.Clock
import com.dataloom.checklist.transfer.pdf.PdfPageLayout
import com.dataloom.checklist.transfer.pdf.PdfQrCode
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.dataloom.checklist.transfer.pdf.PdfPaginator
import com.dataloom.checklist.transfer.pdf.PdfPaginator.Block
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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
        listOf(false, true).forEach { storeLink ->
            val header = PdfPageLayout.header
            val content = PdfPageLayout.content(storeLink)
            val footer = PdfPageLayout.footer(storeLink)
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
    }

    @Test
    fun `footer rows sit inside the footer band, and the store row only exists with a link`() {
        val without = PdfPageLayout.footer(storeLink = false)
        val withLink = PdfPageLayout.footer(storeLink = true)
        assertNull(PdfPageLayout.footerSecondRow(storeLink = false))
        val first = PdfPageLayout.footerFirstRow(storeLink = false)
        assertTrue(first.top > without.top && first.bottom <= without.bottom)

        val firstWith = PdfPageLayout.footerFirstRow(storeLink = true)
        val second = PdfPageLayout.footerSecondRow(storeLink = true)!!
        assertTrue(firstWith.top > withLink.top && second.bottom <= withLink.bottom)
        assertFalse(firstWith.overlaps(second))
        assertEquals(PdfPageLayout.PAGE_HEIGHT - PdfPageLayout.MARGIN, second.bottom, 0.001f)
        // The extra row takes its room from the content area, never from the margin.
        assertTrue(PdfPageLayout.content(true).height < PdfPageLayout.content(false).height)
        assertEquals(without.bottom, withLink.bottom, 0.001f)
    }

    @Test
    fun `placed blocks stay inside the content band on every page`() {
        val blocks = List(120) { i -> Block(height = 18f + (i % 5) * 7f, keepWithNext = i % 10 == 0) }
        listOf(false, true).forEach { storeLink ->
            val content = PdfPageLayout.content(storeLink)
            val pages = PdfPageLayout.place(blocks, storeLink)
            assertTrue(pages.size > 1)
            assertEquals(blocks.indices.toList(), pages.flatten().map { it.index })
            pages.forEach { page ->
                assertEquals(content.top, page.first().top, 0.001f)
                page.forEach { placed ->
                    val bottom = placed.top + blocks[placed.index].height
                    assertTrue(placed.top >= PdfPageLayout.header.bottom)
                    assertTrue("block ${placed.index} runs into the footer", bottom <= content.bottom + 0.001f)
                    assertTrue(bottom <= PdfPageLayout.footer(storeLink).top)
                }
            }
        }
    }

    @Test
    fun `pagination uses the content band height, not the whole page`() {
        listOf(false, true).forEach { storeLink ->
            val height = PdfPageLayout.content(storeLink).height
            val blocks = listOf(Block(height / 2), Block(height / 2), Block(1f))
            assertEquals(listOf(listOf(0, 1), listOf(2)), PdfPageLayout.paginate(blocks, storeLink))
            // A heading at the bottom of a page moves with its first item.
            val withHeading = listOf(Block(height - 10f), Block(8f, keepWithNext = true), Block(20f))
            assertEquals(listOf(listOf(0), listOf(1, 2)), PdfPageLayout.paginate(withHeading, storeLink))
        }
    }

    @Test
    fun `the Get CheckList box and its QR code stay inside the margins and clear of the footer`() {
        val textHeight = 60f
        val promo = Block(PdfPageLayout.promoBlockHeight(textHeight))
        // Fill most of a page so the box has to move to a new page, then check where it lands.
        val content = PdfPageLayout.content(storeLink = true)
        val blocks = List(3) { Block(content.height / 3 - 1f) } + promo
        val pages = PdfPageLayout.place(blocks, storeLink = true)
        val placed = pages.last().single { it.index == blocks.lastIndex }
        val area = PdfPageLayout.Rect(
            PdfPageLayout.MARGIN,
            content.top,
            PdfPageLayout.PAGE_WIDTH - PdfPageLayout.MARGIN,
            content.bottom,
        )
        val box = PdfPageLayout.promoBox(placed.top, textHeight)
        val qr = PdfPageLayout.promoQr(placed.top)
        assertTrue(area.contains(box))
        assertTrue(box.contains(qr))
        assertEquals(PdfPageLayout.PROMO_QR_SIZE, qr.right - qr.left, 0.001f)
        assertTrue("text column starts right of the QR code", PdfPageLayout.PROMO_TEXT_LEFT >= qr.right)
        assertEquals(box.right - PdfPageLayout.PROMO_PADDING, PdfPageLayout.PROMO_TEXT_LEFT + PdfPageLayout.PROMO_TEXT_WIDTH, 0.001f)
        assertTrue(box.bottom <= PdfPageLayout.footer(storeLink = true).top)
        // A text column taller than the QR code makes the box grow, not overflow.
        val tall = PdfPageLayout.promoBox(0f, 200f)
        assertTrue(tall.bottom - tall.top >= 200f + 2 * PdfPageLayout.PROMO_PADDING)
    }

    @Test
    fun `the QR code decodes back to the store URL`() {
        val url = "https://play.google.com/store/apps/details?id=com.dataloom.checklist"
        val matrix = PdfQrCode.encode(url)
        val scale = 4
        val side = (matrix.size + 2 * PdfQrCode.QUIET_ZONE) * scale
        val rects = PdfQrCode.darkRects(matrix, left = 0f, top = 0f, side = side.toFloat())
        val canvas = PdfPageLayout.Rect(0f, 0f, side.toFloat(), side.toFloat())
        rects.forEach { assertTrue(canvas.contains(it)) }
        // Rasterise the drawn rectangles, then read them back with zxing's reader.
        val pixels = IntArray(side * side) { 0xFFFFFFFF.toInt() }
        rects.forEach { r ->
            for (y in r.top.roundToInt() until r.bottom.roundToInt()) {
                for (x in r.left.roundToInt() until r.right.roundToInt()) pixels[y * side + x] = 0xFF000000.toInt()
            }
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels)))
        val decoded = QRCodeReader().decode(bitmap, mapOf(DecodeHintType.PURE_BARCODE to true))
        assertEquals(url, decoded.text)
    }

    @Test
    fun `dark modules in a row are merged and nothing is drawn in the quiet zone`() {
        val matrix = PdfQrCode.encode("https://example.org/x")
        val rects = PdfQrCode.darkRects(matrix, left = 10f, top = 20f, side = (matrix.size + 2 * PdfQrCode.QUIET_ZONE).toFloat())
        val darkModules = (0 until matrix.size).sumOf { y -> (0 until matrix.size).count { x -> matrix[x, y] } }
        val covered = rects.sumOf { (it.right - it.left).toDouble() }.roundToInt()
        assertEquals(darkModules, covered)
        assertTrue(rects.size < darkModules)
        assertTrue(rects.all { it.left >= 14f && it.top >= 24f })
    }

    @Test
    fun `the store link is hidden until a real https URL is configured`() {
        assertNull(StoreLink.of(""))
        assertNull(StoreLink.of("   "))
        assertNull(StoreLink.of("https://"))
        assertNull(StoreLink.of("http://play.google.com/store/apps/details?id=com.dataloom.checklist"))
        assertEquals(
            "https://play.google.com/store/apps/details?id=com.dataloom.checklist",
            StoreLink.of(" https://play.google.com/store/apps/details?id=com.dataloom.checklist "),
        )
    }

    @Test
    fun `share message has the import steps, and the store line only with a link`() {
        val strings = ExportShareStrings(
            subject = "CheckList lists",
            intro = "This is a CheckList file with one or more checklists.",
            getApp = { url -> "Get CheckList on Google Play: $url" },
            stepsTitle = "To import it:",
            steps = listOf(
                "1. Install CheckList.",
                "2. Open the app → Settings → Import.",
                "3. Choose this file.",
                "4. Review the preview and confirm.",
            ),
        )
        val without = ExportShareText.build(strings, storeUrl = null)
        assertEquals("CheckList lists", without.subject)
        assertEquals(
            "This is a CheckList file with one or more checklists.\n\nTo import it:\n1. Install CheckList.\n" +
                "2. Open the app → Settings → Import.\n3. Choose this file.\n4. Review the preview and confirm.",
            without.text,
        )
        assertFalse(without.text.contains("Google Play"))

        val url = "https://play.google.com/store/apps/details?id=com.dataloom.checklist"
        val withLink = ExportShareText.build(strings, storeUrl = url)
        assertEquals(
            "This is a CheckList file with one or more checklists.\n\nGet CheckList on Google Play: $url\n\n" +
                "To import it:\n1. Install CheckList.\n2. Open the app → Settings → Import.\n3. Choose this file.\n" +
                "4. Review the preview and confirm.",
            withLink.text,
        )
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
