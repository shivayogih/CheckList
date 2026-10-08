package com.dataloom.checklist.presentation.checklist.create

import app.cash.turbine.test
import com.dataloom.checklist.R
import com.dataloom.checklist.domain.usecase.CreateCategoryUseCase
import com.dataloom.checklist.domain.usecase.CreateChecklistUseCase
import com.dataloom.checklist.domain.usecase.IsChecklistTitleUsedUseCase
import com.dataloom.checklist.domain.usecase.ObserveCategoriesUseCase
import com.dataloom.checklist.domain.validation.FieldLimits
import com.dataloom.checklist.presentation.common.UiText
import com.dataloom.checklist.testing.FakeCatalogRepository
import com.dataloom.checklist.testing.FakeChecklistRepository
import com.dataloom.checklist.testing.FakeLanguageProvider
import com.dataloom.checklist.testing.MainDispatcherRule
import com.dataloom.checklist.testing.keepCollecting
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CreateChecklistViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val catalog = FakeCatalogRepository()
    private val repo = FakeChecklistRepository(catalog)
    private val groceries = catalog.seedCategory("groceries", "Groceries")
    private val gifts = catalog.seedCategory("gifts", "Gifts")

    private fun viewModel() = CreateChecklistViewModel(
        ObserveCategoriesUseCase(catalog),
        CreateChecklistUseCase(repo),
        IsChecklistTitleUsedUseCase(repo),
        CreateCategoryUseCase(catalog),
        FakeLanguageProvider(),
    )

    @Test
    fun `a blank title shows an error next to the field and creates nothing`() = runTest {
        val vm = viewModel()
        keepCollecting(vm.uiState)

        vm.onAction(CreateChecklistAction.TitleChanged("   "))
        vm.onAction(CreateChecklistAction.Create)

        assertEquals(UiText(R.string.error_title_blank), vm.uiState.value.titleError)
        assertEquals(0, repo.checklistCount())
        assertFalse(vm.uiState.value.isSaving)

        vm.onAction(CreateChecklistAction.TitleChanged("Diwali"))
        assertNull(vm.uiState.value.titleError)
    }

    @Test
    fun `a too long description is reported with the limit`() = runTest {
        val vm = viewModel()
        keepCollecting(vm.uiState)

        vm.onAction(CreateChecklistAction.TitleChanged("Diwali"))
        vm.onAction(CreateChecklistAction.DescriptionChanged("d".repeat(FieldLimits.DESCRIPTION_MAX + 1)))
        vm.onAction(CreateChecklistAction.Create)

        assertEquals(UiText(R.string.error_too_long, listOf(FieldLimits.DESCRIPTION_MAX)), vm.uiState.value.descriptionError)
    }

    @Test
    fun `a title another checklist uses shows a warning after typing pauses`() = runTest {
        repo.createChecklist("Weekly", null, emptyList())
        val vm = viewModel()
        keepCollecting(vm.uiState)

        vm.onAction(CreateChecklistAction.TitleChanged("weekly"))
        assertFalse(vm.uiState.value.titleAlreadyUsed)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.titleAlreadyUsed)

        vm.onAction(CreateChecklistAction.TitleChanged("Monthly"))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.titleAlreadyUsed)
    }

    @Test
    fun `create saves the selected categories in list order and reports the new id`() = runTest {
        val vm = viewModel()
        keepCollecting(vm.uiState)
        assertEquals(listOf("Groceries", "Gifts"), vm.uiState.value.categories.map { it.name })

        vm.onAction(CreateChecklistAction.TitleChanged(" Diwali Shopping "))
        vm.onAction(CreateChecklistAction.ToggleCategory(gifts.id))
        vm.onAction(CreateChecklistAction.ToggleCategory(groceries.id))
        assertEquals(2, vm.uiState.value.selectedCount)

        vm.effect.test {
            vm.onAction(CreateChecklistAction.Create)
            val created = awaitItem() as CreateChecklistEffect.Created
            val detail = repo.detail(created.id)!!
            assertEquals("Diwali Shopping", detail.checklist.title)
            assertEquals(listOf(groceries.id, gifts.id), detail.sections.map { it.category.id })
        }
    }

    @Test
    fun `toggling a category twice deselects it`() = runTest {
        val vm = viewModel()
        keepCollecting(vm.uiState)

        vm.onAction(CreateChecklistAction.ToggleCategory(gifts.id))
        vm.onAction(CreateChecklistAction.ToggleCategory(gifts.id))

        assertEquals(0, vm.uiState.value.selectedCount)
    }

    @Test
    fun `a new custom category is created, listed and selected`() = runTest {
        val vm = viewModel()
        keepCollecting(vm.uiState)

        vm.onAction(CreateChecklistAction.OpenNewCategory)
        vm.onAction(CreateChecklistAction.NewCategoryNameChanged("Pooja Items"))
        vm.onAction(CreateChecklistAction.ConfirmNewCategory)

        val state = vm.uiState.value
        assertNull(state.newCategory)
        val pooja = state.categories.single { it.name == "Pooja Items" }
        assertTrue(pooja.selected)
    }

    @Test
    fun `a category name that already exists keeps the dialog open with an error`() = runTest {
        val vm = viewModel()
        keepCollecting(vm.uiState)

        vm.onAction(CreateChecklistAction.OpenNewCategory)
        vm.onAction(CreateChecklistAction.NewCategoryNameChanged("gifts"))
        vm.onAction(CreateChecklistAction.ConfirmNewCategory)

        assertEquals(UiText(R.string.error_duplicate_name), vm.uiState.value.newCategory?.error)
        assertEquals(2, vm.uiState.value.categories.size)

        vm.onAction(CreateChecklistAction.DismissNewCategory)
        assertNull(vm.uiState.value.newCategory)
    }
}
