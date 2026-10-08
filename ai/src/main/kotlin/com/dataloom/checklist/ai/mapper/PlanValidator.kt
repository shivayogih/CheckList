package com.dataloom.checklist.ai.mapper

import com.dataloom.checklist.ai.model.ActionPlan
import com.dataloom.checklist.ai.model.ContextSnapshot
import com.dataloom.checklist.ai.model.ItemContext
import com.dataloom.checklist.ai.model.ToolCall
import com.dataloom.checklist.ai.tools.AiTool
import com.dataloom.checklist.ai.tools.ParamType
import com.dataloom.checklist.ai.tools.RiskClass
import com.dataloom.checklist.domain.model.Category
import com.dataloom.checklist.domain.model.Quantity
import com.dataloom.checklist.domain.model.UnitDef
import com.dataloom.checklist.domain.validation.ChecklistValidator
import com.dataloom.checklist.domain.validation.ItemValidator
import com.dataloom.checklist.domain.validation.ValidationResult
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Turns an untrusted [ActionPlan] into [PlannedOperation]s (the CommandMapper + CommandValidator of
 * section 11.2). Every call is checked against the [com.dataloom.checklist.ai.tools.AiTool] schema
 * (known tool, known arguments, right types), resolved against this request's refs and the local
 * catalog (keys, categories, units), and run through the same domain validators the UI uses. A call
 * that fails any check is rejected with its reasons and never reaches the executor. This class only
 * reads; it never writes.
 */
class PlanValidator @Inject constructor(private val catalog: CatalogLookup) {

    suspend fun validate(plan: ActionPlan, snapshot: ContextSnapshot): ValidatedPlan {
        val session = Session(snapshot)
        val operations = mutableListOf<PlannedOperation>()
        val rejected = mutableListOf<RejectedCall>()
        // A plan that starts with createChecklist writes into the new list; refs to the open one are invalid.
        session.createsChecklist = plan.calls.firstOrNull()?.name == AiTool.CREATE_CHECKLIST.toolName
        plan.calls.forEachIndexed { index, call ->
            when (val outcome = validateCall(index, call, session)) {
                is Outcome.Ok -> {
                    operations += outcome.operation
                    if (outcome.operation is PlannedOperation.CreateChecklist) session.createdChecklist = outcome.operation
                }
                is Outcome.Bad -> rejected += RejectedCall(index, call, outcome.problems)
            }
        }
        return ValidatedPlan(operations, rejected, plan.unresolved, plan.source, snapshot.context.locale)
    }

    private sealed interface Outcome {
        data class Ok(val operation: PlannedOperation) : Outcome

        data class Bad(val problems: List<ToolProblem>) : Outcome
    }

    /** Per-plan state and lazily loaded catalog data. */
    private inner class Session(val snapshot: ContextSnapshot) {
        var createsChecklist = false
        var createdChecklist: PlannedOperation.CreateChecklist? = null
        private var categoryCache: List<Category>? = null
        private var unitCache: List<UnitDef>? = null

        val locale: String get() = snapshot.context.locale

        /** Only called after [target] was checked to be [ChecklistTarget.Existing]. */
        fun checklistId() = checkNotNull(snapshot.checklistId)

        val target: ChecklistTarget?
            get() = when {
                createsChecklist -> if (createdChecklist != null) ChecklistTarget.CreatedInPlan else null
                else -> snapshot.checklistId?.let { ChecklistTarget.Existing(it) }
            }

        suspend fun categories(): List<Category> = categoryCache ?: catalog.categories(locale).also { categoryCache = it }

        suspend fun unit(code: String): UnitDef? = (unitCache ?: catalog.units().also { unitCache = it })
            .firstOrNull { it.code.value == code }

        suspend fun category(keyOrName: String): Category? {
            val all = categories()
            return all.firstOrNull { it.canonicalKey == keyOrName }
                ?: all.firstOrNull { it.displayName.equals(keyOrName, ignoreCase = true) }
        }
    }

    private suspend fun validateCall(index: Int, call: ToolCall, session: Session): Outcome {
        val tool = AiTool.byName(call.name) ?: return Outcome.Bad(listOf(ToolProblem.UnknownTool))
        if (tool.risk == RiskClass.READ) return Outcome.Bad(listOf(ToolProblem.NotAPlanStep))
        val shape = shapeProblems(tool, call.arguments)
        if (shape.isNotEmpty()) return Outcome.Bad(shape)
        val args = Args(call.arguments)
        return when (tool) {
            AiTool.CREATE_CHECKLIST -> createChecklist(index, args, session)
            AiTool.ADD_CATEGORY -> addCategory(index, args, session)
            AiTool.ADD_CHECKLIST_ITEM -> addItem(index, args, session)
            AiTool.UPDATE_ITEM_QUANTITY -> updateQuantity(index, args, session)
            AiTool.UPDATE_ITEM_UNIT -> updateUnit(index, args, session)
            AiTool.COMPLETE_ITEM, AiTool.UNCOMPLETE_ITEM -> simpleItemOperation(args, session) { item ->
                PlannedOperation.SetCompleted(index, item.itemId, item.name, completed = tool == AiTool.COMPLETE_ITEM)
            }
            AiTool.DELETE_ITEM -> simpleItemOperation(args, session) { item -> PlannedOperation.DeleteItem(index, item.itemId, item.name) }
            AiTool.REMOVE_CATEGORY -> removeCategory(index, args, session)
            AiTool.SEARCH_MASTER_ITEMS, AiTool.SUGGEST_CATEGORIES, AiTool.SUGGEST_ITEMS, AiTool.SUMMARIZE_CHECKLIST ->
                Outcome.Bad(listOf(ToolProblem.NotAPlanStep))
        }
    }

    private suspend fun createChecklist(index: Int, args: Args, session: Session): Outcome {
        if (index != 0) return Outcome.Bad(listOf(ToolProblem.CreateChecklistNotFirst))
        val problems = mutableListOf<ToolProblem>()
        val categories = args.list("categories").map { session.category(it) }
        if (categories.any { it == null }) problems += ToolProblem.UnknownCategory
        val fields = when (val result = ChecklistValidator.validate(args.text("title").orEmpty(), args.text("description"))) {
            is ValidationResult.Invalid -> null.also { problems += ToolProblem.Invalid(result.errors) }
            is ValidationResult.Valid -> result.value
        }
        if (fields == null || problems.isNotEmpty()) return Outcome.Bad(problems)
        return Outcome.Ok(PlannedOperation.CreateChecklist(index, fields.title, fields.description, categories.filterNotNull().distinct()))
    }

    private suspend fun addCategory(index: Int, args: Args, session: Session): Outcome {
        val target = session.target ?: return Outcome.Bad(listOf(ToolProblem.NoChecklist))
        val keyOrName = args.text("categoryKey") ?: args.text("name")
            ?: return Outcome.Bad(listOf(ToolProblem.MissingArgument("categoryKey")))
        val category = session.category(keyOrName) ?: return Outcome.Bad(listOf(ToolProblem.UnknownCategory))
        val present = when (target) {
            is ChecklistTarget.Existing -> session.snapshot.sections.values.any { it.categoryId == category.id }
            ChecklistTarget.CreatedInPlan -> session.createdChecklist?.categories.orEmpty().any { it.id == category.id }
        }
        if (present) return Outcome.Bad(listOf(ToolProblem.CategoryAlreadyInChecklist))
        return Outcome.Ok(PlannedOperation.AddCategory(index, target, category))
    }

    private suspend fun addItem(index: Int, args: Args, session: Session): Outcome {
        val target = session.target ?: return Outcome.Bad(listOf(ToolProblem.NoChecklist))
        val problems = mutableListOf<ToolProblem>()
        val name = args.text("name").orEmpty()
        val master = args.text("canonicalKey")?.let { catalog.masterItemByKey(it, name, session.locale) }
        val quantity = args.quantity(problems)
        val unitDef = args.unit(problems, session)
        val explicitSection = args.text("sectionRef")?.let { ref ->
            session.snapshot.sections[ref].takeIf { !session.createsChecklist }
                ?: null.also { problems += ToolProblem.UnknownRef("sectionRef") }
        }
        if (problems.isNotEmpty()) return Outcome.Bad(problems)

        // Mirrors AddMasterItemsToSectionUseCase: the default unit applies only when there is an amount.
        val effectiveUnit = unitDef ?: master?.defaultUnit?.takeIf { quantity != null }?.let { session.unit(it.value) }
        val displayName = master?.displayName ?: name
        val fields = when (val result = ItemValidator.validate(displayName, quantity, effectiveUnit, notes = null)) {
            is ValidationResult.Invalid -> return Outcome.Bad(listOf(ToolProblem.Invalid(result.errors)))
            is ValidationResult.Valid -> result.value
        }

        val placement = when {
            explicitSection != null -> SectionTarget.Existing(explicitSection.sectionId) to explicitSection.name
            else -> placeItem(master?.categoryId?.let { id -> session.categories().firstOrNull { it.id == id } }, target, session)
        } ?: return Outcome.Bad(listOf(ToolProblem.NoSectionForItem))
        return Outcome.Ok(
            PlannedOperation.AddItem(
                callIndex = index,
                checklist = target,
                section = placement.first,
                sectionName = placement.second,
                masterItem = master,
                name = fields.name,
                quantity = fields.quantity,
                unit = fields.unit,
            ),
        )
    }

    /**
     * A catalog item goes to its category's section (added if missing). A typed item goes to the
     * section the user is looking at, else the only section, else the "Other" category's section.
     */
    private suspend fun placeItem(category: Category?, target: ChecklistTarget, session: Session): Pair<SectionTarget, String>? {
        val sections = if (target is ChecklistTarget.Existing) session.snapshot.sections else emptyMap()
        if (category != null) {
            val existing = sections.values.firstOrNull { it.categoryId == category.id }
            return if (existing != null) SectionTarget.Existing(existing.sectionId) to existing.name else SectionTarget.ForCategory(category.id) to category.displayName
        }
        val focused = session.snapshot.context.focusedSectionRef?.let { sections[it] }
        val only = sections.values.singleOrNull()
        (focused ?: only)?.let { return SectionTarget.Existing(it.sectionId) to it.name }
        val other = session.categories().firstOrNull { it.canonicalKey == CatalogLookup.OTHER_CATEGORY_KEY } ?: return null
        val otherSection = sections.values.firstOrNull { it.categoryId == other.id }
        return if (otherSection != null) SectionTarget.Existing(otherSection.sectionId) to otherSection.name else SectionTarget.ForCategory(other.id) to other.displayName
    }

    private suspend fun updateQuantity(index: Int, args: Args, session: Session): Outcome {
        val problems = mutableListOf<ToolProblem>()
        val quantity = args.quantity(problems)
        val unitDef = args.unit(problems, session)
        val (item, context) = resolveItem(args, session, problems)
        if (problems.isNotEmpty() || item == null || context == null || quantity == null) return Outcome.Bad(problems)
        val effectiveUnit = unitDef ?: context.unit?.let { session.unit(it.value) }
        return when (val result = ItemValidator.validate(context.name, quantity, effectiveUnit, notes = null)) {
            is ValidationResult.Invalid -> Outcome.Bad(listOf(ToolProblem.Invalid(result.errors)))
            is ValidationResult.Valid ->
                Outcome.Ok(PlannedOperation.UpdateQuantity(index, session.checklistId(), item.itemId, item.name, quantity, unitDef?.code))
        }
    }

    private suspend fun updateUnit(index: Int, args: Args, session: Session): Outcome {
        val problems = mutableListOf<ToolProblem>()
        val unitDef = args.unit(problems, session)
        val (item, context) = resolveItem(args, session, problems)
        if (problems.isNotEmpty() || item == null || context == null || unitDef == null) return Outcome.Bad(problems)
        return when (val result = ItemValidator.validate(context.name, context.quantity, unitDef, notes = null)) {
            is ValidationResult.Invalid -> Outcome.Bad(listOf(ToolProblem.Invalid(result.errors)))
            is ValidationResult.Valid ->
                Outcome.Ok(PlannedOperation.UpdateUnit(index, session.checklistId(), item.itemId, item.name, unitDef.code))
        }
    }

    private fun simpleItemOperation(
        args: Args,
        session: Session,
        build: (ContextSnapshot.ItemTarget) -> PlannedOperation,
    ): Outcome {
        val problems = mutableListOf<ToolProblem>()
        val (item, _) = resolveItem(args, session, problems)
        return if (item == null) Outcome.Bad(problems) else Outcome.Ok(build(item))
    }

    private fun removeCategory(index: Int, args: Args, session: Session): Outcome {
        if (session.target !is ChecklistTarget.Existing) return Outcome.Bad(listOf(ToolProblem.NoChecklist))
        val section = session.snapshot.sections[args.text("sectionRef")]
            ?: return Outcome.Bad(listOf(ToolProblem.UnknownRef("sectionRef")))
        return Outcome.Ok(PlannedOperation.RemoveSection(index, section.sectionId, section.name))
    }

    /**
     * Resolves "itemRef" in the open checklist, adding a problem when it fails. Refs never point into a
     * checklist the plan itself creates.
     */
    private fun resolveItem(
        args: Args,
        session: Session,
        problems: MutableList<ToolProblem>,
    ): Pair<ContextSnapshot.ItemTarget?, ItemContext?> {
        if (session.target !is ChecklistTarget.Existing) {
            problems += ToolProblem.NoChecklist
            return null to null
        }
        val ref = args.text("itemRef")
        val item = session.snapshot.items[ref]
        val context = session.snapshot.context.items.firstOrNull { it.ref == ref }
        if (item == null || context == null) {
            problems += ToolProblem.UnknownRef("itemRef")
            return null to null
        }
        return item to context
    }

    private fun shapeProblems(tool: AiTool, arguments: Map<String, Any?>): List<ToolProblem> = buildList {
        arguments.keys.filter { tool.param(it) == null }.forEach { add(ToolProblem.UnexpectedArgument(it)) }
        for (param in tool.params) {
            val value = arguments[param.name]
            val present = value != null && !(value is String && value.isBlank())
            if (!present) {
                if (param.required) add(ToolProblem.MissingArgument(param.name))
                continue
            }
            val typeOk = when (param.type) {
                ParamType.STRING, ParamType.UNIT, ParamType.SECTION_REF, ParamType.ITEM_REF -> value is String
                ParamType.NUMBER -> value is Number || value is String
                ParamType.STRING_LIST -> value is List<*> && value.all { it is String }
            }
            if (!typeOk) add(ToolProblem.WrongType(param.name))
        }
    }

    /** Arguments after [shapeProblems] passed, so types are known to be right. */
    private class Args(private val values: Map<String, Any?>) {
        fun text(name: String): String? = (values[name] as? String)?.trim()?.takeIf { it.isNotEmpty() }

        fun list(name: String): List<String> =
            (values[name] as? List<*>).orEmpty().filterIsInstance<String>().map { it.trim() }.filter { it.isNotEmpty() }

        fun quantity(problems: MutableList<ToolProblem>): Quantity? {
            val raw = values["quantity"] ?: return null
            if (raw is String && raw.isBlank()) return null
            return quantityOf(raw) ?: null.also { problems += ToolProblem.InvalidQuantity }
        }

        suspend fun unit(problems: MutableList<ToolProblem>, session: PlanValidator.Session): UnitDef? {
            val code = text("unit") ?: return null
            return session.unit(code) ?: null.also { problems += ToolProblem.UnknownUnit }
        }
    }

    companion object {
        /** Exact decimal conversion; a model's 2.5 arrives as a Double, the parser's as "2.5". */
        internal fun quantityOf(value: Any?): Quantity? = when (value) {
            is String -> Quantity.parse(value)
            is BigDecimal -> Quantity.parse(value.toPlainString())
            is Int, is Long, is Short, is Byte -> Quantity.parse(value.toString())
            is Double -> value.takeIf { it.isFinite() }?.let { Quantity.parse(BigDecimal(it.toString()).toPlainString()) }
            is Float -> value.takeIf { it.isFinite() }?.let { Quantity.parse(BigDecimal(it.toString()).toPlainString()) }
            else -> null
        }
    }
}
