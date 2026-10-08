package com.dataloom.checklist.domain.validation

import com.dataloom.checklist.domain.model.Quantity
import java.math.BigDecimal
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuantityInputTest {

    private fun milli(text: String): Long? = (QuantityInput.parse(text) as? QuantityParse.Valid)?.quantity?.milli

    private fun error(text: String): ValidationError? = (QuantityInput.parse(text) as? QuantityParse.Invalid)?.error

    private fun randomText(random: Random, alphabet: String, maxLength: Int): String = buildString {
        repeat(random.nextInt(0, maxLength)) { append(alphabet[random.nextInt(alphabet.length)]) }
    }

    @Test
    fun `empty and blank input is no quantity`() {
        assertEquals(QuantityParse.Empty, QuantityInput.parse(""))
        assertEquals(QuantityParse.Empty, QuantityInput.parse("   "))
        assertEquals(QuantityParse.Empty, QuantityInput.parse("\u200B"))
    }

    @Test
    fun `plain numbers parse to exact milli units`() {
        assertEquals(12_000L, milli("12"))
        assertEquals(1_500L, milli("1.5"))
        assertEquals(1_500L, milli("1,5"))
        assertEquals(1_500L, milli("1\u066B5"))
        assertEquals(500L, milli(".5"))
        assertEquals(5_000L, milli("5."))
        assertEquals(1L, milli("0.001"))
        assertEquals(99_999_000L, milli("99999"))
        assertEquals(1_000L, milli("00001"))
        assertEquals(2_500L, milli("  2,5  "))
    }

    @Test
    fun `digits of other scripts are read as numbers`() {
        assertEquals(12_000L, milli("\u0967\u0968")) // Devanagari 12
        assertEquals(3_000L, milli("\u0663")) // Arabic-Indic 3
        assertEquals(12_500L, milli("\u0C67\u0C68.\u0C6B")) // Telugu 12.5
        assertEquals(12_000L, milli("\uFF11\uFF12")) // fullwidth
        assertEquals(1_250L, milli("\u0CE7,\u0CE8\u0CEB")) // Kannada 1,25
    }

    @Test
    fun `letters, signs, exponents and symbols are not numbers`() {
        val bad = listOf(
            "12a", "a12", "abc", "-3", "+3", "1e5", "1E5", "1.2.3", "1,2,3", "1.2,3", ".", ",", "-", "1 2",
            "1_000", "\u00BD", "\u00B2", "1/2", "NaN", "Infinity", "0x10", "1%", "\u20B9", "5kg", "\u22123",
        )
        for (text in bad) {
            assertEquals("'$text'", ValidationError.QUANTITY_NOT_A_NUMBER, error(text))
        }
    }

    @Test
    fun `zero is not positive`() {
        for (text in listOf("0", "0.0", ".0", "0,000", "000")) {
            assertEquals("'$text'", ValidationError.QUANTITY_NOT_POSITIVE, error(text))
        }
    }

    @Test
    fun `more than three decimals is too precise unless the rest are zeros`() {
        assertEquals(ValidationError.QUANTITY_TOO_PRECISE, error("0.0001"))
        assertEquals(ValidationError.QUANTITY_TOO_PRECISE, error("1.2345"))
        assertEquals(1_500L, milli("1.50000"))
    }

    @Test
    fun `values above the maximum and very long digit strings are out of range`() {
        assertEquals(ValidationError.QUANTITY_OUT_OF_RANGE, error("100000"))
        assertEquals(ValidationError.QUANTITY_OUT_OF_RANGE, error("99999.001"))
        assertEquals(ValidationError.QUANTITY_OUT_OF_RANGE, error("9".repeat(30)))
        assertEquals(ValidationError.QUANTITY_OUT_OF_RANGE, error("9".repeat(5000)))
        assertEquals(ValidationError.QUANTITY_OUT_OF_RANGE, error("9".repeat(5000) + ".5"))
        assertEquals(ValidationError.QUANTITY_OUT_OF_RANGE, error(Long.MAX_VALUE.toString()))
        assertEquals(ValidationError.QUANTITY_OUT_OF_RANGE, error("99999999999999999999999999"))
        assertEquals(ValidationError.QUANTITY_NOT_A_NUMBER, error("9".repeat(5000) + "x"))
    }

    @Test
    fun `Quantity parse agrees with QuantityInput`() {
        assertNull(Quantity.parse("1e5"))
        assertNull(Quantity.parse("\u22123"))
        assertEquals(12_000L, Quantity.parse("\u0967\u0968")?.milli)
        assertEquals(2_500L, Quantity.parse("2,5")?.milli)
    }

    @Test
    fun `sanitize drops letters and symbols and keeps one separator`() {
        assertEquals("12", QuantityInput.sanitize("12a"))
        assertEquals("15", QuantityInput.sanitize("1e5"))
        assertEquals("3", QuantityInput.sanitize("-3"))
        assertEquals("1.5", QuantityInput.sanitize("1,5"))
        assertEquals("1.23", QuantityInput.sanitize("1.2.3"))
        assertEquals("12", QuantityInput.sanitize("\u0967\u0968"))
        assertEquals("3", QuantityInput.sanitize("\u0663"))
        assertEquals("", QuantityInput.sanitize("abc!@# "))
        assertEquals(".", QuantityInput.sanitize("."))
        assertEquals("5.", QuantityInput.sanitize("5,"))
    }

    @Test
    fun `sanitize caps integer digits at five and decimals at three`() {
        assertEquals("99999", QuantityInput.sanitize("999999999"))
        assertEquals("1.234", QuantityInput.sanitize("1.23456789"))
        assertEquals(5 + 1 + 3, QuantityInput.sanitize("9".repeat(5000) + "." + "9".repeat(5000)).length)
    }

    @Test
    fun `sanitized text only ever has range or zero problems`() {
        val alphabet = "0123456789.,-+eE abc\u0967\u0663\u066B!@#\u200B\n\uFF11\u00BD"
        val random = Random(280)
        repeat(5_000) {
            val raw = randomText(random, alphabet, 24)
            val clean = QuantityInput.sanitize(raw)
            assertEquals("idempotent for '$raw'", clean, QuantityInput.sanitize(clean))
            assertTrue(
                "only digits and one dot in '$clean' from '$raw'",
                clean.all { it in '0'..'9' || it == '.' } && clean.count { it == '.' } <= 1,
            )
            val parsed = QuantityInput.parse(clean)
            if (parsed is QuantityParse.Invalid) {
                assertTrue(
                    "'$clean' from '$raw' gave ${parsed.error}",
                    parsed.error == ValidationError.QUANTITY_NOT_POSITIVE ||
                        parsed.error == ValidationError.QUANTITY_OUT_OF_RANGE ||
                        // a lone "." has no digit
                        (clean == "." && parsed.error == ValidationError.QUANTITY_NOT_A_NUMBER),
                )
            }
        }
    }

    @Test
    fun `parse never throws and every valid result is exact, positive and within range`() {
        val alphabet = "0123456789.,-+eE aZ\u0967\u0663\u066B!\u200B\n\u00BD\u2212"
        val random = Random(281)
        repeat(20_000) {
            val raw = randomText(random, alphabet, 80)
            val parsed = QuantityInput.parse(raw)
            if (parsed is QuantityParse.Valid) {
                val q = parsed.quantity
                assertTrue(q.milli > 0)
                assertTrue(q.amount <= Quantity.MAX)
                assertEquals(q, Quantity.fromMilli(q.milli))
                assertEquals(0, BigDecimal(q.toPlainString()).compareTo(BigDecimal.valueOf(q.milli, 3)))
            }
        }
    }

    @Test
    fun `round trip of every plain representation`() {
        val random = Random(282)
        repeat(2_000) {
            val milli = random.nextLong(1, 99_999_001)
            val q = Quantity.fromMilli(milli)
            assertEquals(milli, QuantityInput.parseOrNull(q.toPlainString())?.milli)
        }
    }

    @Test
    fun `quantity validator keeps rejecting oversized imported values`() {
        val tooBig = Quantity.fromMilli(100_000_000L)
        assertEquals(listOf(ValidationError.QUANTITY_OUT_OF_RANGE), QuantityValidator.validate(tooBig, null))
    }
}

class PhoneInputTest {

    @Test
    fun `letters and symbols are dropped, digits of any script become ASCII`() {
        assertEquals("98765 43210", PhoneInput.sanitize("98765 43210"))
        assertEquals("+919876543210", PhoneInput.sanitize("+91ab9876543210"))
        assertEquals("987", PhoneInput.sanitize("\u096F\u096E\u096D"))
        assertEquals("9876", PhoneInput.sanitize("98a7#6*"))
        assertEquals("(080) 123-4567", PhoneInput.sanitize("(080) 123-4567"))
    }

    @Test
    fun `plus is only allowed first and spaces cannot double`() {
        assertEquals("+91", PhoneInput.sanitize("+9+1"))
        assertEquals("91", PhoneInput.sanitize("9+1"))
        assertEquals("", PhoneInput.sanitize(" -).")) // only "(" may lead
        assertEquals("(1", PhoneInput.sanitize("(1"))
        assertEquals("9 1", PhoneInput.sanitize("9   1"))
    }

    @Test
    fun `length is capped`() {
        assertEquals(FieldLimits.PHONE_MAX, PhoneInput.sanitize("9".repeat(100)).length)
    }

    @Test
    fun `sanitize is idempotent and its output uses only allowed characters`() {
        val alphabet = "0123456789+-(). abc#*\u0967"
        val random = Random(283)
        repeat(3_000) {
            val raw = List(random.nextInt(0, 40)) { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
            val clean = PhoneInput.sanitize(raw)
            assertEquals(clean, PhoneInput.sanitize(clean))
            assertTrue(clean.all { it in '0'..'9' || it in "+-(). " })
        }
    }
}
