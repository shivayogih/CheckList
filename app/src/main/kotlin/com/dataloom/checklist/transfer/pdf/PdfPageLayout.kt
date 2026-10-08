package com.dataloom.checklist.transfer.pdf

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Page geometry of the PDF export (section 20.4), kept pure so placement is unit tested without a
 * device. Every page has, top to bottom: a branded header band (tick badge, app name, checklist
 * title), the content area, and a footer band (tagline, export time, page number). A faint diagonal
 * watermark is drawn first, under everything else. Units are PDF points (1/72 inch); A4.
 */
object PdfPageLayout {

    /** A horizontal strip of the page, from [top] to [bottom]. */
    data class Band(val top: Float, val bottom: Float) {
        val height: Float get() = bottom - top

        fun overlaps(other: Band): Boolean = top < other.bottom && other.top < bottom
    }

    /** Where a measured content block is drawn on its page. */
    data class Placement(val index: Int, val top: Float)

    const val PAGE_WIDTH = 595
    const val PAGE_HEIGHT = 842
    const val MARGIN = 48f
    const val CONTENT_LEFT = MARGIN
    const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN

    const val HEADER_HEIGHT = 28f
    const val HEADER_GAP = 14f
    const val FOOTER_HEIGHT = 22f
    const val FOOTER_GAP = 14f

    /** Side of the drawn tick badge in the header. */
    const val BADGE_SIZE = 20f

    /** Watermark opacity out of 255: about 9 %, faint enough that printed text stays readable. */
    const val WATERMARK_ALPHA = 23

    /** The watermark spans at most this share of the page diagonal. */
    const val WATERMARK_SPAN = 0.6f

    /** Upper bound so a short brand name is not drawn absurdly large. */
    const val WATERMARK_MAX_SIZE = 120f

    val header = Band(MARGIN, MARGIN + HEADER_HEIGHT)
    val footer = Band(PAGE_HEIGHT - MARGIN - FOOTER_HEIGHT, PAGE_HEIGHT - MARGIN)
    val content = Band(header.bottom + HEADER_GAP, footer.top - FOOTER_GAP)

    val watermarkCenterX: Float get() = PAGE_WIDTH / 2f
    val watermarkCenterY: Float get() = PAGE_HEIGHT / 2f

    /** Rotation in degrees for `Canvas.rotate`: along the diagonal from bottom left to top right. */
    val watermarkAngle: Float
        get() = -Math.toDegrees(atan2(PAGE_HEIGHT.toDouble(), PAGE_WIDTH.toDouble())).toFloat()

    /**
     * Text size for the watermark, given the text width per point of text size (measured width
     * divided by the text size). The rotated text, with [lineHeightPerPoint] as its height per point of text
     * size, always fits inside the page.
     */
    fun watermarkTextSize(widthPerPoint: Float, lineHeightPerPoint: Float = 1.2f): Float {
        if (widthPerPoint <= 0f) return WATERMARK_MAX_SIZE
        val diagonal = hypot(PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat())
        var size = min(WATERMARK_MAX_SIZE, diagonal * WATERMARK_SPAN / widthPerPoint)
        // Shrink until the rotated box (text width x line height) stays inside the page.
        while (size > 1f && !watermarkFits(widthPerPoint * size, lineHeightPerPoint * size)) size *= 0.95f
        return size
    }

    /** True when a [width] x [height] box rotated by [watermarkAngle] about the centre fits the page. */
    fun watermarkFits(width: Float, height: Float): Boolean {
        val radians = Math.toRadians(watermarkAngle.toDouble())
        val c = abs(cos(radians))
        val s = abs(sin(radians))
        val boundsWidth = width * c + height * s
        val boundsHeight = width * s + height * c
        return boundsWidth <= PAGE_WIDTH && boundsHeight <= PAGE_HEIGHT
    }

    /** Splits measured blocks into pages that fit the [content] band. */
    fun paginate(blocks: List<PdfPaginator.Block>): List<List<Int>> = PdfPaginator.paginate(blocks, content.height)

    /** Top of each block on each page: stacked from the top of the [content] band. */
    fun place(blocks: List<PdfPaginator.Block>): List<List<Placement>> =
        paginate(blocks).map { indexes ->
            var y = content.top
            indexes.map { index -> Placement(index, y).also { y += blocks[index].height } }
        }

    /**
     * The export time for the footer, in the app's language: medium date and short time, for
     * example "8 Oct 2026, 09:05" in English (UK).
     */
    fun exportedAt(epochMillis: Long, zone: ZoneId, locale: Locale): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
            .format(Instant.ofEpochMilli(epochMillis).atZone(zone))
}
