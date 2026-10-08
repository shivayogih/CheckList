package com.dataloom.checklist.presentation.masteritem

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.dataloom.checklist.R
import com.dataloom.checklist.domain.model.BuiltInUnits
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.Quantity
import com.dataloom.checklist.domain.model.SectionId
import com.dataloom.checklist.domain.usecase.AddMasterItemsToSectionUseCase
import com.dataloom.checklist.domain.usecase.ObserveChecklistDetailUseCase
import com.dataloom.checklist.domain.usecase.ObserveUnitsUseCase
import com.dataloom.checklist.domain.usecase.SearchMasterItemsUseCase
import com.dataloom.checklist.presentation.common.UiText
import com.dataloom.checklist.testing.FakeCatalogRepository
import com.dataloom.checklist.testing.FakeChecklistRepository
import com.dataloom.checklist.testing.FakeLanguageProvider
import com.dataloom.checklist.testing.MainDispatcherRule
import com.dataloom.checklist.testing.keepCollecting
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AddItemsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val catalog = FakeCatalogRepository()
    private val repo = FakeChecklistRepository(catalog)
    private val groceries = catalog.seedCategory("groceries", "Groceries")
    private val fruits = catalog.seedCategory("fruits", "Fruits")
    private val rice = catalog.seedMasterItem(groceries.id, "rice", "Rice", BuiltInUnits.KG.code, useCount = 5)
    private val sugar = catalog.seedMasterItem(groceries.id, "sugar", "Sugar", BuiltInUnits.KG.code, useCount = 9)
    private val soap = catalog.seedMasterItem(groceries.id, "soap", "Soap", BuiltInUnits.PIECE.code)
    private val apple = catalog.seedMasterItem(fruits.id, "apple", "Apple", BuiltInUnits.KG.code)
    // Set by the setup helper; value classes cannot be lateinit.
    private var checklistId = ChecklistId("")
    private var sectionId = SectionId("")

    private suspend fun TestScope.viewModel(handle: SavedStateHandle = SavedStateHandle()): AddItemsViewModel {
        checklistId = repo.createChecklist("Diwali", null, listOf(groceries.id))
        sectionId = repo.detail(checklistId)!!.sections.single().id
        val vm = AddItemsViewModel(
            checklistId = checklistId.value,
            sectionId = sectionId.value,
            observeDetail = ObserveChecklistDetailUseCase(repo),
            observeUnits = ObserveUnitsUseCase(catalog),
            searchMasterItems = SearchMasterItemsUseCase(catalog),
            addMasterItems = AddMasterItemsToSectionUseCase(repo, catalog),
            languageProvider = FakeLanguageProvider(),
            savedState = handle,
        )
        keepCollecting(vm.uiState)
        advanceUntilIdle()
        return vm
    }

    private fun AddItemsViewModel.rowNames() = uiState.value.rows.map { it.name }

    private fun AddItemsViewModel.row(name: String) = uiState.value.rows.single { it.name == name }

    @Test
    fun `an empty search shows the section's suggestions, most used first`() = runTest {
        val vm = viewModel()

        assertEquals("Groceries", vm.uiState.value.sectionName)
        assertEquals(listOf("Sugar", "Rice", "Soap"), vm.rowNames())
    }

    @Test
    fun `typing searches the section and the toggle searches every category`() = runTest {
        val vm = viewModel()

        vm.onAction(AddItemsAction.QueryChanged("ap"))
        advanceUntilIdle()
        assertEquals(listOf("Soap"), vm.rowNames())

        vm.onAction(AddItemsAction.AllCategoriesChanged(true))
        advanceUntilIdle()
        assertEquals(listOf("Soap", "Apple"), vm.rowNames())
    }

    @Test
    fun `the search text and the all categories switch are restored after process death`() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        first.onAction(AddItemsAction.QueryChanged("ap"))
        first.onAction(AddItemsAction.AllCategoriesChanged(true))
        advanceUntilIdle()

        val second = viewModel(handle)

        assertEquals("ap", second.uiState.value.query)
        assertTrue(second.uiState.value.allCategories)
        assertEquals(listOf("Soap", "Apple"), second.rowNames())
    }

    @Test
    fun `selected items stay visible while searching for more`() = runTest {
        val vm = viewModel()
        vm.onAction(AddItemsAction.ToggleItem(rice.id))

        vm.onAction(AddItemsAction.QueryChanged("sug"))
        advanceUntilIdle()

        assertEquals(listOf("Rice", "Sugar"), vm.rowNames())
        assertTrue(vm.row("Rice").selected)
        assertEquals(1, vm.uiState.value.selectedCount)
    }

    @Test
    fun `selecting defaults the unit and adding copies amount and unit`() = runTest {
        val vm = viewModel()
        vm.onAction(AddItemsAction.ToggleItem(rice.id))
        vm.onAction(AddItemsAction.ToggleItem(sugar.id))
        assertEquals(BuiltInUnits.KG.code, vm.row("Rice").unit)

        vm.onAction(AddItemsAction.QuantityChanged(rice.id, "2,5"))
        vm.effect.test {
            vm.onAction(AddItemsAction.AddSelected)
            assertEquals(AddItemsEffect.Added(2), awaitItem())
        }

        val items = repo.detail(checklistId)!!.sections.single().items
        val addedRice = items.single { it.displayName == "Rice" }
        assertEquals(Quantity.parse("2.5"), addedRice.quantity)
        assertEquals(BuiltInUnits.KG.code, addedRice.unit)
        val addedSugar = items.single { it.displayName == "Sugar" }
        assertEquals(null, addedSugar.quantity)
        assertEquals("No unit without an amount", null, addedSugar.unit)
        assertEquals(0, vm.uiState.value.selectedCount)
    }

    @Test
    fun `an unreadable amount is flagged on its row and nothing is added`() = runTest {
        val vm = viewModel()
        vm.onAction(AddItemsAction.ToggleItem(rice.id))
        vm.onAction(AddItemsAction.QuantityChanged(rice.id, "two"))

        vm.onAction(AddItemsAction.AddSelected)

        assertEquals(UiText(R.string.error_quantity_invalid), vm.row("Rice").error)
        assertTrue(repo.detail(checklistId)!!.sections.single().items.isEmpty())

        vm.onAction(AddItemsAction.QuantityChanged(rice.id, "2"))
        assertEquals(null, vm.row("Rice").error)
    }

    @Test
    fun `a fraction of a whole-number unit is refused by the domain rule`() = runTest {
        val vm = viewModel()
        vm.onAction(AddItemsAction.ToggleItem(soap.id))
        vm.onAction(AddItemsAction.QuantityChanged(soap.id, "1.5"))

        vm.onAction(AddItemsAction.AddSelected)

        assertEquals(UiText(R.string.error_quantity_whole), vm.row("Soap").error)
        assertTrue(repo.detail(checklistId)!!.sections.single().items.isEmpty())
    }

    @Test
    fun `items already on the list are reported instead of duplicated`() = runTest {
        val vm = viewModel()
        vm.effect.test {
            vm.onAction(AddItemsAction.ToggleItem(rice.id))
            vm.onAction(AddItemsAction.AddSelected)
            assertEquals(AddItemsEffect.Added(1), awaitItem())

            vm.onAction(AddItemsAction.ToggleItem(rice.id))
            vm.onAction(AddItemsAction.ToggleItem(sugar.id))
            vm.onAction(AddItemsAction.AddSelected)
            assertEquals(AddItemsEffect.AlreadyOnList(listOf("Rice"), addedCount = 1), awaitItem())
        }
        assertEquals(2, repo.detail(checklistId)!!.sections.single().items.size)
    }

    @Test
    fun `create custom is offered only for a name that is not an exact suggestion`() = runTest {
        val vm = viewModel()
        assertFalse(vm.uiState.value.canCreateCustom)

        vm.onAction(AddItemsAction.QueryChanged("Dry fruits"))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canCreateCustom)

        vm.onAction(AddItemsAction.QueryChanged("rice"))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.canCreateCustom)
    }
}
