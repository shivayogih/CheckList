package com.dataloom.checklist.data.local.fts

import com.dataloom.checklist.data.local.entity.ItemSearchFtsEntity
import com.dataloom.checklist.data.local.entity.MasterItemTranslationEntity
import java.text.Normalizer
import java.util.Locale

/**
 * Builds and queries the item search index (section 6.6). Stored text and queries go through the
 * same [normalize], so "Akki", "AKKI" and full-width forms all meet in one spelling.
 */
object SearchText {

    private val whitespace = Regex("\\s+")

    /** NFKC, lowercase, trimmed, single spaces. */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .trim()
            .replace(whitespace, " ")

    /**
     * Turns user input into a safe FTS4 MATCH expression: every word becomes a prefix term
     * ("basmati ri" -> "basmati* ri*"), and FTS syntax characters are dropped so input can never
     * form an operator. Returns null when nothing searchable remains.
     */
    fun matchExpression(query: String): String? {
        val terms = normalize(query)
            .split(' ')
            .map { word -> word.filter(::isTokenChar) }
            .filter { it.isNotEmpty() }
        return if (terms.isEmpty()) null else terms.joinToString(" ") { "$it*" }
    }

    /** Letters, digits and combining marks: Indic vowel signs and viramas are marks, not separators. */
    private fun isTokenChar(char: Char): Boolean = when (char.category) {
        CharCategory.NON_SPACING_MARK, CharCategory.COMBINING_SPACING_MARK, CharCategory.ENCLOSING_MARK -> true
        else -> char.isLetterOrDigit()
    }

    /**
     * How well a matched row fits the query: [Rank.EXACT] beats [Rank.PREFIX] beats
     * [Rank.CONTAINS]. [matchText] holds one normalized phrase (name or alias) per line.
     */
    fun rank(matchText: String, normalizedQuery: String): Rank =
        matchText.split(LINE).minOfOrNull { phrase ->
            when {
                phrase == normalizedQuery -> Rank.EXACT
                phrase.startsWith(normalizedQuery) -> Rank.PREFIX
                phrase.contains(normalizedQuery) -> Rank.CONTAINS
                else -> Rank.OTHER
            }
        } ?: Rank.OTHER

    enum class Rank { EXACT, PREFIX, CONTAINS, OTHER }

    /**
     * Index rows for one master item: one per seeded translation (name + aliases), plus one for a
     * custom name, which is searchable whatever the current language.
     */
    fun rowsFor(
        masterItemId: String,
        categoryId: String,
        customName: String?,
        customNameLocale: String?,
        translations: List<MasterItemTranslationEntity>,
    ): List<ItemSearchFtsEntity> {
        val seeded = translations.map { translation ->
            ItemSearchFtsEntity(
                refType = ItemSearchFtsEntity.REF_MASTER,
                refId = masterItemId,
                categoryId = categoryId,
                locale = translation.locale,
                text = phrases(listOf(translation.name) + translation.aliasList),
            )
        }
        val custom = customName?.takeIf { it.isNotBlank() }?.let { name ->
            ItemSearchFtsEntity(
                refType = ItemSearchFtsEntity.REF_CUSTOM,
                refId = masterItemId,
                categoryId = categoryId,
                locale = customNameLocale.orEmpty(),
                text = phrases(listOf(name)),
            )
        }
        return seeded + listOfNotNull(custom)
    }

    private fun phrases(values: List<String>): String =
        values.map(::normalize).filter { it.isNotEmpty() }.distinct().joinToString(LINE)

    private const val LINE = "\n"
}
