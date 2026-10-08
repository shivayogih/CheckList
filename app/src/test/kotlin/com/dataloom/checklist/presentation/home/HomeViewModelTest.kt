package com.dataloom.checklist.presentation.home

import app.cash.turbine.test
import com.dataloom.checklist.domain.model.ChecklistFilter
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.NewChecklistItem
import com.dataloom.checklist.domain.usecase.ArchiveChecklistUseCase
import com.dataloom.checklist.domain.usecase.DeleteChecklistUseCase
import com.dataloom.checklist.domain.usecase.DuplicateChecklistUseCase
import com.dataloom.checklist.domain.usecase.ObserveChecklistsUseCase
import com.dataloom.checklist.domain.usecase.UnarchiveChecklistUseCase
import com.dataloom.checklist.testing.FakeCatalogRepository
import com.dataloom.checklist.testing.FakeChecklistRepository
import com.dataloom.checklist.testing.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val catalog = FakeCatalogRepository()
    private val repo = FakeChecklistRepository(catalog)
    private val groceries = catalog.seedCategory("groceries", "Groceries")

    private fun viewModel() = HomeViewModel(
        ObserveChecklistsUseCase(repo),
        DuplicateChecklistUseCase(repo),
        ArchiveChecklistUseCase(repo),
        UnarchiveChecklistUseCase(repo),
        DeleteChecklistUseCase(repo),
    )

    /** Keeps the WhileSubscribed state flow running, as the screen does. */
    private fun TestScope.started(vm: HomeViewModel): HomeViewModel {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    private suspend fun checklistWithItems(title: String, done: Int, total: Int): ChecklistId {
        val id = repo.createChecklist(title, null, listOf(groceries.id))
        val section = repo.detail(id)!!.sections.single().id
        val items = (1..total).map { NewChecklistItem(null, null, "Item $it", "en", null, null, null) }
        repo.addItems(section, items).take(done).forEach { repo.setItemCompleted(it, true) }
        return id
    }

    @Test
    fun `first use shows the empty state until a checklist exists`() = runTest {
        val vm = started(viewModel())
        assertTrue(vm.uiState.value.isFirstUse)

        repo.createChecklist("Diwali Shopping", null, emptyList())

        assertFalse(vm.uiState.value.isFirstUse)
        assertEquals(listOf("Diwali Shopping"), vm.uiState.value.checklists.map { it.title })
    }

    @Test
    fun `rows carry progress counts`() = runTest {
        checklistWithItems("Goa Trip", done = 3, total = 4)
        val vm = started(viewModel())

        val row = vm.uiState.value.checklists.single()
        assertEquals(3, row.completedItems)
        assertEquals(4, row.totalItems)
        assertEquals(0.75f, row.progress)
    }

    @Test
    fun `search and the archived filter narrow the list`() = runTest {
        repo.createChecklist("Goa Trip", null, emptyList())
        val exam = repo.createChecklist("Exam day", null, emptyList())
        repo.setArchived(exam, true)
        val vm = started(viewModel())
        assertEquals(listOf("Goa Trip"), vm.uiState.value.checklists.map { it.title })

        vm.onAction(HomeAction.FilterChanged(ChecklistFilter.ARCHIVED))
        assertEquals(listOf("Exam day"), vm.uiState.value.checklists.map { it.title })

        vm.onAction(HomeAction.SearchChanged("goa "))
        assertTrue(vm.uiState.value.checklists.isEmpty())
        assertEquals("goa ", vm.uiState.value.search)
    }

    @Test
    fun `only archived checklists is not first use`() = runTest {
        val id = repo.createChecklist("Old list", null, emptyList())
        repo.setArchived(id, true)
        val vm = started(viewModel())

        assertFalse(vm.uiState.value.isFirstUse)
        assertTrue(vm.uiState.value.checklists.isEmpty())
    }

    @Test
    fun `archive reports it and undo makes the checklist active again`() = runTest {
        repo.createChecklist("Goa Trip", null, emptyList())
        val vm = started(viewModel())
        val row = vm.uiState.value.checklists.single()

        vm.effect.test {
            vm.onAction(HomeAction.Archive(row))
            assertEquals(HomeEffect.Archived(row.id, "Goa Trip"), awaitItem())
            assertTrue(vm.uiState.value.checklists.isEmpty())

            vm.onAction(HomeAction.UndoArchive(row.id))
            assertEquals(listOf("Goa Trip"), vm.uiState.value.checklists.map { it.title })
        }
    }

    @Test
    fun `delete waits for confirmation`() = runTest {
        repo.createChecklist("Goa Trip", null, emptyList())
        val vm = started(viewModel())
        val row = vm.uiState.value.checklists.single()

        vm.onAction(HomeAction.RequestDelete(row))
        assertEquals(row, vm.uiState.value.pendingDelete)
        vm.onAction(HomeAction.DismissDelete)
        assertNull(vm.uiState.value.pendingDelete)
        assertEquals(1, repo.checklistCount())

        vm.effect.test {
            vm.onAction(HomeAction.RequestDelete(row))
            vm.onAction(HomeAction.ConfirmDelete)
            assertEquals(HomeEffect.Deleted("Goa Trip"), awaitItem())
        }
        assertEquals(0, repo.checklistCount())
        assertNull(vm.uiState.value.pendingDelete)
    }

    @Test
    fun `duplicate creates an un-ticked copy with the given title`() = runTest {
        val id = checklistWithItems("Goa Trip", done = 2, total = 2)
        val vm = started(viewModel())

        vm.effect.test {
            vm.onAction(HomeAction.Duplicate(id, "Goa Trip (copy)"))
            val effect = awaitItem() as HomeEffect.Duplicated
            assertEquals("Goa Trip (copy)", effect.title)
            val copy = repo.detail(effect.id)!!
            assertEquals(0, copy.completedItems)
            assertEquals(2, copy.totalItems)
        }
    }

    @Test
    fun `duplicate with an invalid title reports an error`() = runTest {
        val id = repo.createChecklist("Goa Trip", null, emptyList())
        val vm = started(viewModel())

        vm.effect.test {
            vm.onAction(HomeAction.Duplicate(id, "   "))
            assertTrue(awaitItem() is HomeEffect.Error)
        }
        assertEquals(1, repo.checklistCount())
    }
}
