package com.dataloom.checklist.presentation.common

import com.dataloom.checklist.domain.localization.SupportedLanguages
import com.dataloom.checklist.domain.model.Quantity
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Numbers follow the app language but always use Western digits (A-03, CL-142). */
class LocaleNumbersTest {

    private val westernOnly = Regex("[0-9.,   ]+")

    @Test
    fun `every supported language formats counts and amounts with Western digits`() {
        SupportedLanguages.all.forEach { language ->
            val locale = Locale.forLanguageTag(language.tag)
            listOf(
                LocaleNumbers.formatCount(0, locale),
                LocaleNumbers.formatCount(1234, locale),
                LocaleNumbers.formatQuantity(Quantity.parse("2.5")!!, locale),
                LocaleNumbers.formatQuantity(Quantity.parse("99999")!!, locale),
            ).forEach { text ->
                assertTrue("${language.tag}: '$text' has non-Western digits", westernOnly.matches(text))
            }
        }
    }

    @Test
    fun `Marathi, whose default digits are Devanagari, still gets Western digits`() {
        assertEquals("12", LocaleNumbers.formatCount(12, Locale.forLanguageTag("mr")))
    }

    @Test
    fun `amounts keep up to three decimals and drop trailing zeros`() {
        val english = Locale.ENGLISH
        assertEquals("2.5", LocaleNumbers.formatQuantity(Quantity.parse("2.50")!!, english))
        assertEquals("0.125", LocaleNumbers.formatQuantity(Quantity.parse("0.125")!!, english))
        assertEquals("5", LocaleNumbers.formatQuantity(Quantity.parse("5")!!, english))
    }

    @Test
    fun `integer arguments are formatted, other arguments pass through`() {
        val args = LocaleNumbers.formatArgs(listOf(1500, "Rice", 7L), Locale.ENGLISH)
        assertEquals(listOf("1,500", "Rice", "7"), args.toList())
    }
}
