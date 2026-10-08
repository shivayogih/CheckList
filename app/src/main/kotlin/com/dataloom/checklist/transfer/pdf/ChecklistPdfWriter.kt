package com.dataloom.checklist.transfer.pdf

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.dataloom.checklist.BuildConfig
import com.dataloom.checklist.R
import com.dataloom.checklist.domain.common.Clock
import com.dataloom.checklist.transfer.StoreLink
import com.dataloom.checklist.domain.model.ChecklistDetail
import com.dataloom.checklist.domain.model.ChecklistItem
import com.dataloom.checklist.domain.model.UnitCode
import com.dataloom.checklist.transfer.LocalizedResources
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.OutputStream
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject

/** PDF options (section 20.4). The name is off by default: the same privacy rule as JSON export. */
data class PdfOptions(
    val includeCompleted: Boolean = true,
    /** Printed under the title when not null, e.g. the profile name the user chose to include. */
    val preparedBy: String? = null,
)

/** Text for a unit code next to an amount ("kg", or a custom unit's label). */
fun interface UnitLabels {
    fun label(code: UnitCode): String
}

/** Writes one checklist as a printable PDF. */
interface ChecklistPdfWriter {
    /** Blocking; call from a background dispatcher. Does not close [out]. */
    fun write(detail: ChecklistDetail, options: PdfOptions, units: UnitLabels, out: OutputStream)
}

/**
 * A4 PDF drawn with [PdfDocument] and [StaticLayout] (section 20.4): large type, a drawn checkbox
 * per item (ticked when done), category headings kept with their first item. Every page is branded:
 * a header with the drawn app tick and name plus the checklist title, a footer with the "Powered by"
 * tagline, the export time (from the injected [Clock], in the app language) and the page number, and
 * a faint diagonal watermark drawn under the content. An import hint follows the last item. Once
 * `PLAY_STORE_URL` is set (see [StoreLink]) the footer also carries "Get the app on Google Play"
 * with the URL, and the hint sits in a "Get CheckList" box with a vector QR code of that URL
 * (CL-168). Geometry is in [PdfPageLayout].
 *
 * Text goes through the platform text stack (Minikin + HarfBuzz with the system font fallback
 * chain), so Kannada, Devanagari (Hindi, Marathi), Tamil, Telugu and Malayalam conjuncts and vowel
 * signs are shaped exactly as on screen. Each item is laid out with the locale of the language its
 * name was written in, so locale-specific glyph forms (for example Marathi versus Hindi in
 * Devanagari fonts) are chosen correctly. Known limits:
 * - glyphs come from the device's fonts; a device without a font for a script would show boxes,
 * - the PDF stores shaped glyphs; copying or searching Indic text inside some PDF viewers can give
 *   wrong characters, because conjunct glyphs do not map back one-to-one to Unicode,
 * - below Android 9 the line height ignores taller fallback fonts, so lines get extra padding
 *   instead (`includePad`) to avoid clipping tall Malayalam and Tamil glyphs,
 * - colour emoji in names may be drawn as bitmaps or left out, depending on the device.
 */
class AndroidChecklistPdfWriter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: Clock,
) : ChecklistPdfWriter {

    override fun write(detail: ChecklistDetail, options: PdfOptions, units: UnitLabels, out: OutputStream) {
        val resources = LocalizedResources.of(context)
        val appLocale = resources.configuration.locales[0] ?: Locale.getDefault()
        // The brand name is never translated (app_name is translatable="false").
        val brand = resources.getString(R.string.app_name)
        val branding = Branding(
            brand = brand,
            brandColor = resources.getColor(R.color.ic_launcher_background, null),
            tagline = resources.getString(R.string.pdf_powered_by, brand),
            exportedAt = resources.getString(
                R.string.pdf_exported_at,
                PdfPageLayout.exportedAt(clock.nowMillis(), ZoneId.systemDefault(), appLocale),
            ),
            storeLabel = resources.getString(R.string.pdf_get_app),
            // Null until PLAY_STORE_URL is set in app/build.gradle.kts: then no store line and no QR code.
            storeUrl = StoreLink.of(BuildConfig.PLAY_STORE_URL),
        )
        val blocks = buildBlocks(detail, options, units, resources, appLocale) + lastPageBlock(branding, resources, appLocale)
        val pages = PdfPageLayout.place(blocks.map { PdfPaginator.Block(it.height, it.keepWithNext) }, branding.storeLink)
        val document = PdfDocument()
        try {
            pages.forEachIndexed { pageIndex, placements ->
                val page = document.startPage(
                    PdfDocument.PageInfo.Builder(PdfPageLayout.PAGE_WIDTH, PdfPageLayout.PAGE_HEIGHT, pageIndex + 1).create(),
                )
                val canvas = page.canvas
                // Watermark first so everything else sits on top of it.
                drawWatermark(canvas, branding.brand, appLocale)
                drawHeader(canvas, branding, detail.checklist.title, appLocale)
                placements.forEach { blocks[it.index].draw(canvas, PdfPageLayout.CONTENT_LEFT, it.top) }
                drawFooter(canvas, branding, resources.getString(R.string.pdf_page_number, pageIndex + 1, pages.size), appLocale)
                document.finishPage(page)
            }
            document.writeTo(out)
        } finally {
            document.close()
        }
    }

    /** Text and colour shared by every page's header, footer and watermark. */
    private class Branding(
        val brand: String,
        val brandColor: Int,
        val tagline: String,
        val exportedAt: String,
        val storeLabel: String,
        val storeUrl: String?,
    ) {
        val storeLink: Boolean get() = storeUrl != null
    }

    private fun drawWatermark(canvas: Canvas, brand: String, locale: Locale) {
        val paint = paint(MEASURE_SIZE, bold = true, color = INK, locale = locale).apply {
            alpha = PdfPageLayout.WATERMARK_ALPHA
            textAlign = Paint.Align.CENTER
        }
        // Measure at a normal size (tiny sizes round badly) and scale to "per point of text size".
        val lineHeightPerPoint = paint.fontMetrics.let { it.descent - it.ascent } / MEASURE_SIZE
        paint.textSize = PdfPageLayout.watermarkTextSize(paint.measureText(brand) / MEASURE_SIZE, lineHeightPerPoint)
        val metrics = paint.fontMetrics
        canvas.save()
        canvas.rotate(PdfPageLayout.watermarkAngle, PdfPageLayout.watermarkCenterX, PdfPageLayout.watermarkCenterY)
        // Centre the text vertically on the page centre, not on its baseline.
        val baseline = PdfPageLayout.watermarkCenterY - (metrics.ascent + metrics.descent) / 2
        canvas.drawText(brand, PdfPageLayout.watermarkCenterX, baseline, paint)
        canvas.restore()
    }

    private fun drawHeader(canvas: Canvas, branding: Branding, title: String, locale: Locale) {
        val band = PdfPageLayout.header
        val left = PdfPageLayout.CONTENT_LEFT
        val badgeTop = band.top + (band.height - PdfPageLayout.BADGE_SIZE) / 2
        drawBadge(canvas, left, badgeTop, branding.brandColor)

        val nameLeft = left + PdfPageLayout.BADGE_SIZE + BADGE_GAP
        val nameWidth = paint(BRAND_SIZE, bold = true, locale = locale).measureText(branding.brand)
        val name = singleLine(branding.brand, paint(BRAND_SIZE, bold = true, color = branding.brandColor, locale = locale), nameWidth + 1f)
        drawCentredInBand(canvas, name, nameLeft, band)

        val titleLeft = nameLeft + nameWidth + BADGE_GAP * 2
        val titleWidth = PdfPageLayout.CONTENT_LEFT + PdfPageLayout.CONTENT_WIDTH - titleLeft
        if (titleWidth > 0f) {
            val running = singleLine(title, paint(SMALL_SIZE, color = MUTED, locale = locale), titleWidth, Layout.Alignment.ALIGN_OPPOSITE)
            drawCentredInBand(canvas, running, titleLeft, band)
        }
        canvas.drawLine(left, band.bottom, left + PdfPageLayout.CONTENT_WIDTH, band.bottom, RULE_PAINT)
    }

    /** The launcher icon, drawn: a white tick on a rounded square in the brand colour. */
    private fun drawBadge(canvas: Canvas, x: Float, y: Float, color: Int) {
        val size = PdfPageLayout.BADGE_SIZE
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawRoundRect(RectF(x, y, x + size, y + size), size * 0.22f, size * 0.22f, fill)
        // Same points as ic_launcher_foreground (M36,55 L49,68 L73,42), scaled from its 66dp safe zone.
        fun px(v: Float) = x + (v - 21f) / 66f * size
        fun py(v: Float) = y + (v - 21f) / 66f * size
        val tick = Path().apply {
            moveTo(px(36f), py(55f))
            lineTo(px(49f), py(68f))
            lineTo(px(73f), py(42f))
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = size * 0.12f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.color = Color.WHITE
        }
        canvas.drawPath(tick, stroke)
    }

    private fun drawFooter(canvas: Canvas, branding: Branding, pageNumber: String, locale: Locale) {
        val left = PdfPageLayout.CONTENT_LEFT
        val width = PdfPageLayout.CONTENT_WIDTH
        val footer = PdfPageLayout.footer(branding.storeLink)
        canvas.drawLine(left, footer.top, left + width, footer.top, RULE_PAINT)

        val first = PdfPageLayout.footerFirstRow(branding.storeLink)
        val column = width / 3
        val footerPaint = { paint(FOOTER_SIZE, color = MUTED, locale = locale) }
        drawCentredInBand(canvas, singleLine(branding.tagline, footerPaint(), column), left, first)
        drawCentredInBand(canvas, singleLine(branding.exportedAt, footerPaint(), column, Layout.Alignment.ALIGN_CENTER), left + column, first)
        drawCentredInBand(canvas, singleLine(pageNumber, footerPaint(), column, Layout.Alignment.ALIGN_OPPOSITE), left + 2 * column, first)

        // "Get the app on Google Play" then the full store URL: the URL is never cut, the label may be.
        val storeUrl = branding.storeUrl ?: return
        val second = PdfPageLayout.footerSecondRow(true) ?: return
        val urlPaint = paint(STORE_URL_SIZE, color = branding.brandColor, locale = locale)
        val urlWidth = minOf(width, urlPaint.measureText(storeUrl) + 1f)
        drawCentredInBand(canvas, singleLine(storeUrl, urlPaint, urlWidth, Layout.Alignment.ALIGN_OPPOSITE), left + width - urlWidth, second)
        val labelWidth = width - urlWidth - FOOTER_COLUMN_GAP
        if (labelWidth > 0f) {
            drawCentredInBand(canvas, singleLine(branding.storeLabel, paint(STORE_URL_SIZE, color = MUTED, locale = locale), labelWidth), left, second)
        }
    }

    private fun drawCentredInBand(canvas: Canvas, layout: StaticLayout, x: Float, band: PdfPageLayout.Band) {
        canvas.save()
        canvas.translate(x, band.top + (band.height - layout.height) / 2)
        layout.draw(canvas)
        canvas.restore()
    }

    /** One line, cut with an ellipsis when too long, so it never grows out of its band. */
    private fun singleLine(
        text: CharSequence,
        paint: TextPaint,
        width: Float,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
    ): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
            .setAlignment(alignment)
            .setMaxLines(1)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

    private fun buildBlocks(
        detail: ChecklistDetail,
        options: PdfOptions,
        units: UnitLabels,
        resources: Resources,
        appLocale: Locale,
    ): List<Block> = buildList<Block> {
        add(headerBlock(detail, options, resources, appLocale))
        var printedItems = 0
        detail.sections.forEach { section ->
            val items = section.items.filter { options.includeCompleted || !it.isCompleted }
            if (items.isEmpty()) return@forEach
            add(TextBlock(layout(section.category.displayName, paint(HEADING_SIZE, bold = true, locale = appLocale), CONTENT_WIDTH), SECTION_GAP, RULE_GAP, rule = true, keepWithNext = true))
            items.forEach { add(itemBlock(it, units)) }
            printedItems += items.size
        }
        if (printedItems == 0) {
            add(TextBlock(layout(resources.getString(R.string.pdf_no_items), paint(BODY_SIZE, color = MUTED, locale = appLocale), CONTENT_WIDTH), SECTION_GAP, 0f))
        }
    }

    /**
     * The last block, so it lands on the last page. With a store link: the "Get CheckList" box, a
     * vector QR code of the store URL beside the URL in text and the import hint. Without one: just
     * the import hint (a PDF cannot be imported; the sender's .json file can).
     */
    private fun lastPageBlock(branding: Branding, resources: Resources, appLocale: Locale): Block {
        val hint = resources.getString(R.string.pdf_import_hint, branding.brand)
        val storeUrl = branding.storeUrl
            ?: return TextBlock(layout(hint, paint(SMALL_SIZE, color = MUTED, locale = appLocale), CONTENT_WIDTH), PdfPageLayout.PROMO_GAP, 0f)
        val width = PdfPageLayout.PROMO_TEXT_WIDTH
        val lines = listOf(
            layout(resources.getString(R.string.pdf_promo_title, branding.brand), paint(BODY_SIZE, bold = true, color = branding.brandColor, locale = appLocale), width),
            layout(resources.getString(R.string.pdf_promo_scan), paint(SMALL_SIZE, locale = appLocale), width),
            layout(storeUrl, paint(STORE_URL_SIZE, color = branding.brandColor, locale = appLocale), width),
            layout(hint, paint(STORE_URL_SIZE, color = MUTED, locale = appLocale), width),
        )
        return PromoBlock(StackBlock(lines, PROMO_LINE_GAP, after = 0f), PdfQrCode.encode(storeUrl), branding.brandColor)
    }

    private fun headerBlock(detail: ChecklistDetail, options: PdfOptions, resources: Resources, appLocale: Locale): Block {
        val lines = buildList {
            add(layout(detail.checklist.title, paint(TITLE_SIZE, bold = true, locale = appLocale), CONTENT_WIDTH))
            detail.checklist.description?.let { add(layout(it, paint(BODY_SIZE, locale = appLocale), CONTENT_WIDTH)) }
            options.preparedBy?.takeIf { it.isNotBlank() }?.let {
                add(layout(resources.getString(R.string.pdf_prepared_by, it), paint(SMALL_SIZE, color = MUTED, locale = appLocale), CONTENT_WIDTH))
            }
            val progress = resources.getString(R.string.pdf_progress, detail.completedItems, detail.totalItems)
            add(layout(progress, paint(SMALL_SIZE, color = MUTED, locale = appLocale), CONTENT_WIDTH))
        }
        return StackBlock(lines, LINE_GAP, after = HEADER_GAP)
    }

    private fun itemBlock(item: ChecklistItem, units: UnitLabels): Block {
        val locale = Locale.forLanguageTag(item.displayNameLocale)
        val color = if (item.isCompleted) MUTED else INK
        val amount = item.quantity?.let { quantity ->
            listOfNotNull(quantity.toPlainString(), item.unit?.let(units::label)).joinToString(" ")
        }
        val name = if (amount == null) item.displayName else "${item.displayName}  ·  $amount"
        val textWidth = CONTENT_WIDTH - CHECKBOX_COLUMN
        val lines = listOfNotNull(
            layout(name, paint(ITEM_SIZE, color = color, locale = locale), textWidth),
            item.notes?.let { layout(it, paint(SMALL_SIZE, color = MUTED, locale = locale), textWidth) },
        )
        return ItemBlock(StackBlock(lines, LINE_GAP, after = ITEM_GAP), item.isCompleted)
    }

    private fun paint(size: Float, bold: Boolean = false, color: Int = INK, locale: Locale): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            this.color = color
            textLocale = locale
        }

    private fun layout(
        text: CharSequence,
        paint: TextPaint,
        width: Float,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
    ): StaticLayout {
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt())
            .setAlignment(alignment)
            .setLineSpacing(0f, LINE_SPACING)
            // Builder defaults: simple line breaking and no hyphenation, which Indic scripts do not use.
            .setIncludePad(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Indic fallback fonts are taller than the default Latin font; size lines for them.
            builder.setUseLineSpacingFromFallbacks(true)
        }
        return builder.build()
    }

    /** Something drawn at the left margin with a known height. */
    private interface Block {
        val height: Float
        val keepWithNext: Boolean get() = false

        fun draw(canvas: Canvas, x: Float, y: Float)
    }

    private class TextBlock(
        private val layout: StaticLayout,
        private val before: Float,
        private val after: Float,
        private val rule: Boolean = false,
        override val keepWithNext: Boolean = false,
    ) : Block {
        override val height: Float = before + layout.height + after

        override fun draw(canvas: Canvas, x: Float, y: Float) {
            canvas.save()
            canvas.translate(x, y + before)
            layout.draw(canvas)
            canvas.restore()
            if (rule) {
                val lineY = y + before + layout.height + after / 2
                canvas.drawLine(x, lineY, x + CONTENT_WIDTH, lineY, RULE_PAINT)
            }
        }
    }

    private class StackBlock(private val layouts: List<StaticLayout>, private val gap: Float, private val after: Float) : Block {
        override val height: Float = layouts.sumOf { it.height.toDouble() }.toFloat() + gap * (layouts.size - 1) + after

        val firstLineHeight: Float get() = layouts.firstOrNull()?.let { (it.getLineBottom(0) - it.getLineTop(0)).toFloat() } ?: 0f

        override fun draw(canvas: Canvas, x: Float, y: Float) {
            var top = y
            layouts.forEach {
                canvas.save()
                canvas.translate(x, top)
                it.draw(canvas)
                canvas.restore()
                top += it.height + gap
            }
        }
    }

    private class ItemBlock(private val text: StackBlock, private val completed: Boolean) : Block {
        override val height: Float get() = text.height

        override fun draw(canvas: Canvas, x: Float, y: Float) {
            // Centre the box on the first line of the name, however tall the script's line is.
            val top = y + (text.firstLineHeight - CHECKBOX_SIZE) / 2
            canvas.drawRect(x, top, x + CHECKBOX_SIZE, top + CHECKBOX_SIZE, BOX_PAINT)
            if (completed) {
                val tick = Path().apply {
                    moveTo(x + CHECKBOX_SIZE * 0.2f, top + CHECKBOX_SIZE * 0.55f)
                    lineTo(x + CHECKBOX_SIZE * 0.42f, top + CHECKBOX_SIZE * 0.78f)
                    lineTo(x + CHECKBOX_SIZE * 0.82f, top + CHECKBOX_SIZE * 0.25f)
                }
                canvas.drawPath(tick, TICK_PAINT)
            }
            text.draw(canvas, x + CHECKBOX_COLUMN, y)
        }
    }

    /** Box with the QR code on the left and the text column on the right; geometry in [PdfPageLayout]. */
    private class PromoBlock(private val text: StackBlock, private val qr: PdfQrCode.Matrix, private val color: Int) : Block {
        override val height: Float = PdfPageLayout.promoBlockHeight(text.height)

        override fun draw(canvas: Canvas, x: Float, y: Float) {
            val box = PdfPageLayout.promoBox(y, text.height)
            val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                this.color = color
            }
            canvas.drawRoundRect(RectF(box.left, box.top, box.right, box.bottom), PROMO_CORNER, PROMO_CORNER, border)
            val area = PdfPageLayout.promoQr(y)
            // No anti-aliasing: neighbouring modules must meet without hairline gaps.
            val module = Paint().apply { this.color = Color.BLACK }
            // A white quiet zone over the watermark keeps the contrast scanners need.
            canvas.drawRect(area.left, area.top, area.right, area.bottom, Paint().apply { this.color = Color.WHITE })
            PdfQrCode.darkRects(qr, area.left, area.top, area.right - area.left).forEach {
                canvas.drawRect(it.left, it.top, it.right, it.bottom, module)
            }
            text.draw(canvas, PdfPageLayout.PROMO_TEXT_LEFT, box.top + PdfPageLayout.PROMO_PADDING)
        }
    }

    private companion object {
        // Page geometry (A4 in points, header and footer bands) lives in PdfPageLayout.
        const val CONTENT_WIDTH = PdfPageLayout.CONTENT_WIDTH
        const val BRAND_SIZE = 14f
        const val BADGE_GAP = 6f
        const val MEASURE_SIZE = 100f
        const val STORE_URL_SIZE = 9f
        const val FOOTER_COLUMN_GAP = 8f
        const val PROMO_LINE_GAP = 4f
        const val PROMO_CORNER = 6f

        // Large type: the PDF is often printed for elders or read on a phone.
        const val TITLE_SIZE = 24f
        const val HEADING_SIZE = 17f
        const val ITEM_SIZE = 15f
        const val BODY_SIZE = 13f
        const val SMALL_SIZE = 12f
        const val FOOTER_SIZE = 10f
        const val LINE_SPACING = 1.15f

        const val LINE_GAP = 2f
        const val HEADER_GAP = 12f
        const val SECTION_GAP = 14f
        const val RULE_GAP = 8f
        const val ITEM_GAP = 8f
        const val CHECKBOX_SIZE = 13f
        const val CHECKBOX_COLUMN = 24f

        const val INK = 0xFF1B1B1F.toInt()
        const val MUTED = 0xFF5F6368.toInt()

        val BOX_PAINT = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            color = INK
        }
        val TICK_PAINT = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = INK
        }
        val RULE_PAINT = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 0.6f
            color = MUTED
        }
    }
}
