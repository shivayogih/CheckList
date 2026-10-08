package com.dataloom.checklist.transfer.pdf

import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

/**
 * QR code for the PDF export, encoded with zxing core and turned into plain rectangles so the
 * writer draws it as vector shapes (sharp at any print size). `PdfDocument` cannot add clickable
 * link annotations, so the QR code plus the printed URL are how a reader gets to the store page.
 * Pure: unit tested on the JVM, including a decode round trip.
 */
object PdfQrCode {

    /** Light border around the code, in modules, as the QR specification requires. */
    const val QUIET_ZONE = 4

    /** Square module matrix without the quiet zone; `true` is a dark module. */
    class Matrix internal constructor(val size: Int, private val dark: BooleanArray) {
        operator fun get(x: Int, y: Int): Boolean = dark[y * size + x]
    }

    /** Encodes [text] as UTF-8 at error correction level M (about 15 % of the code can be damaged). */
    fun encode(text: String): Matrix {
        val code = Encoder.encode(text, ErrorCorrectionLevel.M, mapOf(EncodeHintType.CHARACTER_SET to Charsets.UTF_8.name()))
        val bytes = code.matrix
        val size = bytes.width
        val dark = BooleanArray(size * size) { bytes.get(it % size, it / size).toInt() == 1 }
        return Matrix(size, dark)
    }

    /**
     * Dark modules as rectangles for a code whose outer square, quiet zone included, is [side]
     * points wide with its top left corner at ([left], [top]). Neighbouring dark modules in a row
     * are merged into one rectangle to keep the PDF small.
     */
    fun darkRects(matrix: Matrix, left: Float, top: Float, side: Float): List<PdfPageLayout.Rect> {
        val module = side / (matrix.size + 2 * QUIET_ZONE)
        val origin = QUIET_ZONE * module
        val rects = ArrayList<PdfPageLayout.Rect>()
        for (y in 0 until matrix.size) {
            var x = 0
            while (x < matrix.size) {
                if (!matrix[x, y]) {
                    x++
                    continue
                }
                val start = x
                while (x < matrix.size && matrix[x, y]) x++
                rects += PdfPageLayout.Rect(
                    left = left + origin + start * module,
                    top = top + origin + y * module,
                    right = left + origin + x * module,
                    bottom = top + origin + (y + 1) * module,
                )
            }
        }
        return rects
    }
}
