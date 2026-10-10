package com.dataloom.checklist.domain.phone

import com.dataloom.checklist.domain.validation.PhoneEntry
import com.dataloom.checklist.domain.validation.PhoneNumberInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneCountriesTest {

    @Test
    fun `every country has a unique iso code, a dial code and sane lengths`() {
        val all = PhoneCountries.all
        assertTrue(all.size > 200)
        assertEquals(all.size, all.map { it.iso }.toSet().size)
        all.forEach {
            assertTrue(it.iso, it.iso.matches(Regex("[A-Z]{2}")))
            assertTrue(it.iso, it.dialCode in 1..999)
            assertTrue(it.iso, it.nationalDigits.first in 1..it.nationalDigits.last && it.nationalDigits.last <= 15)
        }
    }

    @Test
    fun `india is the default with ten digit numbers`() {
        val india = PhoneCountries.default
        assertEquals("IN", india.iso)
        assertEquals("+91", india.dialText)
        assertEquals(10..10, india.nationalDigits)
        assertEquals("🇮🇳", india.flag)
    }

    @Test
    fun `shared dial codes resolve to their main country`() {
        assertEquals("US", PhoneCountries.splitDialCode("12025550123")?.first?.iso)
        assertEquals("RU", PhoneCountries.splitDialCode("79123456789")?.first?.iso)
        assertEquals("GB", PhoneCountries.splitDialCode("447911123456")?.first?.iso)
        assertEquals("AE", PhoneCountries.splitDialCode("971501234567")?.first?.iso)
        assertNull(PhoneCountries.splitDialCode("999"))
    }

    @Test
    fun `unknown codes fall back to india`() {
        assertEquals("IN", PhoneCountries.orDefault("ZZ").iso)
        assertEquals("GB", PhoneCountries.orDefault("gb").iso)
    }
}

class PhoneNumberInputTest {

    @Test
    fun `only digits get in, typed or pasted`() {
        assertEquals(PhoneEntry("IN", "9845012345"), PhoneNumberInput.typed("98450 12345", "IN"))
        assertEquals(PhoneEntry("IN", "98450"), PhoneNumberInput.typed("98a4#5*0-", "IN"))
        assertEquals(PhoneEntry("IN", "987"), PhoneNumberInput.typed("९८७", "IN"))
        assertEquals(PhoneEntry("IN", ""), PhoneNumberInput.typed("abc", "IN"))
    }

    @Test
    fun `the length is capped per country with room for a trunk zero`() {
        assertEquals("09845012345", PhoneNumberInput.typed("098450123456", "IN").digits)
        assertEquals(11, PhoneNumberInput.typed("9".repeat(30), "IN").digits.length)
        assertEquals(8, PhoneNumberInput.typed("9".repeat(30), "SG").digits.length)
    }

    @Test
    fun `a pasted international number switches the country`() {
        assertEquals(PhoneEntry("GB", "7911123456"), PhoneNumberInput.typed("+44 7911 123456", "IN"))
        assertEquals(PhoneEntry("IN", "9845012345"), PhoneNumberInput.typed(" +91 98450-12345", "US"))
    }

    @Test
    fun `changing the country keeps the digits that still fit`() {
        assertEquals(PhoneEntry("SG", "98450123"), PhoneNumberInput.forCountry("9845012345", "SG"))
        assertEquals(PhoneEntry("GB", "9845012345"), PhoneNumberInput.forCountry("9845012345", "GB"))
    }
}
