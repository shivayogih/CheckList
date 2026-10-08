package com.dataloom.checklist.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuantityTest {

    @Test
    fun `parses whole and decimal amounts exactly`() {
        assertEquals(5000L, Quantity.parse("5")?.milli)
        assertEquals(2500L, Quantity.parse("2.5")?.milli)
        assertEquals(125L, Quantity.parse("0.125")?.milli)
    }

    @Test
    fun `accepts comma as decimal separator and trims spaces`() {
        assertEquals(2500L, Quantity.parse(" 2,5 ")?.milli)
    }

    @Test
    fun `rejects zero, negative, too large, too precise and non-numbers`() {
        assertNull(Quantity.parse("0"))
        assertNull(Quantity.parse("-1"))
        assertNull(Quantity.parse("100000"))
        assertNull(Quantity.parse("0.0001"))
        assertNull(Quantity.parse("abc"))
        assertNull(Quantity.parse(""))
    }

    @Test
    fun `trailing zeros beyond three places are allowed`() {
        assertEquals(1500L, Quantity.parse("1.50000")?.milli)
    }

    @Test
    fun `formats without trailing zeros or exponent`() {
        assertEquals("5", Quantity.of(5).toPlainString())
        assertEquals("2.5", Quantity.fromMilli(2500).toPlainString())
        assertEquals("10", Quantity.parse("10.000")!!.toPlainString())
    }

    @Test
    fun `whole detection`() {
        assertTrue(Quantity.of(3).isWhole)
        assertFalse(Quantity.parse("2.5")!!.isWhole)
    }

    @Test
    fun `round trips through milli`() {
        val q = Quantity.parse("12.345")!!
        assertEquals(q, Quantity.fromMilli(q.milli))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `stored value must be positive`() {
        Quantity.fromMilli(0)
    }
}
