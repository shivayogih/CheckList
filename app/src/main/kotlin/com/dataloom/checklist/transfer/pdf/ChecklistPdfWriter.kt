package com.dataloom.checklist.transfer.pdf

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.dataloom.checklist.R
import com.dataloom.checklist.domain.model.ChecklistDetail
import com.dataloom.checklist.domain.model.ChecklistItem
import com.dataloom.checklist.domain.model.UnitCode
import com.dataloom.checklist.transfer.LocalizedResources
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.OutputStream
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
 * per item (ticked when done), category headings kept with their first item, a page footer.
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
) : ChecklistPdfWriter {

    override fun write(detail: ChecklistDetail, options: PdfOptions, units: UnitLabels, out: OutputStream) {
        val resources = LocalizedResources.of(context)
        val appLocale = resources.configuration.locales[0] ?: Locale.getDefault()
        val blocks = buildBlocks(detail, options, units, resources, appLocale)
        val pages = PdfPaginator.paginate(blocks.map { PdfPaginator.Block(it.height, it.keepWithNext) }, CONTENT_HEIGHT)
        val document = PdfDocument()
        try {
            pages.forEachIndexed { pageIndex, blockIndexes ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create())
                val canvas = page.canvas
                var y = MARGIN
                blockIndexes.forEach { index ->
                    blocks[index].draw(canvas, MARGIN, y)
                    y += blocks[index].height
                }
                drawFooter(canvas, resources.getString(R.string.pdf_page_number, pageIndex + 1, pages.size), appLocale)
                document.finishPage(page)
            }
            document.writeTo(out)
        } finally {
            document.close()
        }
    }

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

    private fun drawFooter(canvas: Canvas, text: String, locale: Locale) {
        val footer = layout(text, paint(FOOTER_SIZE, color = MUTED, locale = locale), CONTENT_WIDTH, Layout.Alignment.ALIGN_CENTER)
        canvas.save()
        canvas.translate(MARGIN, PAGE_HEIGHT - MARGIN - footer.height + FOOTER_HEIGHT / 2)
        footer.draw(canvas)
        canvas.restore()
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
            .setIncludePad(true)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
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

    private companion object {
        // PDF units are points (1/72 inch). A4 is 595 x 842 points.
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 48f
        const val FOOTER_HEIGHT = 24f
        const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
        const val CONTENT_HEIGHT = PAGE_HEIGHT - 2 * MARGIN - FOOTER_HEIGHT

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
