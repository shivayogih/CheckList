package com.dataloom.checklist.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputTextTest {

    @Test
    fun `control, bidi and zero width characters are removed`() {
        assertEquals("ab", InputText.clean("a\u0000b"))
        assertEquals("ab", InputText.clean("a\u202Eb"))
        assertEquals("ab", InputText.clean("a\u2066b\u2069"))
        assertEquals("ab", InputText.clean("a\u200Bb\uFEFF\u2060\u00AD"))
        assertEquals("ab", InputText.clean("a\u200Fb\u200E\u061C"))
        assertEquals("ab", InputText.clean("a\u007Fb"))
    }

    @Test
    fun `joiners stay because Indic spelling and emoji need them`() {
        val kannada = "\u0C95\u0CCD\u200D\u0CB7"
        assertEquals(kannada, InputText.clean(kannada))
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"
        assertEquals(family, InputText.clean(family))
        assertEquals("a\u200Cb", InputText.clean("a\u200Cb"))
    }

    @Test
    fun `single line turns breaks and tabs into one space`() {
        assertEquals("a b c", InputText.clean("a\nb\r\nc"))
        assertEquals("a b", InputText.clean("a\t\t \u00A0b"))
        assertEquals("a b", InputText.clean("a\u2028b"))
        assertEquals("a b", InputText.clean("a\u3000\u3000b"))
    }

    @Test
    fun `multi line keeps breaks, normalizes them and allows one blank line`() {
        assertEquals("a\nb", InputText.clean("a\r\nb", multiline = true))
        assertEquals("a\nb", InputText.clean("a\rb", multiline = true))
        assertEquals("a\n\nb", InputText.clean("a\n\n\n\n\nb", multiline = true))
        assertEquals("a\nb", InputText.clean("a   \nb", multiline = true))
    }

    @Test
    fun `normalize trims and clean does not`() {
        assertEquals("Rice ", InputText.clean("Rice "))
        assertEquals("Rice", InputText.normalize("  Rice \u200B"))
    }

    @Test
    fun `whitespace-only and invisible-only text is blank`() {
        assertTrue(InputText.isBlank(null))
        assertTrue(InputText.isBlank(""))
        assertTrue(InputText.isBlank("   \t\n"))
        assertTrue(InputText.isBlank("\u200B\u200B\uFEFF"))
        assertTrue(InputText.isBlank("\u00A0\u3000"))
        assertFalse(InputText.isBlank("\u200Da"))
    }

    @Test
    fun `typing filter keeps single spaces, caps length by code points and allows one past the limit for fields`() {
        assertEquals(" Rice ", InputText.forTyping("   Rice ", 10))
        assertEquals(" ", InputText.forTyping("     ", 10))
        assertEquals("aabc", InputText.forField("a".repeat(2) + "bcdef", 3))
        assertEquals(101, InputText.forField("a".repeat(500), FieldLimits.TITLE_MAX).length)
        assertEquals("abc", InputText.forTyping("abcdef", 3))
        val cart = "\uD83D\uDED2" // two UTF-16 units
        assertEquals(cart + cart, InputText.forTyping(cart + cart + cart, 2)) // never splits a surrogate pair
    }

    @Test
    fun `typing filter is idempotent`() {
        val samples = listOf(
            "  a\u202E b\n\n c ",
            "\u200B\u200Bx",
            "\u0C95\u0CCD\u200D\u0CB7  \u0CB7",
            "\uD83D\uDED2 \t \uD83D\uDED2",
        )
        for (s in samples) {
            val once = InputText.forTyping(s, 50)
            assertEquals(once, InputText.forTyping(once, 50))
        }
    }

    @Test
    fun `email filter removes every kind of whitespace`() {
        assertEquals("a@b.co", InputText.forEmail(" a @ b\u00A0.co\n"))
        assertEquals(254, InputText.forEmail("a".repeat(400)).length)
    }
}
