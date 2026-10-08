package com.dataloom.checklist.presentation.ai

import app.cash.turbine.test
import com.dataloom.checklist.R
import com.dataloom.checklist.ai.AiAssistant
import com.dataloom.checklist.ai.executor.ActionPlanExecutor
import com.dataloom.checklist.ai.mapper.CatalogLookup
import com.dataloom.checklist.ai.mapper.PlanValidator
import com.dataloom.checklist.ai.model.ActionPlan
import com.dataloom.checklist.ai.model.AiResult
import com.dataloom.checklist.ai.model.PlanSource
import com.dataloom.checklist.ai.model.RejectionReason
import com.dataloom.checklist.ai.model.ToolCall
import com.dataloom.checklist.ai.model.UnavailableReason
import com.dataloom.checklist.ai.model.UnresolvedFragment
import com.dataloom.checklist.ai.model.UnresolvedReason
import com.dataloom.checklist.ai.parser.CommandGrammar
import com.dataloom.checklist.ai.parser.LanguagePacks
import com.dataloom.checklist.ai.parser.OfflineCommandParser
import com.dataloom.checklist.ai.policy.AiSettings
import com.dataloom.checklist.ai.policy.ConfirmationPolicy
import com.dataloom.checklist.ai.service.AIService
import com.dataloom.checklist.ai.service.CompositeAIService
import com.dataloom.checklist.ai.service.MockAIService
import com.dataloom.checklist.ai.tools.AiTool
import com.dataloom.checklist.domain.model.BuiltInUnits
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.NewChecklistItem
import com.dataloom.checklist.domain.model.Quantity
import com.dataloom.checklist.domain.model.SectionId
import com.dataloom.checklist.domain.usecase.AddCategoriesToChecklistUseCase
import com.dataloom.checklist.domain.usecase.AddCustomItemUseCase
import com.dataloom.checklist.domain.usecase.AddMasterItemsToSectionUseCase
import com.dataloom.checklist.domain.usecase.CreateChecklistUseCase
import com.dataloom.checklist.domain.usecase.DeleteChecklistItemUseCase
import com.dataloom.checklist.domain.usecase.ObserveCategoriesUseCase
import com.dataloom.checklist.domain.usecase.ObserveChecklistDetailUseCase
import com.dataloom.checklist.domain.usecase.ObserveUnitsUseCase
import com.dataloom.checklist.domain.usecase.RemoveSectionUseCase
import com.dataloom.checklist.domain.usecase.SearchMasterItemsUseCase
import com.dataloom.checklist.domain.usecase.SetItemCompletedUseCase
import com.dataloom.checklist.domain.usecase.UpdateChecklistItemUseCase
import com.dataloom.checklist.presentation.common.UiText
import com.dataloom.checklist.testing.FakeAiPreferences
import com.dataloom.checklist.testing.FakeCatalogRepository
import com.dataloom.checklist.testing.FakeChecklistRepository
import com.dataloom.checklist.testing.FakeLanguageProvider
import com.dataloom.checklist.testing.MainDispatcherRule
import com.dataloom.checklist.testing.keepCollecting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The command field end to end: real validator, policy, gate and executor over fake repositories, with a
 * scripted [MockAIService] (or the real offline parser) behind the same [CompositeAIService] the app binds,
 * so the Settings toggle really gates the call.
 */
class AiCommandViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val catalog = FakeCatalogRepository()
    private val repo = FakeChecklistRepository(catalog)
    private val groceries = catalog.seedCategory("groceries", "Groceries")
    private val preferences = FakeAiPreferences(AiSettings(enabled = true))
    private var checklistId = ChecklistId("")
    private var groceriesSection = SectionId("")

    private fun addItem(key: String, name: String, quantity: String, unit: String) = ToolCall(
        AiTool.ADD_CHECKLIST_ITEM.toolName,
        mapOf("name" to name, "canonicalKey" to key, "quantity" to quantity, "unit" to unit),
    )

    private val scripted: Map<String, AiResult<ActionPlan>> = mapOf(
        "1 kg rice" to AiResult.Success(ActionPlan(listOf(addItem("rice", "rice", "1", "KG")), PlanSource.MOCK)),
        "slow" to AiResult.Unavailable(UnavailableReason.TIMEOUT),
        "gibberish" to AiResult.Rejected(RejectionReason.NOT_UNDERSTOOD),
        "blah blah" to AiResult.Success(
            ActionPlan(emptyList(), PlanSource.MOCK, listOf(UnresolvedFragment("blah blah", UnresolvedReason.NOT_UNDERSTOOD))),
        ),
    )

    private suspend fun seed(riceName: String = "Rice", milkName: String = "Milk") {
        catalog.seedMasterItem(groceries.id, "rice", riceName, BuiltInUnits.KG.code)
        catalog.seedMasterItem(groceries.id, "milk", milkName, BuiltInUnits.LITRE.code)
        checklistId = repo.createChecklist("Weekly shopping", null, listOf(groceries.id))
        groceriesSection = repo.detail(checklistId)!!.sections.single().id
        repo.addItems(groceriesSection, listOf(NewChecklistItem(null, null, "Salt", "en", null, null, null)))
        repo.addItemsCalls.clear()
    }

    private fun TestScope.viewModel(service: AIService = MockAIService(scripted), language: String = "en"): AiCommandViewModel {
        val lookup = CatalogLookup(SearchMasterItemsUseCase(catalog), ObserveCategoriesUseCase(catalog), ObserveUnitsUseCase(catalog))
        val observeDetail = ObserveChecklistDetailUseCase(repo)
        val executor = ActionPlanExecutor(
            CreateChecklistUseCase(repo),
            AddCategoriesToChecklistUseCase(repo),
            AddMasterItemsToSectionUseCase(repo, catalog),
            AddCustomItemUseCase(repo, catalog),
            UpdateChecklistItemUseCase(repo, catalog),
            SetItemCompletedUseCase(repo),
            RemoveSectionUseCase(repo),
            DeleteChecklistItemUseCase(repo),
            observeDetail,
        )
        val assistant = AiAssistant(
            CompositeAIService(service, online = null, settings = preferences),
            PlanValidator(lookup),
            ConfirmationPolicy(),
            executor,
            preferences,
            observeDetail,
            ObserveUnitsUseCase(catalog),
        )
        val vm = AiCommandViewModel(
            checklistId = checklistId.value,
            assistant = assistant,
            preferences = preferences,
            languageProvider = FakeLanguageProvider(language),
            applicationScope = CoroutineScope(mainDispatcher.dispatcher + SupervisorJob()),
        )
        keepCollecting(vm.uiState)
        return vm
    }

    private fun AiCommandViewModel.submit(command: String) {
        onAction(AiCommandAction.CommandChanged(command))
        onAction(AiCommandAction.Submit)
    }

    private fun AiCommandViewModel.review(): AiReviewUi {
        val review = uiState.value.review
        assertNotNull("The review sheet should be open", review)
        return review!!
    }

    private fun items() = repo.detail(checklistId)!!.sections.flatMap { it.items }

    private fun itemNames() = items().map { it.displayName }

    private fun message(vararg lines: UiText, problem: Boolean) = AiMessageUi(lines.toList(), isProblem = problem)

    @Test
    fun `the command field shows only while the assistant is on`() = runTest {
        seed()
        preferences.setEnabled(false)
        val vm = viewModel()

        vm.uiState.test {
            assertFalse(awaitItem().isAvailable)
            preferences.setEnabled(true)
            assertTrue(awaitItem().isAvailable)
            preferences.setEnabled(false)
            assertFalse(awaitItem().isAvailable)
        }
    }

    @Test
    fun `a command is shown for review with item, quantity and unit, and nothing is written yet`() = runTest {
        seed()
        val vm = viewModel()

        vm.uiState.test {
            assertEquals(AiCommandUiState(isAvailable = true), awaitItem())
            vm.onAction(AiCommandAction.CommandChanged("2 kg rice and 1 litre milk"))
            assertTrue(awaitItem().canSubmit)
            vm.onAction(AiCommandAction.Submit)

            // Turbine sees "working" and then the review (conflation may merge them).
            var state = awaitItem()
            while (state.review == null) state = awaitItem()
            assertFalse(state.isWorking)
            assertEquals(
                listOf(
                    AiStepUi(0, AiStepKind.ADD_ITEM, "Rice", Quantity.of(2), BuiltInUnits.KG.code, "Groceries"),
                    AiStepUi(1, AiStepKind.ADD_ITEM, "Milk", Quantity.of(1), BuiltInUnits.LITRE.code, "Groceries"),
                ),
                state.review!!.steps,
            )
            assertTrue(state.review!!.notUnderstood.isEmpty())
            assertFalse("No second command while reviewing", state.canSubmit)
        }
        assertEquals(listOf("Salt"), itemNames())
        assertTrue(repo.addItemsCalls.isEmpty())
    }

    @Test
    fun `cancel closes the review and writes nothing, and the plan cannot run later`() = runTest {
        seed()
        val vm = viewModel()
        val before = repo.detail(checklistId)

        vm.submit("2 kg rice and 1 litre milk")
        vm.review()
        vm.onAction(AiCommandAction.Cancel)

        assertNull(vm.uiState.value.review)
        assertEquals("The command stays so it can be edited", "2 kg rice and 1 litre milk", vm.uiState.value.command)
        // A Confirm arriving after Cancel (double tap) must not run the dropped plan.
        vm.onAction(AiCommandAction.Confirm)
        assertEquals(before, repo.detail(checklistId))
        assertTrue(repo.addItemsCalls.isEmpty())
        assertTrue(repo.updates.isEmpty())
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun `confirm runs only the ticked steps and says what ran`() = runTest {
        seed()
        val vm = viewModel()
        vm.submit("2 kg rice and 1 litre milk")
        val milk = vm.review().steps.single { it.name == "Milk" }

        vm.onAction(AiCommandAction.ToggleStep(milk.callIndex, checked = false))
        assertFalse(vm.review().steps.single { it.name == "Milk" }.checked)
        vm.onAction(AiCommandAction.Confirm)

        assertEquals(listOf("Salt", "Rice"), itemNames())
        val rice = items().single { it.displayName == "Rice" }
        assertEquals(Quantity.of(2), rice.quantity)
        assertEquals(BuiltInUnits.KG.code, rice.unit)
        val state = vm.uiState.value
        assertNull(state.review)
        assertFalse(state.isWorking)
        assertEquals("", state.command)
        assertEquals(message(UiText(R.string.ai_result_done, listOf("Rice")), problem = false), state.message)
    }

    @Test
    fun `confirm is not possible with every step unticked`() = runTest {
        seed()
        val vm = viewModel()
        vm.submit("2 kg rice and 1 litre milk")
        vm.review().steps.forEach { vm.onAction(AiCommandAction.ToggleStep(it.callIndex, checked = false)) }

        assertFalse(vm.review().canConfirm)
        vm.onAction(AiCommandAction.Confirm)

        assertNotNull("The sheet stays open", vm.uiState.value.review)
        assertTrue(repo.addItemsCalls.isEmpty())
    }

    @Test
    fun `steps that fail are reported, with the steps after them`() = runTest {
        seed()
        val vm = viewModel()
        vm.submit("2 kg rice and 1 litre milk")
        vm.review()
        // The section goes away while the sheet is open (for example from another screen).
        repo.removeSection(groceriesSection)

        vm.onAction(AiCommandAction.Confirm)

        assertEquals(message(UiText(R.string.ai_result_failed, listOf("Rice, Milk")), problem = true), vm.uiState.value.message)
    }

    @Test
    fun `items already on the list are reported separately`() = runTest {
        seed()
        val rice = catalog.allMasterItems().single { it.canonicalKey == "rice" }
        repo.addItems(groceriesSection, listOf(NewChecklistItem(rice.id, "rice", "Rice", "en", null, null, null)))
        val vm = viewModel()
        vm.submit("2 kg rice and 1 litre milk")

        vm.onAction(AiCommandAction.Confirm)

        assertEquals(
            message(UiText(R.string.ai_result_done, listOf("Milk")), UiText(R.string.ai_result_already, listOf("Rice")), problem = false),
            vm.uiState.value.message,
        )
        assertEquals(1, itemNames().count { it == "Rice" })
    }

    @Test
    fun `assistant switched off gives the disabled message`() = runTest {
        seed()
        val service = MockAIService(scripted)
        val vm = viewModel(service)
        // Switched off in another window after the field was shown.
        preferences.setEnabled(false)

        vm.submit("2 kg rice and 1 litre milk")

        assertEquals(message(UiText(R.string.ai_unavailable_disabled), problem = true), vm.uiState.value.message)
        assertTrue("The service is not even asked", service.requests.isEmpty())
        assertTrue(repo.addItemsCalls.isEmpty())
    }

    @Test
    fun `timeout and not understood give their own messages`() = runTest {
        seed()
        val vm = viewModel()

        vm.submit("slow")
        assertEquals(message(UiText(R.string.ai_unavailable_timeout), problem = true), vm.uiState.value.message)
        assertFalse(vm.uiState.value.isWorking)

        vm.submit("gibberish")
        assertEquals(message(UiText(R.string.ai_not_understood), problem = true), vm.uiState.value.message)

        // A plan with no steps at all is "not understood" too, not an empty review sheet.
        vm.submit("blah blah")
        assertEquals(message(UiText(R.string.ai_not_understood), problem = true), vm.uiState.value.message)
        assertNull(vm.uiState.value.review)
        assertTrue(repo.addItemsCalls.isEmpty())
    }

    @Test
    fun `a blank command asks for text without calling the assistant`() = runTest {
        seed()
        val service = MockAIService(scripted)
        val vm = viewModel(service)

        vm.submit("   ")

        assertFalse(vm.uiState.value.canSubmit)
        assertEquals(message(UiText(R.string.ai_empty_command), problem = true), vm.uiState.value.message)
        assertTrue(service.requests.isEmpty())
        vm.onAction(AiCommandAction.DismissMessage)
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun `one simple step still asks first unless add without asking is on`() = runTest {
        seed()
        val vm = viewModel()

        vm.submit("1 kg rice")
        assertEquals(1, vm.review().steps.size)
        assertTrue(repo.addItemsCalls.isEmpty())
        vm.onAction(AiCommandAction.Cancel)

        preferences.setAutoExecuteSimple(true)
        vm.submit("1 kg rice")

        assertNull("Ran without the sheet", vm.uiState.value.review)
        assertEquals(listOf("Salt", "Rice"), itemNames())
        assertEquals(message(UiText(R.string.ai_result_done, listOf("Rice")), problem = false), vm.uiState.value.message)
    }

    @Test
    fun `add without asking never skips review for more than one step`() = runTest {
        seed()
        preferences.setAutoExecuteSimple(true)
        val vm = viewModel()

        vm.submit("2 kg rice and 1 litre milk")

        assertEquals(2, vm.review().steps.size)
        assertTrue(repo.addItemsCalls.isEmpty())
    }

    @Test
    fun `a Hindi command through the offline parser is reviewed in Hindi`() = runTest {
        // The fake catalog has one name per item; here they are the Hindi names, as Room would resolve them.
        seed(riceName = "चावल", milkName = "दूध")
        val parser = OfflineCommandParser(CommandGrammar(LanguagePacks.bundled()), CatalogLookup(
            SearchMasterItemsUseCase(catalog),
            ObserveCategoriesUseCase(catalog),
            ObserveUnitsUseCase(catalog),
        ))
        val vm = viewModel(parser, language = "hi")

        vm.submit("दो किलो चावल और एक लीटर दूध")

        assertEquals(
            listOf(
                AiStepUi(0, AiStepKind.ADD_ITEM, "चावल", Quantity.of(2), BuiltInUnits.KG.code, "Groceries"),
                AiStepUi(1, AiStepKind.ADD_ITEM, "दूध", Quantity.of(1), BuiltInUnits.LITRE.code, "Groceries"),
            ),
            vm.review().steps,
        )
        vm.onAction(AiCommandAction.Confirm)
        assertEquals(listOf("Salt", "चावल", "दूध"), itemNames())
    }
}
