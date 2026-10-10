package com.dataloom.checklist.ai.policy

import com.dataloom.checklist.ai.fixtures.AiTestHarness
import com.dataloom.checklist.ai.mapper.ValidatedPlan
import com.dataloom.checklist.ai.model.ActionPlan
import com.dataloom.checklist.ai.model.PlanSource
import com.dataloom.checklist.ai.model.ToolCall
import com.dataloom.checklist.ai.model.UnresolvedFragment
import com.dataloom.checklist.ai.model.UnresolvedReason
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Section 13: Read none; one simple step confirm (auto only if allowed); bulk and destructive always reviewed. */
class ConfirmationPolicyTest {

    private val harness = AiTestHarness()
    private val policy = ConfirmationPolicy()
    private val ask = AiSettings(enabled = true)
    private val auto = AiSettings(enabled = true, autoExecuteSimple = true)

    private suspend fun plan(vararg calls: ToolCall, unresolved: List<UnresolvedFragment> = emptyList()): ValidatedPlan {
        val snapshot = harness.snapshot(harness.weeklyList())
        return harness.validator.validate(ActionPlan(calls.toList(), PlanSource.OFFLINE_PARSER, unresolved), snapshot)
    }

    private val addSugar = ToolCall("addChecklistItem", mapOf("name" to "sugar", "canonicalKey" to "sugar", "quantity" to "1", "unit" to "KG"))
    private val tickOnion = ToolCall("completeItem", mapOf("itemRef" to "i4"))

    @Test
    fun `one simple step is confirmed, or runs directly when the user allowed it`() = runTest {
        assertEquals(ConfirmationRequirement.CONFIRM, policy.requirementFor(plan(addSugar), ask))
        assertEquals(ConfirmationRequirement.NONE, policy.requirementFor(plan(addSugar), auto))
        assertEquals(ConfirmationRequirement.CONFIRM, policy.requirementFor(plan(tickOnion), ask))
        assertEquals(ConfirmationRequirement.NONE, policy.requirementFor(plan(tickOnion), auto))
    }

    @Test
    fun `more than one step is always reviewed`() = runTest {
        assertEquals(ConfirmationRequirement.REVIEW, policy.requirementFor(plan(addSugar, tickOnion), auto))
    }

    @Test
    fun `destructive steps are always reviewed`() = runTest {
        assertEquals(ConfirmationRequirement.REVIEW, policy.requirementFor(plan(ToolCall("deleteItem", mapOf("itemRef" to "i3"))), auto))
        assertEquals(ConfirmationRequirement.REVIEW, policy.requirementFor(plan(ToolCall("removeCategory", mapOf("sectionRef" to "s2"))), auto))
    }

    @Test
    fun `a new checklist or a new section is always reviewed`() = runTest {
        assertEquals(ConfirmationRequirement.REVIEW, policy.requirementFor(plan(ToolCall("createChecklist", mapOf("title" to "Trip"))), auto))
        val banana = ToolCall("addChecklistItem", mapOf("name" to "banana", "canonicalKey" to "banana"))
        assertEquals(ConfirmationRequirement.REVIEW, policy.requirementFor(plan(banana), auto))
    }

    @Test
    fun `partly understood commands are always reviewed`() = runTest {
        val withRejected = plan(addSugar, ToolCall("deleteItem", mapOf("itemRef" to "i99")))
        assertEquals(ConfirmationRequirement.REVIEW, policy.requirementFor(withRejected, auto))
        val withUnresolved = plan(addSugar, unresolved = listOf(UnresolvedFragment("saffron", UnresolvedReason.NOT_UNDERSTOOD)))
        assertEquals(ConfirmationRequirement.REVIEW, policy.requirementFor(withUnresolved, auto))
    }
}
