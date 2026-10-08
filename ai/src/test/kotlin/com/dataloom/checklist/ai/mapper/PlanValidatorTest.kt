package com.dataloom.checklist.ai.mapper

import com.dataloom.checklist.ai.fixtures.AiTestHarness
import com.dataloom.checklist.ai.model.ActionPlan
import com.dataloom.checklist.ai.model.ContextSnapshot
import com.dataloom.checklist.ai.model.PlanSource
import com.dataloom.checklist.ai.model.ToolCall
import com.dataloom.checklist.domain.model.BuiltInUnits
import com.dataloom.checklist.domain.model.Quantity
import com.dataloom.checklist.domain.validation.ValidationError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every untrusted call is checked before anyone sees it. Open checklist: s1 Groceries [i1 Rice 1 kg,
 * i2 Milk, i3 Ghee], s2 Vegetables [i4 Onion].
 */
class PlanValidatorTest {

    private val harness = AiTestHarness()

    private fun call(name: String, vararg args: Pair<String, Any?>) = ToolCall(name, mapOf(*args))

    private suspend fun validate(vararg calls: ToolCall, snapshot: ContextSnapshot? = null): ValidatedPlan {
        val context = snapshot ?: harness.snapshot(harness.weeklyList())
        return harness.validator.validate(ActionPlan(calls.toList(), PlanSource.ONLINE_MODEL), context)
    }

    private suspend fun problems(vararg calls: ToolCall): List<ToolProblem> {
        val plan = validate(*calls)
        assertTrue("expected a rejection, got ${plan.operations}", plan.operations.isEmpty())
        return plan.rejected.single().problems
    }

    private suspend fun accepted(vararg calls: ToolCall): List<PlannedOperation> {
        val plan = validate(*calls)
        assertEquals("unexpected rejections", emptyList<RejectedCall>(), plan.rejected)
        return plan.operations
    }

    // --- Shape: tool names, argument names and types -------------------------------------------

    @Test
    fun `unknown tools are rejected`() = runTest {
        assertEquals(listOf(ToolProblem.UnknownTool), problems(call("dropDatabase")))
        assertEquals(listOf(ToolProblem.UnknownTool), problems(call("AddChecklistItem", "name" to "rice")))
    }

    @Test
    fun `read tools are never plan steps`() = runTest {
        assertEquals(listOf(ToolProblem.NotAPlanStep), problems(call("searchMasterItems", "query" to "rice")))
    }

    @Test
    fun `missing, unexpected and mistyped arguments are rejected`() = runTest {
        assertEquals(listOf(ToolProblem.MissingArgument("name")), problems(call("addChecklistItem", "quantity" to "2")))
        assertEquals(listOf(ToolProblem.MissingArgument("name")), problems(call("addChecklistItem", "name" to "  ")))
        assertEquals(listOf(ToolProblem.UnexpectedArgument("price")), problems(call("addChecklistItem", "name" to "rice", "price" to "40")))
        assertEquals(listOf(ToolProblem.WrongType("quantity")), problems(call("addChecklistItem", "name" to "rice", "quantity" to true)))
        assertEquals(listOf(ToolProblem.WrongType("categories")), problems(call("createChecklist", "title" to "Trip", "categories" to "travel")))
        assertEquals(listOf(ToolProblem.WrongType("itemRef")), problems(call("completeItem", "itemRef" to 1)))
    }

    // --- Values: quantities, units, business rules ---------------------------------------------

    @Test
    fun `quantities outside the domain rules are rejected`() = runTest {
        listOf("0", "-1", "100000", "2.1234", "abc", Double.NaN, 0.0, -2).forEach { bad ->
            assertEquals("quantity $bad", listOf(ToolProblem.InvalidQuantity), problems(call("addChecklistItem", "name" to "rice", "quantity" to bad)))
        }
    }

    @Test
    fun `unknown units are rejected, never created`() = runTest {
        assertEquals(listOf(ToolProblem.UnknownUnit), problems(call("addChecklistItem", "name" to "rice", "quantity" to "2", "unit" to "TONNE")))
        assertEquals(listOf(ToolProblem.UnknownUnit), problems(call("addChecklistItem", "name" to "rice", "quantity" to "2", "unit" to "kg")))
        assertEquals(12, harness.catalog.observeUnitsNow().size)
    }

    @Test
    fun `the same item rules as the UI apply`() = runTest {
        assertEquals(
            listOf(ToolProblem.Invalid(listOf(ValidationError.QUANTITY_MUST_BE_WHOLE))),
            problems(call("addChecklistItem", "name" to "eggs", "quantity" to "2.5", "unit" to "DOZEN")),
        )
        assertEquals(
            listOf(ToolProblem.Invalid(listOf(ValidationError.UNIT_WITHOUT_QUANTITY))),
            problems(call("addChecklistItem", "name" to "eggs", "unit" to "DOZEN")),
        )
        assertEquals(
            listOf(ToolProblem.Invalid(listOf(ValidationError.ITEM_NAME_TOO_LONG))),
            problems(call("addChecklistItem", "name" to "x".repeat(81))),
        )
        assertEquals(
            listOf(ToolProblem.Invalid(listOf(ValidationError.TITLE_TOO_LONG))),
            problems(call("createChecklist", "title" to "t".repeat(101))),
        )
        assertEquals(
            listOf(ToolProblem.Invalid(listOf(ValidationError.UNIT_WITHOUT_QUANTITY))),
            problems(call("updateItemUnit", "itemRef" to "i2", "unit" to "LITRE")),
        )
        assertEquals(
            listOf(ToolProblem.Invalid(listOf(ValidationError.QUANTITY_MUST_BE_WHOLE))),
            problems(call("updateItemQuantity", "itemRef" to "i1", "quantity" to "1.5", "unit" to "PIECE")),
        )
    }

    // --- References and plan structure ---------------------------------------------------------

    @Test
    fun `refs must come from this request's context`() = runTest {
        assertEquals(listOf(ToolProblem.UnknownRef("itemRef")), problems(call("completeItem", "itemRef" to "i99")))
        assertEquals(listOf(ToolProblem.UnknownRef("itemRef")), problems(call("deleteItem", "itemRef" to "item-7")))
        assertEquals(listOf(ToolProblem.UnknownRef("sectionRef")), problems(call("removeCategory", "sectionRef" to "s9")))
        assertEquals(listOf(ToolProblem.UnknownRef("sectionRef")), problems(call("addChecklistItem", "name" to "rice", "sectionRef" to "s9")))
    }

    @Test
    fun `categories must exist and not be in the list already`() = runTest {
        assertEquals(listOf(ToolProblem.UnknownCategory), problems(call("addCategory", "categoryKey" to "weapons")))
        assertEquals(listOf(ToolProblem.CategoryAlreadyInChecklist), problems(call("addCategory", "categoryKey" to "groceries")))
        assertEquals(listOf(ToolProblem.MissingArgument("categoryKey")), problems(call("addCategory")))
        assertEquals(listOf(ToolProblem.UnknownCategory), problems(call("createChecklist", "title" to "Trip", "categories" to listOf("travel", "spaceships"))))
        val added = accepted(call("addCategory", "name" to "Fruits")).single() as PlannedOperation.AddCategory
        assertEquals("fruits", added.category.canonicalKey)
    }

    @Test
    fun `createChecklist must come first and owns the plan`() = runTest {
        val plan = validate(
            call("addChecklistItem", "name" to "rice"),
            call("createChecklist", "title" to "Trip"),
        )
        assertEquals(listOf(ToolProblem.CreateChecklistNotFirst), plan.rejected.single { it.callIndex == 1 }.problems)

        val creating = validate(
            call("createChecklist", "title" to "Trip", "categories" to listOf("travel")),
            call("addChecklistItem", "name" to "Passport", "canonicalKey" to "passport"),
            call("completeItem", "itemRef" to "i1"),
            call("addChecklistItem", "name" to "rice", "sectionRef" to "s1"),
        )
        assertEquals(2, creating.operations.size)
        val add = creating.operations[1] as PlannedOperation.AddItem
        assertEquals(ChecklistTarget.CreatedInPlan, add.checklist)
        // Refs point into the open checklist, not the new one, so they are refused.
        assertEquals(listOf(ToolProblem.NoChecklist), creating.rejected.single { it.callIndex == 2 }.problems)
        assertEquals(listOf(ToolProblem.UnknownRef("sectionRef")), creating.rejected.single { it.callIndex == 3 }.problems)
    }

    @Test
    fun `steps after a failed createChecklist have no checklist`() = runTest {
        val plan = validate(call("createChecklist", "title" to " "), call("addChecklistItem", "name" to "rice"))
        assertTrue(plan.operations.isEmpty())
        assertEquals(listOf(ToolProblem.NoChecklist), plan.rejected[1].problems)
    }

    @Test
    fun `item steps need an open checklist`() = runTest {
        val empty = harness.snapshot(null)
        val plan = validate(call("addChecklistItem", "name" to "rice"), call("completeItem", "itemRef" to "i1"), snapshot = empty)
        assertEquals(listOf(ToolProblem.NoChecklist), plan.rejected[0].problems)
        assertEquals(listOf(ToolProblem.NoChecklist), plan.rejected[1].problems)
    }

    @Test
    fun `one bad call does not hide the good ones`() = runTest {
        val plan = validate(call("addChecklistItem", "name" to "rice", "canonicalKey" to "rice"), call("deleteItem", "itemRef" to "i42"))
        assertEquals(1, plan.operations.size)
        assertEquals(1, plan.rejected.single().callIndex)
    }

    // --- Resolution -----------------------------------------------------------------------------

    @Test
    fun `catalog items resolve by key and go to their category's section`() = runTest {
        val snapshot = harness.snapshot(harness.weeklyList())
        val ops = harness.validator.validate(
            ActionPlan(
                listOf(
                    call("addChecklistItem", "name" to "rice", "canonicalKey" to "rice", "quantity" to 2.5, "unit" to "KG"),
                    call("addChecklistItem", "name" to "tomato", "canonicalKey" to "tomato"),
                    call("addChecklistItem", "name" to "banana", "canonicalKey" to "banana", "quantity" to 1),
                    call("addChecklistItem", "name" to "Passport", "canonicalKey" to "travel_passport"),
                ),
                PlanSource.ONLINE_MODEL,
            ),
            snapshot,
        ).operations.map { it as PlannedOperation.AddItem }
        val groceries = snapshot.sections.getValue("s1").sectionId
        val vegetables = snapshot.sections.getValue("s2").sectionId

        assertEquals("rice", ops[0].masterItem?.canonicalKey)
        assertEquals(SectionTarget.Existing(groceries), ops[0].section)
        assertEquals(Quantity.parse("2.5"), ops[0].quantity)
        assertEquals(BuiltInUnits.KG.code, ops[0].unit)
        assertEquals(SectionTarget.Existing(vegetables), ops[1].section)
        // Fruits is not in the list yet: its section is added first; the default unit applies with an amount.
        assertEquals(SectionTarget.ForCategory(harness.catalog.category("fruits").id), ops[2].section)
        assertEquals(BuiltInUnits.DOZEN.code, ops[2].unit)
        assertEquals("travel_passport", ops[3].masterItem?.canonicalKey)
    }

    @Test
    fun `typed items go to the focused section, else the only one, else Other`() = runTest {
        val list = harness.weeklyList()
        val eggs = call("addChecklistItem", "name" to "eggs")

        val unfocused = harness.snapshot(list)
        val toOther = harness.validator.validate(ActionPlan(listOf(eggs), PlanSource.MOCK), unfocused).operations.single() as PlannedOperation.AddItem
        assertNull(toOther.masterItem)
        assertEquals(SectionTarget.ForCategory(harness.catalog.category("other").id), toOther.section)

        val vegetables = unfocused.sections.getValue("s2").sectionId
        val focused = harness.snapshot(list, focused = vegetables)
        val toFocused = harness.validator.validate(ActionPlan(listOf(eggs), PlanSource.MOCK), focused).operations.single() as PlannedOperation.AddItem
        assertEquals(SectionTarget.Existing(vegetables), toFocused.section)
    }

    @Test
    fun `an unknown canonical key falls back to the typed name`() = runTest {
        val op = accepted(call("addChecklistItem", "name" to "Saffron", "canonicalKey" to "saffron")).single() as PlannedOperation.AddItem
        assertNull(op.masterItem)
        assertEquals("Saffron", op.name)
    }

    @Test
    fun `quantity updates keep the item's unit when none is given`() = runTest {
        val op = accepted(call("updateItemQuantity", "itemRef" to "i1", "quantity" to "3")).single() as PlannedOperation.UpdateQuantity
        assertEquals(Quantity.of(3), op.quantity)
        assertNull(op.unit)
        assertEquals("Rice", op.itemName)
    }

    @Test
    fun `validation writes nothing`() = runTest {
        val list = harness.weeklyList()
        val before = harness.checklists.writes
        validate(
            call("addChecklistItem", "name" to "rice", "canonicalKey" to "rice"),
            call("deleteItem", "itemRef" to "i1"),
            call("removeCategory", "sectionRef" to "s2"),
            snapshot = harness.snapshot(list),
        )
        assertEquals(before, harness.checklists.writes)
        assertEquals(4, harness.checklists.detailNow(list)!!.totalItems)
    }
}
