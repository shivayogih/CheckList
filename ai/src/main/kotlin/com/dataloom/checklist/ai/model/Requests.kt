package com.dataloom.checklist.ai.model

import com.dataloom.checklist.domain.model.UnitCode

/** Where the text came from. Voice input (future) produces the same [Utterance]; nothing downstream changes. */
enum class InputSource { TEXT, VOICE }

/** One command from the user, e.g. "2 kg rice, 1 dozen eggs and milk" or "5 ಕೆಜಿ ಅಕ್ಕಿ". */
data class Utterance(
    val text: String,
    /** BCP 47 tag of the app language, e.g. "kn" or "kn-IN". */
    val locale: String,
    val source: InputSource = InputSource.TEXT,
) {
    companion object {
        /** Quick commands are short; anything longer is refused rather than half-parsed. */
        const val MAX_LENGTH = 500
    }
}

/** "Make me a checklist for a Goa trip." [prompt] is the user's own words. */
data class GenerateRequest(
    val prompt: String,
    val locale: String,
    val allowedUnits: List<UnitCode>,
)

/** A category the AI suggests; [categoryKey] is set for seeded categories. */
data class CategorySuggestion(val categoryKey: String?, val name: String)

/** An item the AI suggests; [canonicalKey] is set when it matches the catalog. */
data class ItemSuggestion(val canonicalKey: String?, val name: String, val defaultUnit: UnitCode?)

data class ItemSuggestionContext(val categoryKey: String, val checklist: ChecklistContext)

/**
 * A checklist summary. Counts and pending names are structured so the UI can format them from string
 * resources; [text] is only set by a model that writes prose in the user's language.
 */
data class AiSummary(
    val totalItems: Int,
    val completedItems: Int,
    val pendingItems: List<String>,
    val text: String? = null,
)

/** Counts and pending names straight from the context; no AI needed. */
fun ChecklistContext.summary(): AiSummary = AiSummary(
    totalItems = items.size,
    completedItems = items.count { it.isCompleted },
    pendingItems = items.filterNot { it.isCompleted }.map { it.name },
)
