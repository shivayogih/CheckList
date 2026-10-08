package com.dataloom.checklist.ai.service

import com.dataloom.checklist.ai.fixtures.AiTestHarness
import com.dataloom.checklist.ai.model.ActionPlan
import com.dataloom.checklist.ai.model.AiResult
import com.dataloom.checklist.ai.model.AiSummary
import com.dataloom.checklist.ai.model.CategorySuggestion
import com.dataloom.checklist.ai.model.ChecklistContext
import com.dataloom.checklist.ai.model.GenerateRequest
import com.dataloom.checklist.ai.model.ItemSuggestion
import com.dataloom.checklist.ai.model.ItemSuggestionContext
import com.dataloom.checklist.ai.model.PlanSource
import com.dataloom.checklist.ai.model.RejectionReason
import com.dataloom.checklist.ai.model.UnavailableReason
import com.dataloom.checklist.ai.model.Utterance
import com.dataloom.checklist.ai.policy.AiSettings
import com.dataloom.checklist.ai.policy.AiSettingsSource
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiServicesTest {

    private val harness = AiTestHarness()
    private val emptyContext = ChecklistContext("en", null, emptyList(), emptyList())
    private val openContext = emptyContext.copy(title = "Weekly")

    // --- MockAIService ------------------------------------------------------------------------

    @Test
    fun `the mock is deterministic and records what it was asked`() = runTest {
        val mock = MockAIService()
        val first = mock.interpret(Utterance("anything", "en"), openContext)
        val second = mock.interpret(Utterance("anything", "en"), openContext)
        assertEquals(first, second)
        mock.generateChecklist(GenerateRequest("Goa trip", "en", emptyList()))
        assertEquals(listOf("interpret:anything", "interpret:anything", "generate:Goa trip"), mock.requests)
    }

    @Test
    fun `the mock answers scripted utterances and needs a checklist otherwise`() = runTest {
        val scripted = AiResult.Rejected(RejectionReason.NOT_UNDERSTOOD)
        val mock = MockAIService(mapOf("hello" to scripted))
        assertEquals(scripted, mock.interpret(Utterance("  Hello ", "en"), openContext))
        assertEquals(AiResult.Rejected(RejectionReason.NEEDS_CHECKLIST), mock.interpret(Utterance("2 kg rice", "en"), emptyContext))
    }

    @Test
    fun `the mock's canned plans pass validation against the real catalog`() = runTest {
        val mock = MockAIService()
        val list = harness.snapshot(harness.weeklyList())
        val interpreted = (mock.interpret(Utterance("x", "en"), list.context) as AiResult.Success).value
        assertEquals(PlanSource.MOCK, interpreted.source)
        assertTrue(harness.validator.validate(interpreted, list).rejected.isEmpty())
        val generated = (mock.generateChecklist(GenerateRequest("Goa trip", "en", emptyList())) as AiResult.Success).value
        assertTrue(harness.validator.validate(generated, harness.snapshot(null)).rejected.isEmpty())
    }

    // --- CompositeAIService -------------------------------------------------------------------

    private class FakeOnline(private val answer: suspend () -> AiResult<ActionPlan>) : AIService {
        var calls = 0

        override suspend fun interpret(utterance: Utterance, context: ChecklistContext): AiResult<ActionPlan> {
            calls++
            return answer()
        }

        override suspend fun generateChecklist(request: GenerateRequest): AiResult<ActionPlan> {
            calls++
            return answer()
        }

        override suspend fun suggestCategories(title: String, locale: String): AiResult<List<CategorySuggestion>> =
            AiResult.Success(emptyList())

        override suspend fun suggestItems(context: ItemSuggestionContext): AiResult<List<ItemSuggestion>> = AiResult.Success(emptyList())

        override suspend fun summarize(context: ChecklistContext): AiResult<AiSummary> = AiResult.Success(AiSummary(0, 0, emptyList()))
    }

    private val onlinePlan = AiResult.Success(ActionPlan(emptyList(), PlanSource.ONLINE_MODEL))

    private fun composite(settings: AiSettings, online: AIService?, offline: AIService = MockAIService()) =
        CompositeAIService(offline, online, AiSettingsSource { settings }, onlineTimeoutMillis = 1_000)

    @Test
    fun `when AI is off nothing is called`() = runTest {
        val offline = MockAIService()
        val online = FakeOnline { onlinePlan }
        val service = composite(AiSettings(enabled = false, onlineEnabled = true), online, offline)
        assertEquals(AiResult.Unavailable(UnavailableReason.DISABLED), service.interpret(Utterance("rice", "en"), openContext))
        assertEquals(AiResult.Unavailable(UnavailableReason.DISABLED), service.generateChecklist(GenerateRequest("x", "en", emptyList())))
        assertTrue(offline.requests.isEmpty())
        assertEquals(0, online.calls)
    }

    @Test
    fun `the offline parser answers first, online only fills in when allowed`() = runTest {
        val online = FakeOnline { onlinePlan }
        val parserFirst = composite(AiSettings(enabled = true, onlineEnabled = true), online)
        assertEquals(PlanSource.MOCK, (parserFirst.interpret(Utterance("rice", "en"), openContext) as AiResult.Success).value.source)
        assertEquals(0, online.calls)

        // The offline side cannot help (no checklist): online is asked only when the user enabled it.
        assertEquals(onlinePlan, parserFirst.interpret(Utterance("rice", "en"), emptyContext))
        val offlineOnly = composite(AiSettings(enabled = true, onlineEnabled = false), online)
        assertEquals(AiResult.Rejected(RejectionReason.NEEDS_CHECKLIST), offlineOnly.interpret(Utterance("rice", "en"), emptyContext))
        assertEquals(1, online.calls)
    }

    @Test
    fun `without an online service, online-only features are not supported offline`() = runTest {
        val service = composite(AiSettings(enabled = true, onlineEnabled = true), online = null, offline = harness.parser)
        assertEquals(AiResult.Unavailable(UnavailableReason.NOT_SUPPORTED), service.generateChecklist(GenerateRequest("x", "en", emptyList())))
    }

    @Test
    fun `slow or failing online calls become friendly results`() = runTest {
        val enabled = AiSettings(enabled = true, onlineEnabled = true)
        val slow = composite(enabled, FakeOnline { awaitCancellation() })
        assertEquals(AiResult.Unavailable(UnavailableReason.TIMEOUT), slow.generateChecklist(GenerateRequest("x", "en", emptyList())))

        val broken = composite(enabled, FakeOnline { error("boom") })
        assertTrue(broken.generateChecklist(GenerateRequest("x", "en", emptyList())) is AiResult.Error)
    }
}
