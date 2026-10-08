package com.dataloom.checklist.ai.parser

import java.text.Normalizer
import java.util.Locale

/** A word as typed ([surface], kept for names and titles) and its matching form ([norm]). */
data class Token(val surface: String, val norm: String) {
    val isSeparator: Boolean get() = norm == SEPARATOR

    companion object {
        const val SEPARATOR = ","
        fun separator() = Token(SEPARATOR, SEPARATOR)
    }
}

/**
 * Normalizes and splits command text. Matching uses NFKC, lowercase, ASCII digits (every script's
 * digits, so "೫" and "५" read as 5) and no zero-width characters; the typed spelling is kept for
 * display. Commas, semicolons, dandas, "&" and "+" separate items; "2,5" between digits is a decimal.
 */
object TextNormalizer {

    private val vulgarFractions = mapOf('½' to ".5", '¼' to ".25", '¾' to ".75")
    private val decimalComma = Regex("(?<=\\d),(?=\\d)")
    private val separatorChars = setOf(',', ';', '、', '،', '।', '॥', '\n', '&', '+', '|')
    private val zeroWidth = Regex("[\\u200B-\\u200D\\uFEFF]")
    private val numberPrefix = Regex("^(\\d+(?:[./]\\d+)?|\\.\\d+)")

    /** Normal form of one word or phrase for matching. */
    fun normalize(text: String): String {
        val digits = buildString(text.length) {
            for (char in text) append(if (char.isDigit() && char !in '0'..'9') Character.forDigit(Character.digit(char, 10), 10) else char)
        }
        return Normalizer.normalize(digits, Normalizer.Form.NFKC)
            .replace(zeroWidth, "")
            .lowercase(Locale.ROOT)
            .trim()
    }

    /**
     * Splits [text] into tokens. A number glued to a known unit ("2kg", "500ಗ್ರಾಂ") becomes two tokens;
     * [isUnit] decides, so "7up" stays one word.
     */
    fun tokenize(text: String, isUnit: (String) -> Boolean = { false }): List<Token> {
        var prepared = text
        vulgarFractions.forEach { (fraction, decimal) -> prepared = prepared.replace(fraction.toString(), decimal) }
        prepared = prepared.replace(decimalComma, ".").replace('⁄', '/')
        val spaced = buildString(prepared.length + 8) {
            for (char in prepared) if (char in separatorChars) append(" , ") else append(char)
        }
        val tokens = mutableListOf<Token>()
        for (raw in spaced.split(Regex("\\s+"))) {
            if (raw == Token.SEPARATOR) {
                tokens += Token.separator()
                continue
            }
            val word = clean(raw)
            if (word.isEmpty()) continue
            tokens += splitNumberAndUnit(word, isUnit)
        }
        return tokens
    }

    /** Normalized single words of a phrase, the way [tokenize] would produce them. */
    fun words(phrase: String): List<String> = tokenize(phrase).filterNot { it.isSeparator }.map { it.norm }

    private fun splitNumberAndUnit(word: String, isUnit: (String) -> Boolean): List<Token> {
        val norm = normalize(word)
        val number = numberPrefix.find(norm)?.value
        if (number != null && number.length < norm.length) {
            // Digits map one-to-one to characters, so the same split point works on the typed text.
            val rest = clean(word.substring(number.length))
            if (rest.isNotEmpty() && isUnit(normalize(rest))) return listOf(Token(word.substring(0, number.length), number), Token(rest, normalize(rest)))
        }
        return listOf(Token(word, norm))
    }

    /** Strips punctuation around a word; a dot stays only before a digit (".5"). */
    private fun clean(raw: String): String {
        var word = raw.trim(::isTrimmable).trimEnd('.', '/')
        while (word.startsWith('.') && word.getOrNull(1)?.isDigit() != true) word = word.drop(1).trim(::isTrimmable)
        return word
    }

    /** Punctuation and symbols around a word ("rice.", "\"Diwali\""), but not a number's leading dot. */
    private fun isTrimmable(char: Char): Boolean = when (Character.getType(char).toByte()) {
        Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION,
        Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
        Character.MATH_SYMBOL, Character.OTHER_SYMBOL,
        -> true
        Character.OTHER_PUNCTUATION -> char != '.' && char != '/'
        else -> false
    }
}
