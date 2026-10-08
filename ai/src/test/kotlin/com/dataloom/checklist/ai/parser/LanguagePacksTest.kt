package com.dataloom.checklist.ai.parser

import com.dataloom.checklist.ai.fixtures.bundledPacks
import com.dataloom.checklist.domain.localization.SupportedLanguages
import com.dataloom.checklist.domain.model.BuiltInUnits
import com.dataloom.checklist.domain.model.Quantity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The language data files are code too: these rules keep a typo from silently breaking a language. */
class LanguagePacksTest {

    private val packs = SupportedLanguages.all.map { bundledPacks[it.tag] ?: error("No pack for ${it.tag}") }

    @Test
    fun `every supported language has a pack named after it`() {
        assertEquals(SupportedLanguages.all.map { it.tag }.toSet(), bundledPacks.packs.keys)
        packs.forEach { pack -> assertEquals(pack.language, SupportedLanguages.fromTag(pack.language)?.tag) }
    }

    @Test
    fun `scripts are real Unicode script names`() {
        packs.flatMap { it.scripts }.forEach { Character.UnicodeScript.valueOf(it) }
    }

    @Test
    fun `unit keys are built-in unit codes`() {
        val codes = BuiltInUnits.all.map { it.code.value }.toSet()
        packs.forEach { pack -> pack.units.keys.forEach { assertTrue("${pack.language}: unknown unit $it", it in codes) } }
    }

    @Test
    fun `number words are valid quantities`() {
        packs.forEach { pack ->
            pack.numbers.forEach { (word, value) -> assertNotNull("${pack.language}: $word=$value", Quantity.parse(value)) }
        }
    }

    @Test
    fun `single-word entries stay single words after normalization`() {
        packs.forEach { pack ->
            val singles = pack.numbers.keys + pack.units.values.flatten() + pack.separators + pack.fillers + pack.articles + pack.titleConnectors
            singles.forEach { assertEquals("${pack.language}: '$it' must be one word", 1, TextNormalizer.words(it).size) }
        }
    }

    @Test
    fun `no spelling means two different things inside one language`() {
        packs.forEach { pack ->
            val unitWords = pack.units.flatMap { (code, words) -> words.map { TextNormalizer.normalize(it) to code } }
            val clashes = unitWords.groupBy({ it.first }, { it.second }).filterValues { it.distinct().size > 1 }
            assertTrue("${pack.language}: unit spellings with two codes $clashes", clashes.isEmpty())
            val numbers = pack.numbers.keys.map(TextNormalizer::normalize).toSet()
            val overlap = numbers.intersect(unitWords.map { it.first }.toSet()) + numbers.intersect(pack.fillers.map(TextNormalizer::normalize).toSet())
            assertTrue("${pack.language}: words that are numbers and units or fillers $overlap", overlap.isEmpty())
        }
    }

    @Test
    fun `every language can add, tick, untick, remove, change and create`() {
        packs.forEach { pack ->
            CommandIntent.entries.filterNot { it == CommandIntent.ADD }.forEach { intent ->
                val markers = pack.intents[intent]
                assertTrue("${pack.language}: no words for $intent", markers != null && (markers.prefixes + markers.suffixes).isNotEmpty())
            }
        }
    }

    @Test
    fun `every language has a kilogram spelling in its own script or transliteration`() {
        packs.forEach { pack -> assertTrue(pack.language, pack.units["KG"].orEmpty().isNotEmpty()) }
    }
}
