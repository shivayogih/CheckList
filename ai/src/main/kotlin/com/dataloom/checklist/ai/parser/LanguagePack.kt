package com.dataloom.checklist.ai.parser

import com.dataloom.checklist.domain.localization.SupportedLanguages
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What a command asks for. Order is the tie-break when two intents match equally well, so a bare
 * "mark rice" (a COMPLETE and an UNCOMPLETE prefix) means complete.
 */
enum class CommandIntent { CREATE_CHECKLIST, COMPLETE, UNCOMPLETE, REMOVE, UPDATE, ADD }

/** Words that mark an intent at the start ([prefixes], English verbs) or end ([suffixes], Indian-language verbs). */
@Serializable
data class IntentMarkers(
    val prefixes: List<String> = emptyList(),
    val suffixes: List<String> = emptyList(),
)

/**
 * The vocabulary of one language for [OfflineCommandParser], loaded from
 * `src/main/resources/com/dataloom/checklist/ai/lang/<tag>.json`. Native script and common Latin
 * transliterations live side by side; matching is on normalized whole words (see [TextNormalizer]).
 * Adding a language means adding a file, no code (section 9.4).
 */
@Serializable
data class LanguagePack(
    val language: String,
    /** [Character.UnicodeScript] names whose letters identify this language ("KANNADA"). */
    val scripts: List<String>,
    /** Number words to exact decimal strings: "ಎರಡು" -> "2", "आधा" -> "0.5". */
    val numbers: Map<String, String> = emptyMap(),
    /** "a", "an": a quantity of 1 only when a unit follows ("a dozen eggs"). */
    val articles: List<String> = emptyList(),
    /** Unit code (a [com.dataloom.checklist.domain.model.BuiltInUnits] code) to its spellings. */
    val units: Map<String, List<String>> = emptyMap(),
    /** Words that separate items ("and", "ಮತ್ತು"); commas always do. */
    val separators: List<String> = emptyList(),
    /** Words dropped from item names ("of", "please", "ಸ್ವಲ್ಪ"). */
    val fillers: List<String> = emptyList(),
    /** Endings tried off an item name when the full word is not in the catalog ("ಯನ್ನು", "s"). */
    val nameSuffixes: List<String> = emptyList(),
    /** Endings that also mean "and" ("ും" in "അരിയും പാലും"): the word ends an item. */
    val andSuffixes: List<String> = emptyList(),
    /** Stem endings restored after removing a suffix ("ല" -> "ൽ" for Malayalam chillu letters). */
    val stemRepairs: Map<String, String> = emptyMap(),
    /** Words between the command and a new checklist's title ("called", "नाम"). */
    val titleConnectors: List<String> = emptyList(),
    val intents: Map<CommandIntent, IntentMarkers> = emptyMap(),
)

/** All bundled packs, keyed by language tag. */
class LanguagePacks(val packs: Map<String, LanguagePack>) {

    operator fun get(language: String): LanguagePack? = packs[language]

    companion object {
        private const val RESOURCE_DIR = "/com/dataloom/checklist/ai/lang/"
        private val json = Json { ignoreUnknownKeys = false }

        fun parse(text: String): LanguagePack = json.decodeFromString(LanguagePack.serializer(), text)

        /** Loads one pack per supported language; a missing or malformed pack is a build-time bug. */
        fun bundled(): LanguagePacks = LanguagePacks(
            SupportedLanguages.all.associate { language ->
                val path = "$RESOURCE_DIR${language.tag}.json"
                val stream = checkNotNull(LanguagePacks::class.java.getResourceAsStream(path)) { "Missing language pack $path" }
                val pack = stream.bufferedReader(Charsets.UTF_8).use { parse(it.readText()) }
                check(pack.language == language.tag) { "$path declares language ${pack.language}" }
                language.tag to pack
            },
        )
    }
}
