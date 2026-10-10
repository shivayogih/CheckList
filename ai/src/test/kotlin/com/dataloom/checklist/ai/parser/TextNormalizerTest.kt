package com.dataloom.checklist.ai.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizerTest {

    private fun words(text: String, units: Set<String> = setOf("kg", "g", "ml", "ಕೆಜಿ")) =
        TextNormalizer.tokenize(text) { it in units }.map { it.norm }

    @Test
    fun `digits of every Indian script read as ASCII`() {
        val five = mapOf("kn" to "೫", "hi/mr" to "५", "ta" to "௫", "te" to "౫", "ml" to "൫")
        five.forEach { (script, digit) -> assertEquals(script, "5", TextNormalizer.normalize(digit)) }
        assertEquals("12", TextNormalizer.normalize("१२"))
    }

    @Test
    fun `separators, decimal commas and fractions`() {
        assertEquals(listOf("rice", ",", "milk", ",", "dal", ",", "salt"), words("rice,milk; dal & salt"))
        assertEquals(listOf("2.5", "kg", "rice"), words("2,5 kg rice"))
        assertEquals(listOf("1.5", "kg"), words("1½ kg"))
        assertEquals(listOf(".5", "kg"), words("½ kg"))
        assertEquals(listOf("1/2", "kg"), words("1⁄2 kg"))
        assertEquals(listOf("दूध", ",", "चावल"), words("दूध। चावल"))
    }

    @Test
    fun `numbers glued to units are split only for real units`() {
        assertEquals(listOf("2", "kg", "rice"), words("2kg rice"))
        assertEquals(listOf("500", "g"), words("500g"))
        assertEquals(listOf("2", "ಕೆಜಿ", "ಅಕ್ಕಿ"), words("2ಕೆಜಿ ಅಕ್ಕಿ"))
        assertEquals(listOf("7up"), words("7up"))
    }

    @Test
    fun `punctuation around words is dropped, the typed spelling is kept`() {
        val tokens = TextNormalizer.tokenize("\"Diwali\" Shopping. (Rice)!")
        assertEquals(listOf("Diwali", "Shopping", "Rice"), tokens.map { it.surface })
        assertEquals(listOf("diwali", "shopping", "rice"), tokens.map { it.norm })
    }

    @Test
    fun `zero-width joiners and case do not matter for matching`() {
        assertEquals(TextNormalizer.normalize("ಕೆಜಿ"), TextNormalizer.normalize("ಕೆ‍ಜಿ"))
        assertEquals("rice", TextNormalizer.normalize("RICE"))
        assertEquals(TextNormalizer.normalize("प्याज़"), TextNormalizer.normalize("प्याज़"))
    }
}
