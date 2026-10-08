package com.dataloom.checklist.ai.tools

import com.dataloom.checklist.domain.model.UnitCode

/** How much a tool can change; drives [com.dataloom.checklist.ai.policy.ConfirmationPolicy] (section 13). */
enum class RiskClass { READ, CREATE, MODIFY, DESTRUCTIVE }

enum class ParamType {
    STRING,

    /** A decimal amount; strings ("2.5") and JSON numbers are both accepted. */
    NUMBER,

    /** One of the allowed unit codes ("KG"); declared to the model as an enum. */
    UNIT,
    STRING_LIST,

    /** "s1": a section ref from the request's [com.dataloom.checklist.ai.model.ChecklistContext]. */
    SECTION_REF,

    /** "i3": an item ref from the request's context. */
    ITEM_REF,
}

data class ToolParam(
    val name: String,
    val type: ParamType,
    val required: Boolean,
    /** For the model, not for users: model-facing text is not UI and is not translated. */
    val description: String,
)

/**
 * The single source of truth for every tool an AI may call (section 13). Function declarations for a
 * model ([ToolCatalog.declarations]), the validator and the executor all switch over this enum, so a
 * new tool cannot be declared without being validated and executed (the `when`s are exhaustive).
 */
enum class AiTool(
    val toolName: String,
    val risk: RiskClass,
    val description: String,
    val params: List<ToolParam>,
) {
    SEARCH_MASTER_ITEMS(
        "searchMasterItems",
        RiskClass.READ,
        "Search the local item catalog by name in any supported language. Returns canonical keys.",
        listOf(
            ToolParam("query", ParamType.STRING, required = true, "Item name as the user wrote it."),
            ToolParam("categoryKey", ParamType.STRING, required = false, "Limit the search to one category key."),
        ),
    ),
    SUGGEST_CATEGORIES(
        "suggestCategories",
        RiskClass.READ,
        "List the categories available for a checklist title.",
        listOf(ToolParam("title", ParamType.STRING, required = true, "Checklist title.")),
    ),
    SUGGEST_ITEMS(
        "suggestItems",
        RiskClass.READ,
        "List catalog items of one category, most used first.",
        listOf(ToolParam("categoryKey", ParamType.STRING, required = true, "Category key, e.g. groceries.")),
    ),
    SUMMARIZE_CHECKLIST(
        "summarizeChecklist",
        RiskClass.READ,
        "Counts and pending items of the open checklist.",
        emptyList(),
    ),
    CREATE_CHECKLIST(
        "createChecklist",
        RiskClass.CREATE,
        "Create a new checklist. Must be the first call of a plan; later addChecklistItem calls without " +
            "sectionRef go into it.",
        listOf(
            ToolParam("title", ParamType.STRING, required = true, "Title, 1-100 characters."),
            ToolParam("description", ParamType.STRING, required = false, "Optional description."),
            ToolParam("categories", ParamType.STRING_LIST, required = false, "Category keys to start with."),
        ),
    ),
    ADD_CATEGORY(
        "addCategory",
        RiskClass.CREATE,
        "Add an existing category to the open checklist. Give categoryKey or the category name.",
        listOf(
            ToolParam("categoryKey", ParamType.STRING, required = false, "Category key, e.g. vegetables."),
            ToolParam("name", ParamType.STRING, required = false, "Category name when there is no key."),
        ),
    ),
    ADD_CHECKLIST_ITEM(
        "addChecklistItem",
        RiskClass.CREATE,
        "Add one item. Use the catalog canonicalKey when the item exists; otherwise only the name.",
        listOf(
            ToolParam("name", ParamType.STRING, required = true, "Item name, 1-80 characters."),
            ToolParam("canonicalKey", ParamType.STRING, required = false, "Catalog key, e.g. rice."),
            ToolParam("quantity", ParamType.NUMBER, required = false, "Amount, more than 0, at most 3 decimals."),
            ToolParam("unit", ParamType.UNIT, required = false, "Unit code; needs a quantity."),
            ToolParam("sectionRef", ParamType.SECTION_REF, required = false, "Section to add to, e.g. s1."),
        ),
    ),
    UPDATE_ITEM_QUANTITY(
        "updateItemQuantity",
        RiskClass.MODIFY,
        "Change the amount of an item already in the checklist.",
        listOf(
            ToolParam("itemRef", ParamType.ITEM_REF, required = true, "Item ref, e.g. i3."),
            ToolParam("quantity", ParamType.NUMBER, required = true, "New amount."),
            ToolParam("unit", ParamType.UNIT, required = false, "New unit; omit to keep the current one."),
        ),
    ),
    UPDATE_ITEM_UNIT(
        "updateItemUnit",
        RiskClass.MODIFY,
        "Change the unit of an item that already has an amount.",
        listOf(
            ToolParam("itemRef", ParamType.ITEM_REF, required = true, "Item ref, e.g. i3."),
            ToolParam("unit", ParamType.UNIT, required = true, "New unit code."),
        ),
    ),
    COMPLETE_ITEM(
        "completeItem",
        RiskClass.MODIFY,
        "Tick an item as done.",
        listOf(ToolParam("itemRef", ParamType.ITEM_REF, required = true, "Item ref, e.g. i3.")),
    ),
    UNCOMPLETE_ITEM(
        "uncompleteItem",
        RiskClass.MODIFY,
        "Untick an item.",
        listOf(ToolParam("itemRef", ParamType.ITEM_REF, required = true, "Item ref, e.g. i3.")),
    ),
    REMOVE_CATEGORY(
        "removeCategory",
        RiskClass.DESTRUCTIVE,
        "Remove a section and all its items from the checklist.",
        listOf(ToolParam("sectionRef", ParamType.SECTION_REF, required = true, "Section ref, e.g. s1.")),
    ),
    DELETE_ITEM(
        "deleteItem",
        RiskClass.DESTRUCTIVE,
        "Delete an item from the checklist.",
        listOf(ToolParam("itemRef", ParamType.ITEM_REF, required = true, "Item ref, e.g. i3.")),
    ),
    ;

    fun param(name: String): ToolParam? = params.firstOrNull { it.name == name }

    companion object {
        fun byName(name: String): AiTool? = entries.firstOrNull { it.toolName == name }
    }
}

/** Vendor-neutral function declaration; an online adapter converts it to its SDK's schema type. */
data class FunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: List<ParameterDeclaration>,
)

data class ParameterDeclaration(
    val name: String,
    val type: SchemaType,
    val description: String,
    val required: Boolean,
    /** Allowed values for an enum parameter (unit codes); empty when free. */
    val enumValues: List<String> = emptyList(),
)

enum class SchemaType { STRING, NUMBER, STRING_ARRAY }

object ToolCatalog {

    /**
     * Function declarations for a model. Units become an enum of [allowedUnits], so an unknown unit is
     * a validation error, never a silently created custom unit (section 13).
     */
    fun declarations(allowedUnits: List<UnitCode>): List<FunctionDeclaration> =
        AiTool.entries.map { tool ->
            FunctionDeclaration(
                name = tool.toolName,
                description = tool.description,
                parameters = tool.params.map { param ->
                    ParameterDeclaration(
                        name = param.name,
                        type = when (param.type) {
                            ParamType.NUMBER -> SchemaType.NUMBER
                            ParamType.STRING_LIST -> SchemaType.STRING_ARRAY
                            ParamType.STRING, ParamType.UNIT, ParamType.SECTION_REF, ParamType.ITEM_REF -> SchemaType.STRING
                        },
                        description = param.description,
                        required = param.required,
                        enumValues = if (param.type == ParamType.UNIT) allowedUnits.map { it.value } else emptyList(),
                    )
                },
            )
        }
}
