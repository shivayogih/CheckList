package com.dataloom.checklist.transfer

import com.dataloom.checklist.transfer.pdf.PdfPaginator
import com.dataloom.checklist.transfer.pdf.PdfPaginator.Block
import java.time.LocalDate
import org.junit.Assert.assertEquals
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
}
