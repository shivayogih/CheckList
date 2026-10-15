package com.dataloom.checklist.ai.draft

import com.dataloom.checklist.ai.parser.TextNormalizer
import com.dataloom.checklist.domain.validation.FieldLimits
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads a free request ("Create checklist for my next Goa trip", "ದೀಪಾವಳಿ ಶಾಪಿಂಗ್") with keywords, not
 * a language model: it picks the bundled [DraftTemplate]s the words point to and makes a title from
 * the words that are left. Pure and synchronous, so it is tested without a catalog.
 *
 * Scoring: a keyword is 2 points, a hint 1, each counted once. The best template wins; one more is
 * merged in only when it also has a keyword of its own ("Diwali pooja" gets both, but the "shopping"
 * hint in "Diwali shopping" does not add groceries). Ties go to the template listed first.
 */
@Singleton
class DraftMatcher @Inject constructor(private val templates: DraftTemplates) {

    data class Match(val templates: List<DraftTemplate>, val title: String)

    private class Pattern(val words: List<String>)

    private class Compiled(val template: DraftTemplate, val keywords: List<Pattern>, val hints: List<Pattern>)

    private val compiled: List<Compiled> = templates.templates.map { template ->
        Compiled(template, patterns(template.keywords), patterns(template.hints))
    }

    private val fillers: Set<String> = templates.fillers.values.flatten().flatMap { TextNormalizer.words(it) }.toSet()

    /** The matching templates, best first, and a title; null when no template matches. */
    fun match(prompt: String, locale: String): Match? {
        val words = TextNormalizer.words(prepare(prompt))
        if (words.isEmpty()) return null
        val scored = compiled.mapNotNull { entry ->
            val strong = entry.keywords.count { it.matches(words) }
            val score = strong * KEYWORD_POINTS + entry.hints.count { it.matches(words) }
            if (score == 0) null else Triple(entry.template, score, strong > 0)
        }.sortedByDescending { it.second }
        val best = scored.firstOrNull() ?: return null
        val extra = scored.drop(1).filter { it.third }.take(MAX_TEMPLATES - 1)
        val chosen = listOf(best.first) + extra.map { it.first }
        return Match(chosen, title(prompt) ?: localized(best.first.title, locale))
    }

    /**
     * The request without command words, first letter capitalized: "Create check list for my next Goa
     * trip" -> "Goa trip". Null when nothing with a letter is left.
     */
    fun title(prompt: String): String? {
        val kept = TextNormalizer.tokenize(prepare(prompt))
            .filterNot { it.isSeparator || it.norm in fillers }
            .map { it.surface }
        val text = kept.joinToString(" ").trim()
        if (text.none { it.isLetter() }) return null
        val capitalized = text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        return capitalized.truncateCodePoints(FieldLimits.TITLE_MAX)
    }

    private fun prepare(prompt: String): String = prompt.replace(CHECK_LIST, "checklist")

    private fun Pattern.matches(prompt: List<String>): Boolean {
        if (words.isEmpty() || words.size > prompt.size) return false
        return (0..prompt.size - words.size).any { start ->
            words.indices.all { wordMatches(prompt[start + it], words[it]) }
        }
    }

    private fun wordMatches(word: String, keyword: String): Boolean =
        word == keyword || (keyword.codePointCount(0, keyword.length) >= MIN_PREFIX && word.startsWith(keyword))

    private fun patterns(byLanguage: Map<String, List<String>>): List<Pattern> =
        byLanguage.values.flatten().map { Pattern(TextNormalizer.words(it)) }.filter { it.words.isNotEmpty() }

    companion object {
        const val KEYWORD_POINTS = 2

        /** At most this many templates are merged into one draft. */
        const val MAX_TEMPLATES = 2

        /** A keyword this long also matches inflected words it starts ("ಪ್ರವಾಸಕ್ಕೆ", "trips"). */
        const val MIN_PREFIX = 3

        private val CHECK_LIST = Regex("(?i)\\bcheck[\\s-]+list\\b")

        fun localized(names: Map<String, String>, locale: String): String {
            val language = locale.substringBefore('-').substringBefore('_').lowercase(Locale.ROOT)
            return names[language] ?: names.getValue("en")
        }

        private fun String.truncateCodePoints(max: Int): String =
            if (codePointCount(0, length) <= max) this else substring(0, offsetByCodePoints(0, max)).trim()
    }
}
