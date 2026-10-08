package com.dataloom.checklist.transfer.pdf

/**
 * Splits measured blocks into pages. Pure, so page breaking is unit tested without a device.
 * A block with [Block.keepWithNext] (a category heading) moves to the next page together with the
 * block after it rather than being left alone at the bottom of a page. A block taller than a page
 * gets a page of its own.
 */
object PdfPaginator {

    data class Block(val height: Float, val keepWithNext: Boolean = false)

    /** Indexes of the blocks on each page; at least one (possibly empty) page. */
    fun paginate(blocks: List<Block>, pageHeight: Float): List<List<Int>> {
        val pages = ArrayList<List<Int>>()
        var current = ArrayList<Int>()
        var used = 0f

        fun newPage() {
            if (current.isEmpty()) return
            pages += current
            current = ArrayList()
            used = 0f
        }

        var start = 0
        while (start < blocks.size) {
            var end = start
            while (blocks[end].keepWithNext && end + 1 < blocks.size) end++
            val groupHeight = (start..end).fold(0f) { sum, index -> sum + blocks[index].height }
            if (used + groupHeight > pageHeight) newPage()
            for (index in start..end) {
                if (used + blocks[index].height > pageHeight) newPage()
                current += index
                used += blocks[index].height
            }
            start = end + 1
        }
        if (current.isNotEmpty() || pages.isEmpty()) pages += current
        return pages
    }
}
