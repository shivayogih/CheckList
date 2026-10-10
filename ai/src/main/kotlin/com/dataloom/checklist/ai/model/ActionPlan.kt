package com.dataloom.checklist.ai.model

/**
 * One proposed tool invocation, exactly as an AI produced it. Nothing here is trusted: names, keys,
 * refs and numbers are checked by [com.dataloom.checklist.ai.mapper.PlanValidator] before a user ever
 * sees them, and executed only through domain use cases after confirmation.
 *
 * [arguments] values are what a function-calling model returns after JSON decoding: [String],
 * [Number], [Boolean] or a [List] of strings. Anything else is rejected.
 */
data class ToolCall(
    val name: String,
    val arguments: Map<String, Any?> = emptyMap(),
)

enum class PlanSource { MOCK, OFFLINE_PARSER, ONLINE_MODEL }

/**
 * What an [com.dataloom.checklist.ai.service.AIService] proposes: an ordered list of tool calls. A plan
 * is only a proposal ("AI proposes, the app disposes", ADR-008); it has no way to execute itself.
 */
data class ActionPlan(
    val calls: List<ToolCall>,
    val source: PlanSource,
    /** Parts of the input that produced no call, so the review screen can say what was skipped. */
    val unresolved: List<UnresolvedFragment> = emptyList(),
)

data class UnresolvedFragment(val text: String, val reason: UnresolvedReason)

enum class UnresolvedReason {
    /** No item name, quantity or command could be read from this part. */
    NOT_UNDERSTOOD,

    /** "Mark ghee done" when the open checklist has no ghee. */
    ITEM_NOT_IN_CHECKLIST,

    /** "Change rice" without the new amount. */
    MISSING_QUANTITY,

    /** "Create a checklist" without a name. */
    MISSING_TITLE,
}
