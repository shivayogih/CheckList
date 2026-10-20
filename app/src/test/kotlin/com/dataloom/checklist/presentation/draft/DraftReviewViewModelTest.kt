package com.dataloom.checklist.presentation.draft

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.dataloom.checklist.R
import com.dataloom.checklist.ai.draft.ChecklistDraftBuilder
import com.dataloom.checklist.ai.draft.ChecklistDraftSaver
import com.dataloom.checklist.ai.draft.DraftMatcher
import com.dataloom.checklist.ai.draft.DraftTemplates
import com.dataloom.checklist.ai.mapper.CatalogLookup
import com.dataloom.checklist.ai.parser.LanguagePacks
import com.dataloom.checklist.domain.usecase.AddCustomItemUseCase
import com.dataloom.checklist.domain.usecase.AddMasterItemsToSectionUseCase
import com.dataloom.checklist.domain.usecase.CreateChecklistUseCase
import com.dataloom.checklist.domain.usecase.DeleteChecklistUseCase
import com.dataloom.checklist.domain.usecase.ObserveCategoriesUseCase
import com.dataloom.checklist.domain.usecase.ObserveChecklistDetailUseCase
import com.dataloom.checklist.domain.usecase.ObserveMasterItemsUseCase
import com.dataloom.checklist.domain.usecase.ObserveUnitsUseCase
import com.dataloom.checklist.domain.usecase.SearchMasterItemsUseCase
import com.dataloom.checklist.presentation.common.UiText
import com.dataloom.checklist.testing.FakeCatalogRepository
import com.dataloom.checklist.testing.FakeChecklistRepository
import com.dataloom.checklist.testing.FakeLanguageProvider
import com.dataloom.checklist.testing.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The Home draft flow: build, edit, approve. Nothing is written before Approve. */
class DraftReviewViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val catalog = FakeCatalogRepository()
    private val repo = FakeChecklistRepository(catalog)
    private val travel = catalog.seedCategory("travel", "Travel & Packing")

    init {
        catalog.seedMasterItem(travel.id, "tickets", "Tickets")
        catalog.seedMasterItem(travel.id, "hotel_booking", "Hotel Booking")
    }

    private fun viewModel(
        prompt: String = "Create checklist for my next Goa trip",
        savedState: SavedStateHandle = SavedStateHandle(),
    ) =
        DraftReviewViewModel(
            prompt = prompt,
            builder = ChecklistDraftBuilder(
                DraftMatcher(DraftTemplates.bundled()),
                CatalogLookup(
                    SearchMasterItemsUseCase(catalog),
                    ObserveCategoriesUseCase(catalog),
                    ObserveUnitsUseCase(catalog),
                ),
                ObserveMasterItemsUseCase(catalog),
                LanguagePacks.bundled(),
            ),
            saver = ChecklistDraftSaver(
                CreateChecklistUseCase(repo),
                AddMasterItemsToSectionUseCase(repo, catalog),
                AddCustomItemUseCase(repo, catalog),
                ObserveChecklistDetailUseCase(repo),
                DeleteChecklistUseCase(repo),
            ),
            languageProvider = FakeLanguageProvider("en"),
            applicationScope = CoroutineScope(mainDispatcher.dispatcher + SupervisorJob()),
            savedState = savedState,
        )

    @Test
    fun `a request becomes an editable draft and nothing is saved`() {
        val state = viewModel().uiState.value

        assertTrue(state.hasDraft)
        assertEquals("Goa trip", state.title)
        assertEquals(listOf("Tickets", "Hotel Booking"), state.sections.single().items.map { it.name })
        assertEquals(2, state.includedCount)
        assertTrue(state.canApprove)
        assertEquals(0, repo.checklistCount())
    }

    @Test
    fun `approve saves only what the user kept, added and named`() = runTest {
        val vm = viewModel()
        val section = vm.uiState.value.sections.single()

        vm.onAction(DraftReviewAction.TitleChanged("Goa, December"))
        vm.onAction(DraftReviewAction.ToggleItem(section.id, section.items[1].key, included = false))
        vm.onAction(DraftReviewAction.OpenAddItem(section.id))
        vm.onAction(DraftReviewAction.AddItemNameChanged("Sunglasses"))
        vm.onAction(DraftReviewAction.ConfirmAddItem)
        assertEquals(0, repo.checklistCount())

        vm.effect.test {
            vm.onAction(DraftReviewAction.Approve)
            val saved = awaitItem() as DraftReviewEffect.Saved
            val detail = repo.detail(saved.id)!!
            assertEquals("Goa, December", detail.checklist.title)
            assertEquals(listOf("Tickets", "Sunglasses"), detail.sections.single().items.map { it.displayName })
        }
    }

    @Test
    fun `a blank title or no ticked items cannot be approved`() {
        val vm = viewModel()
        vm.onAction(DraftReviewAction.TitleChanged("   "))
        assertFalse(vm.uiState.value.canApprove)
        vm.onAction(DraftReviewAction.Approve)
        assertEquals(0, repo.checklistCount())

        vm.onAction(DraftReviewAction.TitleChanged("Trip"))
        vm.onAction(DraftReviewAction.RemoveSection(vm.uiState.value.sections.single().id))
        assertEquals(0, vm.uiState.value.includedCount)
        assertFalse(vm.uiState.value.canApprove)
    }

    @Test
    fun `an item already in the group is refused`() {
        val vm = viewModel()
        val section = vm.uiState.value.sections.single()
        vm.onAction(DraftReviewAction.OpenAddItem(section.id))
        vm.onAction(DraftReviewAction.AddItemNameChanged("tickets"))
        vm.onAction(DraftReviewAction.ConfirmAddItem)

        assertEquals(UiText(R.string.draft_item_duplicate), vm.uiState.value.addItem?.error)
        assertEquals(2, vm.uiState.value.sections.single().items.size)
    }

    @Test
    fun `blank item names are refused`() {
        val vm = viewModel()
        vm.onAction(DraftReviewAction.OpenAddItem(vm.uiState.value.sections.single().id))
        vm.onAction(DraftReviewAction.AddItemNameChanged("   "))
        vm.onAction(DraftReviewAction.ConfirmAddItem)

        assertNotNull(vm.uiState.value.addItem?.error)
    }

    @Test
    fun `edits survive process death`() {
        val handle = SavedStateHandle()
        val first = viewModel(savedState = handle)
        val section = first.uiState.value.sections.single()
        first.onAction(DraftReviewAction.TitleChanged("Beach week"))
        first.onAction(DraftReviewAction.ToggleItem(section.id, section.items[0].key, included = false))

        val restored = viewModel(savedState = handle).uiState.value

        assertEquals("Beach week", restored.title)
        assertEquals(listOf(false, true), restored.sections.single().items.map { it.included })
    }

    @Test
    fun `a request that is not understood offers the manual way`() {
        val state = viewModel(prompt = "asdf qwerty").uiState.value

        assertFalse(state.hasDraft)
        assertEquals(UiText(R.string.draft_not_understood), state.problem)
        assertNull(state.sections.firstOrNull())
        assertEquals(0, repo.checklistCount())
    }
}
