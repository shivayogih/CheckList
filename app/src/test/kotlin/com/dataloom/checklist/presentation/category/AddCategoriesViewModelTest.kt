package com.dataloom.checklist.presentation.category

import app.cash.turbine.test
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.usecase.AddCategoriesToChecklistUseCase
import com.dataloom.checklist.domain.usecase.CreateCategoryUseCase
import com.dataloom.checklist.domain.usecase.ObserveCategoriesUseCase
import com.dataloom.checklist.domain.usecase.ObserveChecklistDetailUseCase
import com.dataloom.checklist.testing.FakeCatalogRepository
import com.dataloom.checklist.testing.FakeChecklistRepository
import com.dataloom.checklist.testing.FakeLanguageProvider
import com.dataloom.checklist.testing.MainDispatcherRule
import com.dataloom.checklist.testing.keepCollecting
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AddCategoriesViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val catalog = FakeCatalogRepository()
    private val repo = FakeChecklistRepository(catalog)
    private val groceries = catalog.seedCategory("groceries", "Groceries")
    private val fruits = catalog.seedCategory("fruits", "Fruits")
    private val gifts = catalog.seedCategory("gifts", "Gifts")
    // Set by the setup helper; value classes cannot be lateinit.
    private var checklistId = ChecklistId("")

    private suspend fun TestScope.viewModel(): AddCategoriesViewModel {
        checklistId = repo.createChecklist("Diwali", null, listOf(groceries.id))
        val vm = AddCategoriesViewModel(
            checklistId = checklistId.value,
            observeCategories = ObserveCategoriesUseCase(catalog),
            observeDetail = ObserveChecklistDetailUseCase(repo),
            addCategories = AddCategoriesToChecklistUseCase(repo),
            createCategory = CreateCategoryUseCase(catalog),
            languageProvider = FakeLanguageProvider(),
        )
        keepCollecting(vm.uiState)
        return vm
    }

    @Test
    fun `only categories not yet in the checklist are offered`() = runTest {
        val vm = viewModel()
        assertEquals(listOf("Fruits", "Gifts"), vm.uiState.value.categories.map { it.name })
    }

    @Test
    fun `selected categories are appended as sections`() = runTest {
        val vm = viewModel()
        vm.onAction(AddCategoriesAction.ToggleCategory(gifts.id))
        vm.onAction(AddCategoriesAction.ToggleCategory(fruits.id))

        vm.effect.test {
            vm.onAction(AddCategoriesAction.Save)
            assertEquals(AddCategoriesEffect.Added(2), awaitItem())
        }
        assertEquals(listOf(groceries.id, fruits.id, gifts.id), repo.detail(checklistId)!!.sections.map { it.category.id })
        assertTrue(vm.uiState.value.categories.isEmpty())
    }

    @Test
    fun `a new category is selected and can be added straight away`() = runTest {
        val vm = viewModel()
        vm.onAction(AddCategoriesAction.OpenNewCategory)
        vm.onAction(AddCategoriesAction.NewCategoryNameChanged("Pooja Items"))
        vm.onAction(AddCategoriesAction.ConfirmNewCategory)
        assertEquals(1, vm.uiState.value.selectedCount)

        vm.onAction(AddCategoriesAction.Save)

        assertEquals(listOf("Groceries", "Pooja Items"), repo.detail(checklistId)!!.sections.map { it.category.displayName })
    }

    @Test
    fun `the screen closes when the checklist is deleted`() = runTest {
        val vm = viewModel()
        vm.effect.test {
            repo.deleteChecklist(checklistId)
            assertEquals(AddCategoriesEffect.ChecklistGone, awaitItem())
        }
    }
}
