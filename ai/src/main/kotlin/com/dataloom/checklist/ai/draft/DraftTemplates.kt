package com.dataloom.checklist.ai.draft

import com.dataloom.checklist.domain.localization.SupportedLanguages
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One common scenario ("trip", "Diwali", "property purchase") the offline draft builder knows, loaded
 * from `src/main/resources/com/dataloom/checklist/ai/draft/templates.json`. [keywords] pick the
 * template (2 points each), [hints] only support it (1 point: "documents", "shopping"). Keywords of
 * every language are checked, so a mixed "ಗೋವಾ trip" still matches. Adding a scenario means editing
 * the JSON, no code.
 */
@Serializable
data class DraftTemplate(
    val id: String,
    /** Language tag to words or short phrases; a word also matches longer words it starts ("ಪ್ರವಾಸಕ್ಕೆ"). */
    val keywords: Map<String, List<String>>,
    val hints: Map<String, List<String>> = emptyMap(),
    /** Fallback checklist title per language when the request has nothing left after the fillers. */
    val title: Map<String, String>,
    val sections: List<TemplateSection>,
)

/**
 * Items of one catalog category. [items] are seed canonical keys of that category; [custom] are typed
 * items (not in the catalog) with a name per language.
 */
@Serializable
data class TemplateSection(
    val category: String,
    val items: List<String> = emptyList(),
    val custom: List<Map<String, String>> = emptyList(),
)

@Serializable
data class DraftTemplates(
    /** Words dropped from a request to make the title ("create", "checklist", "ಮಾಡಿ"). */
    val fillers: Map<String, List<String>>,
    val templates: List<DraftTemplate>,
) {
    companion object {
        private const val RESOURCE = "/com/dataloom/checklist/ai/draft/templates.json"
        private val json = Json { ignoreUnknownKeys = false }

        fun parse(text: String): DraftTemplates = json.decodeFromString(serializer(), text)

        /** The bundled templates; a missing or malformed file is a build-time bug (TemplatesTest). */
        fun bundled(): DraftTemplates {
            val stream = checkNotNull(DraftTemplates::class.java.getResourceAsStream(RESOURCE)) { "Missing $RESOURCE" }
            val templates = stream.bufferedReader(Charsets.UTF_8).use { parse(it.readText()) }
            val languages = SupportedLanguages.all.map { it.tag }.toSet()
            templates.templates.forEach { template ->
                check(template.title.keys == languages) { "Template ${template.id} needs a title in every language" }
            }
            return templates
        }
    }
}
