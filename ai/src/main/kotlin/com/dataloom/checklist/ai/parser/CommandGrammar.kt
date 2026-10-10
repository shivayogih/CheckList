package com.dataloom.checklist.ai.parser

import com.dataloom.checklist.domain.localization.SupportedLanguages
import com.dataloom.checklist.domain.model.UnitCode
import java.math.BigDecimal
import javax.inject.Inject

/** One item phrase: "2 kg rice" -> name "rice", 2, KG. [name] keeps the typed spelling. */
data class ParsedItem(
    val name: String,
    val quantity: BigDecimal?,
    val unit: UnitCode?,
    /** The words this item came from, for "could not understand ..." messages. */
    val text: String,
)

data class ParsedCommand(
    val intent: CommandIntent,
    val items: List<ParsedItem>,
    /** Title of a new checklist ([CommandIntent.CREATE_CHECKLIST] only). */
    val title: String?,
    /** Phrases with no item name, e.g. a lone "2 kg". */
    val unparsed: List<String>,
    /** Languages used, most preferred first; item lookup searches in this order. */
    val languages: List<String>,
    /** Endings to try off a name that is not in the catalog as typed. */
    val nameVariants: (String) -> List<String>,
)

/**
 * Rule-based reading of a short command in any of the 7 languages, in native script or Latin
 * transliteration. Pure and synchronous: it finds the intent, the item phrases, and each phrase's
 * quantity and unit; [OfflineCommandParser] then looks the names up in the catalog.
 *
 * Steps: pick languages (the user's, those whose script appears, English, and for Latin text every
 * language's transliterations); tokenize; find intent words at the start or end (English verbs lead,
 * Indian-language verbs trail); split the rest into items at commas, "and" words, "and" endings, and
 * where a new quantity starts; read number + unit in either order ("2 kg rice", "rice 2 kg").
 */
class CommandGrammar @Inject constructor(private val packs: LanguagePacks) {

    fun parse(text: String, locale: String): ParsedCommand {
        val languages = languagesFor(text, locale)
        val lexicon = Lexicon(languages.mapNotNull { packs[it] })
        val tokens = TextNormalizer.tokenize(text) { lexicon.unit(it) != null }.trimSeparators()
        val (intent, payload) = detectIntent(tokens, lexicon)
        val variants = { name: String -> nameVariants(name, lexicon) }
        if (intent == CommandIntent.CREATE_CHECKLIST) {
            return ParsedCommand(intent, emptyList(), title(payload, lexicon), emptyList(), languages, variants)
        }
        val items = mutableListOf<ParsedItem>()
        val unparsed = mutableListOf<String>()
        for (run in runs(payload, lexicon)) {
            for (group in groups(classify(run, lexicon))) {
                val item = group.toItem()
                if (item != null) items += item else unparsed += group.text()
            }
        }
        return ParsedCommand(intent, items, null, unparsed, languages, variants)
    }

    /**
     * The user's language, languages whose script appears in [text], English, and for Latin text all
     * other languages (people type "2 kilo akki" with an English keyboard whatever the app language).
     */
    fun languagesFor(text: String, locale: String): List<String> {
        val scripts = text.codePoints().toArray()
            .filter { Character.isLetter(it) }
            .map { Character.UnicodeScript.of(it).name }
            .toSet()
        val primary = SupportedLanguages.fromTag(locale)?.tag ?: ENGLISH
        val all = SupportedLanguages.all.map { it.tag }.filter { packs[it] != null }
        val byScript = all.filter { tag -> packs[tag]!!.scripts.any { it in scripts } }
        val latin = LATIN in scripts || scripts.isEmpty()
        return (listOf(primary) + byScript + ENGLISH + if (latin) all else emptyList())
            .distinct()
            .filter { packs[it] != null }
    }

    private fun List<Token>.trimSeparators(): List<Token> = dropWhile { it.isSeparator }.dropLastWhile { it.isSeparator }

    /**
     * Each intent scores the length of its longest matching start phrase plus its longest matching end
     * phrase ("mark rice as not done": UNCOMPLETE 1 + 3 beats COMPLETE 1 + 1). No match means ADD.
     */
    private fun detectIntent(tokens: List<Token>, lexicon: Lexicon): Pair<CommandIntent, List<Token>> {
        val words = tokens.map { it.norm }
        var best = CommandIntent.ADD
        var bestPrefix = 0
        var bestSuffix = 0
        for (intent in CommandIntent.entries) {
            val prefix = lexicon.prefixes.filter { it.intent == intent && words.startsWith(it.words) }.maxOfOrNull { it.words.size } ?: 0
            val suffix = lexicon.suffixes.filter { it.intent == intent && words.endsWith(it.words) }.maxOfOrNull { it.words.size } ?: 0
            val (p, s) = if (prefix + suffix > words.size) (if (prefix >= suffix) prefix to 0 else 0 to suffix) else prefix to suffix
            if (p + s > bestPrefix + bestSuffix) {
                best = intent
                bestPrefix = p
                bestSuffix = s
            }
        }
        return best to tokens.subList(bestPrefix, tokens.size - bestSuffix).trimSeparators()
    }

    private fun title(payload: List<Token>, lexicon: Lexicon): String? {
        val words = payload.filterNot { it.isSeparator }
            .dropWhile { it.norm in lexicon.titleConnectors }
            .dropLastWhile { it.norm in lexicon.titleConnectors }
        return words.joinToString(" ") { it.surface }.takeIf { it.isNotBlank() }
    }

    /** Splits at separators, "and" words, and words with an "and" ending (the ending is removed). */
    private fun runs(payload: List<Token>, lexicon: Lexicon): List<List<Token>> {
        val runs = mutableListOf<List<Token>>()
        var current = mutableListOf<Token>()
        payload.forEachIndexed { index, token ->
            if (token.isSeparator || token.norm in lexicon.separators) {
                if (current.isNotEmpty()) runs += current
                current = mutableListOf()
                return@forEachIndexed
            }
            val ending = lexicon.andSuffixes.firstOrNull { token.norm.endsWith(it) && token.norm.length > it.length + 1 }
            val isLast = index == payload.lastIndex || payload[index + 1].isSeparator
            if (ending != null && !isLast && lexicon.number(token.norm) == null) {
                val stem = token.surface.dropLast(ending.length)
                current += Token(stem, TextNormalizer.normalize(stem))
                runs += current
                current = mutableListOf()
            } else {
                current += token
            }
        }
        if (current.isNotEmpty()) runs += current
        return runs
    }

    private sealed interface Element {
        data class Qty(val value: BigDecimal, val unit: UnitCode?, val words: List<Token>) : Element

        data class Name(val token: Token) : Element
    }

    /**
     * Finds quantity phrases in one run: number (+ article) + unit, article + unit ("a dozen"), or a
     * long unit word alone at either end ("dozen eggs" = 1 dozen). Short unit spellings ("g", "m")
     * count only right after a number, so they never eat a word of a name.
     */
    private fun classify(run: List<Token>, lexicon: Lexicon): List<Element> {
        val tokens = run.filterNot { it.norm in lexicon.fillers }
        val elements = mutableListOf<Element>()
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            val number = lexicon.number(token.norm)
            val unitAt = { index: Int -> tokens.getOrNull(index)?.let { lexicon.unit(it.norm) } }
            val isArticle = { index: Int -> tokens.getOrNull(index)?.norm in lexicon.articles }
            when {
                number != null -> {
                    val skip = if (isArticle(i + 1) && unitAt(i + 2) != null) 1 else 0
                    val unit = unitAt(i + 1 + skip)
                    val used = if (unit != null) 2 + skip else 1
                    elements += Element.Qty(number, unit, tokens.subList(i, i + used))
                    i += used
                }
                isArticle(i) && unitAt(i + 1) != null -> {
                    elements += Element.Qty(BigDecimal.ONE, unitAt(i + 1), tokens.subList(i, i + 2))
                    i += 2
                }
                isArticle(i) -> i++
                lexicon.unit(token.norm) != null && (i == 0 || i == tokens.lastIndex) && token.norm.codePointCount(0, token.norm.length) > SHORT_UNIT ->
                    {
                        elements += Element.Qty(BigDecimal.ONE, lexicon.unit(token.norm), listOf(token))
                        i++
                    }
                else -> {
                    elements += Element.Name(token)
                    i++
                }
            }
        }
        return elements
    }

    /**
     * Splits a run without separators into items. Quantity-first text ("2 kg rice 1 kg dal") starts a
     * new item at each quantity after a name; name-first text ("rice 2 kg dal 1 kg") ends an item
     * after each quantity.
     */
    private fun groups(elements: List<Element>): List<List<Element>> {
        if (elements.isEmpty()) return emptyList()
        val quantityFirst = elements.first() is Element.Qty
        val groups = mutableListOf<List<Element>>()
        var current = mutableListOf<Element>()
        for (element in elements) {
            val hasName = current.any { it is Element.Name }
            val hasQty = current.any { it is Element.Qty }
            val startNew = when {
                quantityFirst -> element is Element.Qty && hasName
                else -> element is Element.Name && hasName && hasQty
            }
            if (startNew) {
                groups += current
                current = mutableListOf()
            }
            current += element
        }
        if (current.isNotEmpty()) groups += current
        return groups
    }

    private fun List<Element>.toItem(): ParsedItem? {
        val names = filterIsInstance<Element.Name>().map { it.token.surface }
        if (names.isEmpty()) return null
        val quantity = filterIsInstance<Element.Qty>().firstOrNull()
        return ParsedItem(names.joinToString(" "), quantity?.value, quantity?.unit, text())
    }

    private fun List<Element>.text(): String = joinToString(" ") { element ->
        when (element) {
            is Element.Qty -> element.words.joinToString(" ") { it.surface }
            is Element.Name -> element.token.surface
        }
    }

    /** [name] first, then with a known ending removed ("ಅಕ್ಕಿಯನ್ನು" -> "ಅಕ್ಕಿ"), then stem repairs. */
    private fun nameVariants(name: String, lexicon: Lexicon): List<String> {
        val stems = mutableListOf(name)
        val norm = TextNormalizer.normalize(name)
        for (suffix in lexicon.nameSuffixes + lexicon.andSuffixes) {
            if (norm.endsWith(suffix) && norm.length - suffix.length >= MIN_STEM) stems += name.dropLast(suffix.length)
        }
        val repaired = stems.flatMap { stem ->
            lexicon.stemRepairs.mapNotNull { (from, to) -> if (stem.endsWith(from)) stem.dropLast(from.length) + to else null }
        }
        return (stems + repaired).distinct()
    }

    private fun List<String>.startsWith(prefix: List<String>) = size >= prefix.size && subList(0, prefix.size) == prefix

    private fun List<String>.endsWith(suffix: List<String>) = size >= suffix.size && subList(size - suffix.size, size) == suffix

    private companion object {
        const val ENGLISH = "en"
        const val LATIN = "LATIN"

        /** Unit spellings this short ("g", "kg") need a number before them. */
        const val SHORT_UNIT = 2

        /** Shortest stem worth searching after removing an ending. */
        const val MIN_STEM = 2
    }
}
