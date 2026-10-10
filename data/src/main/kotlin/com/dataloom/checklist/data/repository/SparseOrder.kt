package com.dataloom.checklist.data.repository

/**
 * Sparse ordering for display_order and position (section 8): values are spaced [STEP] apart so a
 * move usually rewrites only the moved row; when two neighbours leave no gap, the list is renumbered.
 */
object SparseOrder {

    const val STEP = 1000

    data class Entry(val id: String, val order: Int)

    /** Order value for appending after [last] (null for an empty list). */
    fun after(last: Int?): Int = (last ?: 0) + STEP

    /** Orders for a list appended after [last]. */
    fun appended(last: Int?, count: Int): List<Int> = List(count) { index -> after(last) + index * STEP }

    /**
     * New order values after moving [movedId] to [toIndex] in [siblings] (already in display order).
     * Returns only the rows that change; empty when nothing moves or the ID is unknown.
     */
    fun move(siblings: List<Entry>, movedId: String, toIndex: Int): Map<String, Int> {
        val currentIndex = siblings.indexOfFirst { it.id == movedId }
        if (currentIndex < 0) return emptyMap()
        val others = siblings.filterIndexed { index, _ -> index != currentIndex }
        val target = toIndex.coerceIn(0, others.size)
        if (target == currentIndex) return emptyMap()

        val previous = others.getOrNull(target - 1)?.order?.toLong()
        val next = others.getOrNull(target)?.order?.toLong()
        val candidate = when {
            previous == null && next == null -> STEP.toLong()
            previous == null -> next!! - STEP
            next == null -> previous + STEP
            next - previous > 1 -> (previous + next) / 2
            else -> null
        }
        if (candidate != null && candidate in Int.MIN_VALUE..Int.MAX_VALUE) {
            return mapOf(movedId to candidate.toInt())
        }

        val reordered = others.toMutableList().apply { add(target, siblings[currentIndex]) }
        return reordered.mapIndexedNotNull { index, entry ->
            val order = (index + 1) * STEP
            if (entry.order == order) null else entry.id to order
        }.toMap()
    }
}
