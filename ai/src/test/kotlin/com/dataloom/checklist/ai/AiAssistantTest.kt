package com.dataloom.checklist.ai

import com.dataloom.checklist.ai.executor.OperationOutcome
import com.dataloom.checklist.ai.fixtures.AiTestHarness
import com.dataloom.checklist.ai.mapper.PlannedOperation
import com.dataloom.checklist.ai.model.AiResult
import com.dataloom.checklist.ai.model.UnavailableReason
import com.dataloom.checklist.ai.model.Utterance
import com.dataloom.checklist.ai.policy.AiSettings
import com.dataloom.checklist.ai.policy.AiSettingsSource
import com.dataloom.checklist.ai.policy.ConfirmationRequirement
import com.dataloom.checklist.ai.policy.ReviewedPlan
import com.dataloom.checklist.ai.service.CompositeAIService
import com.dataloom.checklist.ai.service.MockAIService
import com.dataloom.checklist.domain.model.BuiltInUnits
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.Quantity
import com.dataloom.checklist.domain.usecase.DomainError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * End to end through the real use cases: text -> plan -> review -> confirmation -> execution.
 * The core guarantee: nothing is written until a plan is confirmed.
 */
class AiAssistantTest {

    private suspend fun AiTestHarness.reviewed(text: String, list: ChecklistId?, locale: String = "en"): ReviewedPlan {
        val result = assistant.interpret(Utterance(text, locale), snapshot(list, locale))
        return (result as? AiResult.Success)?.value ?: error("'$text' gave $result")
    }

    private fun AiTestHarness.itemNames(list: ChecklistId) =
        checklists.detailNow(list)!!.sections.associate { section -> section.category.canonicalKey to section.items.map { it.displayName } }

    @Test
    fun `a proposal writes nothing until it is confirmed`() = runTest {
        val harness = AiTestHarness()
        val list = harness.weeklyList()
        val before = harness.checklists.writes

        val plan = harness.reviewed("2 kg sugar, 1 dozen eggs and dal", list)
        assertEquals(ConfirmationRequirement.REVIEW, plan.requirement)
        assertEquals(3, plan.operations.size)
        assertEquals(before, harness.checklists.writes)
        assertNull("bulk plans are never auto-approved", harness.assistant.autoApprove(plan))
        assertEquals(before, harness.checklists.writes)

        val report = harness.assistant.execute(harness.assistant.confirm(plan))
        assertTrue(report.succeeded)
        val items = harness.itemNames(list)
        assertEquals(listOf("Rice", "Milk", "Ghee", "Sugar", "Dal"), items["groceries"])
        assertEquals(listOf("eggs"), items["other"])
        val sugar = harness.checklists.detailNow(list)!!.sections.flatMap { it.items }.first { it.displayName == "Sugar" }
        assertEquals(Quantity.of(2), sugar.quantity)
        assertEquals(BuiltInUnits.KG.code, sugar.unit)
    }

    @Test
    fun `a simple step needs a tap unless the user allowed AI to act alone`() = runTest {
        val asking = AiTestHarness()
        val list = asking.weeklyList()
        val plan = asking.reviewed("mark onion done", list)
        assertEquals(ConfirmationRequirement.CONFIRM, plan.requirement)
        assertNull(asking.assistant.autoApprove(plan))

        val trusting = AiTestHarness(AiSettings(enabled = true, autoExecuteSimple = true))
        val trustedList = trusting.weeklyList()
        val trustedPlan = trusting.reviewed("mark onion done", trustedList)
        val approved = trusting.assistant.autoApprove(trustedPlan)
        assertNotNull(approved)
        trusting.assistant.execute(approved!!)
        assertTrue(trusting.checklists.detailNow(trustedList)!!.sections[1].items.single().isCompleted)
    }

    @Test
    fun `a destructive step is never auto-approved`() = runTest {
        val harness = AiTestHarness(AiSettings(enabled = true, autoExecuteSimple = true))
        val list = harness.weeklyList()
        val plan = harness.reviewed("remove ghee", list)
        assertEquals(ConfirmationRequirement.REVIEW, plan.requirement)
        assertNull(harness.assistant.autoApprove(plan))
        assertTrue("Ghee" in harness.itemNames(list)["groceries"]!!)
    }

    @Test
    fun `a confirmed plan runs once`() = runTest {
        val harness = AiTestHarness()
        val list = harness.weeklyList()
        val confirmed = harness.assistant.confirm(harness.reviewed("2 kg sugar", list))
        harness.assistant.execute(confirmed)
        try {
            harness.assistant.execute(confirmed)
            fail("A second execution must fail")
        } catch (expected: IllegalStateException) {
            // Expected: replaying a confirmation would add the items twice.
        }
        assertEquals(1, harness.itemNames(list)["groceries"]!!.count { it == "Sugar" })
    }

    @Test
    fun `unticked steps are not executed`() = runTest {
        val harness = AiTestHarness()
        val list = harness.weeklyList()
        val plan = harness.reviewed("2 kg sugar and dal", list)
        harness.assistant.execute(harness.assistant.confirm(plan, excludedCallIndexes = setOf(1)))
        assertEquals(listOf("Rice", "Milk", "Ghee", "Sugar"), harness.itemNames(list)["groceries"])
    }

    @Test
    fun `nothing can be confirmed when nothing is left`() = runTest {
        val harness = AiTestHarness()
        val list = harness.weeklyList()
        val plan = harness.reviewed("2 kg sugar", list)
        try {
            harness.assistant.confirm(plan, excludedCallIndexes = setOf(0))
            fail("Confirming an empty selection must fail")
        } catch (expected: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun `AI is off by default and then does nothing`() = runTest {
        val harness = AiTestHarness()
        val offByDefault = CompositeAIService(harness.parser, null, AiSettingsSource.DISABLED)
        val list = harness.weeklyList()
        val before = harness.checklists.writes
        val result = offByDefault.interpret(Utterance("2 kg rice", "en"), harness.snapshot(list).context)
        assertEquals(AiResult.Unavailable(UnavailableReason.DISABLED), result)
        assertEquals(before, harness.checklists.writes)
        assertEquals(AiSettings(), AiSettingsSource.DISABLED.current())
    }

    @Test
    fun `steps after a refused step are skipped`() = runTest {
        val harness = AiTestHarness()
        val list = harness.weeklyList()
        val plan = harness.reviewed("change ghee to 2 kg", list)
        // The item is deleted on another screen before the user confirms.
        val ghee = harness.checklists.detailNow(list)!!.sections[0].items.first { it.displayName == "Ghee" }
        harness.checklists.deleteItem(ghee.id)
        val report = harness.assistant.execute(harness.assistant.confirm(plan))
        assertEquals(OperationOutcome.Failed(DomainError.NotFound), report.results.single().outcome)
    }

    @Test
    fun `use case rules still apply at execution time`() = runTest {
        val harness = AiTestHarness()
        val list = harness.weeklyList()
        // Rice is already in Groceries: the domain answers "already present", nothing is duplicated.
        val report = harness.assistant.execute(harness.assistant.confirm(harness.reviewed("rice", list)))
        assertEquals(OperationOutcome.AlreadyPresent, report.results.single().outcome)
        assertEquals(1, harness.itemNames(list)["groceries"]!!.count { it == "Rice" })
    }

    @Test
    fun `updating an amount keeps the notes, which the AI never saw`() = runTest {
        val harness = AiTestHarness()
        val list = harness.weeklyList()
        val snapshot = harness.snapshot(list)
        assertTrue("notes must not reach the AI", "private note" !in snapshot.context.toString())
        harness.assistant.execute(harness.assistant.confirm(harness.reviewed("change ghee to 2 kg", list)))
        val ghee = harness.checklists.detailNow(list)!!.sections[0].items.first { it.displayName == "Ghee" }
        assertEquals("private note", ghee.notes)
        assertEquals(Quantity.of(2), ghee.quantity)
        assertEquals(BuiltInUnits.KG.code, ghee.unit)
    }

    @Test
    fun `create a checklist from Home in Kannada`() = runTest {
        val harness = AiTestHarness()
        val plan = harness.reviewed("ದೀಪಾವಳಿ ಪಟ್ಟಿ ಮಾಡಿ", null, "kn")
        assertEquals(ConfirmationRequirement.REVIEW, plan.requirement)
        val report = harness.assistant.execute(harness.assistant.confirm(plan))
        assertEquals("ದೀಪಾವಳಿ", harness.checklists.detailNow(report.createdChecklistId!!)!!.checklist.title)
    }

    @Test
    fun `a generated checklist is created with its items in one confirmed plan`() = runTest {
        val harness = AiTestHarness(serviceOverride = MockAIService())
        val result = harness.assistant.generate("Goa trip", harness.snapshot(null)) as AiResult.Success
        val plan = result.value
        assertEquals(listOf(PlannedOperation.CreateChecklist::class, PlannedOperation.AddItem::class, PlannedOperation.AddItem::class, PlannedOperation.AddItem::class), plan.operations.map { it::class })
        assertEquals(0, harness.checklists.checklistCount())

        val report = harness.assistant.execute(harness.assistant.confirm(plan))
        val created = harness.itemNames(report.createdChecklistId!!)
        assertEquals(listOf("Rice", "Dal", "Milk"), created["groceries"])
    }

    @Test
    fun `unticking the new checklist drops the items meant for it`() = runTest {
        val harness = AiTestHarness(serviceOverride = MockAIService())
        val plan = (harness.assistant.generate("Goa trip", harness.snapshot(null)) as AiResult.Success).value
        try {
            harness.assistant.confirm(plan, excludedCallIndexes = setOf(0))
            fail("Items without their checklist must not run")
        } catch (expected: IllegalArgumentException) {
            // Expected.
        }
        assertEquals(0, harness.checklists.checklistCount())
    }
}
