package com.dataloom.checklist.presentation.home

import org.junit.Assert.assertEquals
import org.junit.Test

class CopyTitleTest {

    private val pattern: (String) -> String = { "$it (copy)" }

    @Test
    fun `short titles get the localized suffix`() {
        assertEquals("Goa Trip (copy)", copyTitle("  Goa Trip ", pattern))
    }

    @Test
    fun `long titles are shortened so the copy stays within the limit`() {
        val title = "a".repeat(100)
        val copy = copyTitle(title, pattern, max = 100)
        assertEquals(100, copy.codePointCount(0, copy.length))
        assertEquals("a".repeat(93) + " (copy)", copy)
    }

    @Test
    fun `shortening counts code points, not UTF-16 units`() {
        val title = "😀".repeat(10)
        val copy = copyTitle(title, pattern, max = 12)
        assertEquals("😀😀😀😀😀 (copy)", copy)
    }
}
