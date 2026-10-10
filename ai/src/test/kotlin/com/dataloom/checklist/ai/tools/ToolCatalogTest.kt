package com.dataloom.checklist.ai.tools

import com.dataloom.checklist.ai.fixtures.AiTestHarness
import com.dataloom.checklist.ai.mapper.ToolProblem
import com.dataloom.checklist.ai.model.ActionPlan
import com.dataloom.checklist.ai.model.AiResult
import com.dataloom.checklist.ai.model.PlanSource
import com.dataloom.checklist.ai.model.ToolCall
import com.dataloom.checklist.domain.model.BuiltInUnits
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract between the declared tools, the validator and the read runner (section 13): a tool cannot
 * be declared to a model without being handled.
 */
class ToolCatalogTest {

    private val harness = AiTestHarness()

    /** A plausible value for each parameter type, using refs of the weekly list. */
    private fun sample(param: ToolParam): Any = when (param.type) {
        ParamType.STRING -> when (param.name) {
            "categoryKey" -> "fruits"
            "title" -> "Trip"
            else -> "rice"
        }
        ParamType.NUMBER -> "2"
        ParamType.UNIT -> "KG"
        ParamType.STRING_LIST -> listOf("travel")
        ParamType.SECTION_REF -> "s1"
        ParamType.ITEM_REF -> "i1"
    }

    @Test
    fun `the section 13 tools are all declared, with unique names`() {
        val expected = setOf(
            "searchMasterItems", "suggestCategories", "suggestItems", "summarizeChecklist", "createChecklist",
            "addCategory", "addChecklistItem", "updateItemQuantity", "updateItemUnit", "completeItem",
            "uncompleteItem", "removeCategory", "deleteItem",
        )
        assertEquals(expected, AiTool.entries.map { it.toolName }.toSet())
        assertEquals(AiTool.entries.size, expected.size)
    }

    @Test
    fun `risk classes match the design`() {
        val byRisk = AiTool.entries.groupBy({ it.risk }, { it.toolName })
        assertEquals(setOf("removeCategory", "deleteItem"), byRisk.getValue(RiskClass.DESTRUCTIVE).toSet())
        assertEquals(setOf("searchMasterItems", "suggestCategories", "suggestItems", "summarizeChecklist"), byRisk.getValue(RiskClass.READ).toSet())
    }

    @Test
    fun `declarations mirror the tools and limit units to the allowed codes`() {
        val units = BuiltInUnits.all.map { it.code }
        val declarations = ToolCatalog.declarations(units)
        assertEquals(AiTool.entries.map { it.toolName }, declarations.map { it.name })
        declarations.forEach { declaration ->
            val tool = AiTool.byName(declaration.name)!!
            assertEquals(tool.params.map { it.name to it.required }, declaration.parameters.map { it.name to it.required })
        }
        val unit = declarations.first { it.name == "addChecklistItem" }.parameters.first { it.name == "unit" }
        assertEquals(units.map { it.value }, unit.enumValues)
        assertEquals(SchemaType.NUMBER, declarations.first { it.name == "addChecklistItem" }.parameters.first { it.name == "quantity" }.type)
    }

    @Test
    fun `every write tool is understood by the validator`() = runTest {
        val snapshot = harness.snapshot(harness.weeklyList())
        AiTool.entries.filter { it.risk != RiskClass.READ }.forEach { tool ->
            val args = tool.params.associate { it.name to sample(it) }
            val plan = harness.validator.validate(ActionPlan(listOf(ToolCall(tool.toolName, args)), PlanSource.ONLINE_MODEL), snapshot)
            val problems = plan.rejected.flatMap { it.problems }
            assertTrue("${tool.toolName}: $problems", ToolProblem.UnknownTool !in problems && ToolProblem.NotAPlanStep !in problems)
            assertTrue("${tool.toolName}: shape problems $problems", problems.none { it is ToolProblem.MissingArgument || it is ToolProblem.WrongType || it is ToolProblem.UnexpectedArgument })
        }
    }

    @Test
    fun `every read tool is answered by the read runner and refused as a plan step`() = runTest {
        val snapshot = harness.snapshot(harness.weeklyList())
        AiTool.entries.filter { it.risk == RiskClass.READ }.forEach { tool ->
            val call = ToolCall(tool.toolName, tool.params.associate { it.name to sample(it) })
            assertTrue(tool.toolName, harness.readTools.run(call, snapshot.context) is AiResult.Success)
            val plan = harness.validator.validate(ActionPlan(listOf(call), PlanSource.ONLINE_MODEL), snapshot)
            assertEquals(listOf(ToolProblem.NotAPlanStep), plan.rejected.single().problems)
        }
    }

    @Test
    fun `the read runner refuses write tools and answers with keys and names only`() = runTest {
        val snapshot = harness.snapshot(harness.weeklyList(), "kn")
        assertTrue(harness.readTools.run(ToolCall("deleteItem", mapOf("itemRef" to "i1")), snapshot.context) is AiResult.Rejected)
        val found = harness.readTools.run(ToolCall("searchMasterItems", mapOf("query" to "akki")), snapshot.context) as AiResult.Success
        val items = (found.value as ReadToolResult.Items).items
        assertEquals("rice", items.first().canonicalKey)
        assertEquals("ಅಕ್ಕಿ", items.first().name)
        val summary = harness.readTools.run(ToolCall("summarizeChecklist"), snapshot.context) as AiResult.Success
        assertEquals(4, (summary.value as ReadToolResult.Summary).summary.totalItems)
    }
}
