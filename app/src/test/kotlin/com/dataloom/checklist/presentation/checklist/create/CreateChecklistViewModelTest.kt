package com.dataloom.checklist.presentation.checklist.create

import androidx.lifecycle.SavedStateHandle
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

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) = CreateChecklistViewModel(
        ObserveCategoriesUseCase(catalog),
        CreateChecklistUseCase(repo),
        IsChecklistTitleUsedUseCase(repo),
        CreateCategoryUseCase(catalog),
        FakeLanguageProvider(),
        handle,
    )

    @Test
    fun `the typed title, description and ticked categories are restored after process death`() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        keepCollecting(first.uiState)
        first.onAction(CreateChecklistAction.TitleChanged("Diwali"))
        first.onAction(CreateChecklistAction.DescriptionChanged("Festival shopping"))
        first.onAction(CreateChecklistAction.ToggleCategory(gifts.id))

        val second = viewModel(handle)
        keepCollecting(second.uiState)

        val state = second.uiState.value
        assertEquals("Diwali", state.title)
        assertEquals("Festival shopping", state.description)
        assertEquals(listOf("Gifts"), state.categories.filter { it.selected }.map { it.name })
    }

    @Test
    fun `errors and the saving flag are not restored`() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        keepCollecting(first.uiState)
        first.onAction(CreateChecklistAction.TitleChanged("   "))
        first.onAction(CreateChecklistAction.Create)
        assertEquals(UiText(R.string.error_title_blank), first.uiState.value.titleError)

        val second = viewModel(handle)
        keepCollecting(second.uiState)

        assertNull(second.uiState.value.titleError)
        assertFalse(second.uiState.value.isSaving)
    }

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

    // CL-280: filtering, live errors and the enabled state of Create.

    @Test
    fun `create is disabled until the title has a visible character`() = runTest {
        val vm = viewModel()
        keepCollecting(vm.uiState)
        assertFalse(vm.uiState.value.canCreate)
        assertNull(vm.uiState.value.titleError) // nothing typed yet

        vm.onAction(CreateChecklistAction.TitleChanged("    "))
        assertEquals(UiText(R.string.error_title_blank), vm.uiState.value.titleError)
        assertFalse(vm.uiState.value.canCreate)

        vm.onAction(CreateChecklistAction.TitleChanged("\u200B\u202E"))
        assertEquals("", vm.uiState.value.title)
        assertFalse(vm.uiState.value.canCreate)

        vm.onAction(CreateChecklistAction.TitleChanged("Diwali"))
        assertTrue(vm.uiState.value.canCreate)
    }

    @Test
    fun `an over long title or description disables create and shows the limit`() = runTest {
        val vm = viewModel()
        keepCollecting(vm.uiState)
        vm.onAction(CreateChecklistAction.TitleChanged("t".repeat(1_000)))
        assertEquals(FieldLimits.TITLE_MAX + 1, vm.uiState.value.title.length)
        assertEquals(UiText(R.string.error_too_long, listOf(FieldLimits.TITLE_MAX)), vm.uiState.value.titleError)
        assertFalse(vm.uiState.value.canCreate)

        vm.onAction(CreateChecklistAction.TitleChanged("Diwali"))
        vm.onAction(CreateChecklistAction.DescriptionChanged("d".repeat(FieldLimits.DESCRIPTION_MAX + 1)))
        assertEquals(UiText(R.string.error_too_long, listOf(FieldLimits.DESCRIPTION_MAX)), vm.uiState.value.descriptionError)
        assertFalse(vm.uiState.value.canCreate)
    }

    @Test
    fun `pasted line breaks become spaces in the title and are kept in the description`() = runTest {
        val vm = viewModel()
        keepCollecting(vm.uiState)
        vm.onAction(CreateChecklistAction.TitleChanged("Goa\ntrip\t2026"))
        vm.onAction(CreateChecklistAction.DescriptionChanged("line 1\r\nline 2"))
        assertEquals("Goa trip 2026", vm.uiState.value.title)
        assertEquals("line 1\nline 2", vm.uiState.value.description)
    }

    @Test
    fun `the new category dialog filters its name and enables Create only for a valid one`() = runTest {
        val vm = viewModel()
        keepCollecting(vm.uiState)
        vm.onAction(CreateChecklistAction.OpenNewCategory)
        assertFalse(vm.uiState.value.newCategory!!.canConfirm)

        vm.onAction(CreateChecklistAction.NewCategoryNameChanged("   "))
        assertEquals(UiText(R.string.error_category_name_blank), vm.uiState.value.newCategory!!.error)
        assertFalse(vm.uiState.value.newCategory!!.canConfirm)

        vm.onAction(CreateChecklistAction.NewCategoryNameChanged("Pooja\u200B Items\n"))
        assertEquals("Pooja Items ", vm.uiState.value.newCategory!!.name)
        assertTrue(vm.uiState.value.newCategory!!.canConfirm)

        vm.onAction(CreateChecklistAction.NewCategoryNameChanged("c".repeat(FieldLimits.CATEGORY_NAME_MAX + 1)))
        assertFalse(vm.uiState.value.newCategory!!.canConfirm)
    }
}
