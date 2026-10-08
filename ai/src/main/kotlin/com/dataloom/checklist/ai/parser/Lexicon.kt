package com.dataloom.checklist.ai.parser

import com.dataloom.checklist.domain.model.UnitCode
import java.math.BigDecimal
import java.math.RoundingMode

/** An intent phrase as normalized words. */
data class Marker(val intent: CommandIntent, val words: List<String>)

/**
 * The merged vocabulary of the languages relevant to one command, in priority order: when two
 * languages spell a word differently, the first language's meaning wins. Built per command because the
 * languages depend on the user's locale and the scripts in the text.
 */
class Lexicon(packs: List<LanguagePack>) {

    private val numbers = LinkedHashMap<String, BigDecimal>()
    private val units = LinkedHashMap<String, UnitCode>()
    val articles: Set<String>
    val separators: Set<String>
    val fillers: Set<String>
    val nameSuffixes: List<String>
    val andSuffixes: List<String>
    val stemRepairs: Map<String, String>
    val titleConnectors: Set<String>
    val prefixes: List<Marker>
    val suffixes: List<Marker>

    init {
        val articles = linkedSetOf<String>()
        val separators = linkedSetOf<String>()
        val fillers = linkedSetOf<String>()
        val nameSuffixes = linkedSetOf<String>()
        val andSuffixes = linkedSetOf<String>()
        val stemRepairs = LinkedHashMap<String, String>()
        val connectors = linkedSetOf<String>()
        val prefixes = mutableListOf<Marker>()
        val suffixes = mutableListOf<Marker>()
        for (pack in packs) {
            pack.numbers.forEach { (word, value) -> numbers.putIfAbsent(TextNormalizer.normalize(word), BigDecimal(value)) }
            pack.units.forEach { (code, spellings) ->
                spellings.forEach { units.putIfAbsent(TextNormalizer.normalize(it), UnitCode(code)) }
            }
            pack.articles.mapTo(articles, TextNormalizer::normalize)
            pack.separators.mapTo(separators, TextNormalizer::normalize)
            pack.fillers.mapTo(fillers, TextNormalizer::normalize)
            pack.nameSuffixes.mapTo(nameSuffixes, TextNormalizer::normalize)
            pack.andSuffixes.mapTo(andSuffixes, TextNormalizer::normalize)
            pack.stemRepairs.forEach { (from, to) -> stemRepairs.putIfAbsent(TextNormalizer.normalize(from), TextNormalizer.normalize(to)) }
            pack.titleConnectors.mapTo(connectors, TextNormalizer::normalize)
            pack.intents.forEach { (intent, markers) ->
                markers.prefixes.mapTo(prefixes) { Marker(intent, TextNormalizer.words(it)) }
                markers.suffixes.mapTo(suffixes) { Marker(intent, TextNormalizer.words(it)) }
            }
        }
        this.articles = articles
        this.separators = separators
        this.fillers = fillers
        // Longest ending first, so "ಯನ್ನು" is tried before "ನ್ನು".
        this.nameSuffixes = nameSuffixes.sortedByDescending { it.length }
        this.andSuffixes = andSuffixes.sortedByDescending { it.length }
        this.stemRepairs = stemRepairs
        this.titleConnectors = connectors
        this.prefixes = prefixes.filter { it.words.isNotEmpty() }.distinct()
        this.suffixes = suffixes.filter { it.words.isNotEmpty() }.distinct()
    }

    fun unit(norm: String): UnitCode? = units[norm]

    /** "2", "2.5", ".5", "1/2" or a number word; null for anything else. */
    fun number(norm: String): BigDecimal? {
        NUMERIC.matchEntire(norm)?.let { return BigDecimal(it.value) }
        FRACTION.matchEntire(norm)?.let { match ->
            val denominator = match.groupValues[2].toBigDecimal()
            if (denominator.signum() == 0) return null
            return match.groupValues[1].toBigDecimal().divide(denominator, FRACTION_SCALE, RoundingMode.HALF_UP).stripTrailingZeros()
        }
        return numbers[norm]
    }

    private companion object {
        val NUMERIC = Regex("\\d+(?:\\.\\d+)?|\\.\\d+")
        val FRACTION = Regex("(\\d+)/(\\d+)")
        const val FRACTION_SCALE = 3
    }
}
