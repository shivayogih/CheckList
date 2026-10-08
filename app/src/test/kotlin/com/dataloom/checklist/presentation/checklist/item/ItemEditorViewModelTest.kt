package com.dataloom.checklist.presentation.checklist.item

import app.cash.turbine.test
import com.dataloom.checklist.R
import com.dataloom.checklist.domain.model.BuiltInUnits
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.ChecklistItemId
import com.dataloom.checklist.domain.model.NewChecklistItem
import com.dataloom.checklist.domain.model.Quantity
import com.dataloom.checklist.domain.model.SectionId
import com.dataloom.checklist.domain.usecase.AddCustomItemUseCase
import com.dataloom.checklist.domain.usecase.CreateCustomUnitUseCase
import com.dataloom.checklist.domain.usecase.ObserveChecklistDetailUseCase
import com.dataloom.checklist.domain.usecase.ObserveUnitsUseCase
import com.dataloom.checklist.domain.usecase.UpdateChecklistItemUseCase
import com.dataloom.checklist.presentation.common.UiText
import com.dataloom.checklist.testing.FakeCatalogRepository
import com.dataloom.checklist.testing.FakeChecklistRepository
import com.dataloom.checklist.testing.FakeLanguageProvider
import com.dataloom.checklist.testing.MainDispatcherRule
import com.dataloom.checklist.testing.keepCollecting
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ItemEditorViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val catalog = FakeCatalogRepository()
    private val repo = FakeChecklistRepository(catalog)
    private val groceries = catalog.seedCategory("groceries", "Groceries")
    private lateinit var checklistId: ChecklistId
    private lateinit var sectionId: SectionId

    private suspend fun seed() {
        checklistId = repo.createChecklist("Diwali", null, listOf(groceries.id))
        sectionId = repo.detail(checklistId)!!.sections.single().id
    }

    private fun TestScope.viewModel(itemId: ChecklistItemId? = null, initialName: String = ""): ItemEditorViewModel {
        val vm = ItemEditorViewModel(
            checklistId = checklistId.value,
            sectionId = sectionId.value,
            itemId = itemId?.value,
            initialName = initialName,
            observeDetail = ObserveChecklistDetailUseCase(repo),
            observeUnits = ObserveUnitsUseCase(catalog),
            addCustomItem = AddCustomItemUseCase(repo, catalog),
            updateItem = UpdateChecklistItemUseCase(repo, catalog),
            createCustomUnit = CreateCustomUnitUseCase(catalog),
            languageProvider = FakeLanguageProvider("kn"),
        )
        keepCollecting(vm.uiState)
        return vm
    }

    private fun items() = repo.detail(checklistId)!!.sections.single().items

    @Test
    fun `a custom item takes the typed name, an amount with a comma, a unit and notes`() = runTest {
        seed()
        val vm = viewModel(initialName = "Dry fruits")
        assertFalse(vm.uiState.value.isEditing)
        assertEquals("Dry fruits", vm.uiState.value.name)
        assertEquals("Groceries", vm.uiState.value.sectionName)

        vm.onAction(ItemEditorAction.QuantityChanged("2,5"))
        vm.onAction(ItemEditorAction.UnitChanged(BuiltInUnits.KG.code))
        vm.onAction(ItemEditorAction.NotesChanged("For guests"))
        vm.effect.test {
            vm.onAction(ItemEditorAction.Save)
            assertEquals(ItemEditorEffect.Saved, awaitItem())
        }

        val item = items().single()
        assertEquals("Dry fruits", item.displayName)
        assertEquals("kn", item.displayNameLocale)
        assertEquals(Quantity.parse("2.5"), item.quantity)
        assertEquals(BuiltInUnits.KG.code, item.unit)
        assertEquals("For guests", item.notes)
        assertNull("Not saved to suggestions unless asked", item.masterItemId)
    }

    @Test
    fun `save to suggestions also creates a master item in the category`() = runTest {
        seed()
        val vm = viewModel(initialName = "Dry fruits")

        vm.onAction(ItemEditorAction.SaveToSuggestionsChanged(true))
        vm.onAction(ItemEditorAction.Save)

        val master = catalog.allMasterItems().single { it.displayName == "Dry fruits" }
        assertEquals(groceries.id, master.categoryId)
        assertEquals(master.id, items().single().masterItemId)
    }

    @Test
    fun `a matching suggestion already on the list is reported, not duplicated`() = runTest {
        seed()
        val rice = catalog.seedMasterItem(groceries.id, "rice", "Rice")
        repo.addItems(sectionId, listOf(NewChecklistItem(rice.id, "rice", "Rice", "en", null, null, null)))
        val vm = viewModel(initialName = "rice")

        vm.onAction(ItemEditorAction.SaveToSuggestionsChanged(true))
        vm.effect.test {
            vm.onAction(ItemEditorAction.Save)
            assertEquals(ItemEditorEffect.AlreadyOnList("Rice"), awaitItem())
        }
        assertEquals(1, items().size)
    }

    @Test
    fun `field errors appear next to their fields`() = runTest {
        seed()
        val vm = viewModel()

        vm.onAction(ItemEditorAction.QuantityChanged("abc"))
        vm.onAction(ItemEditorAction.Save)
        assertEquals(UiText(R.string.error_quantity_invalid), vm.uiState.value.quantityError)

        vm.onAction(ItemEditorAction.QuantityChanged(""))
        vm.onAction(ItemEditorAction.UnitChanged(BuiltInUnits.KG.code))
        vm.onAction(ItemEditorAction.Save)
        val state = vm.uiState.value
        assertEquals(UiText(R.string.error_item_name_blank), state.nameError)
        assertEquals(UiText(R.string.error_unit_without_quantity), state.unitError)
        assertTrue(items().isEmpty())
    }

    @Test
    fun `editing loads the item and saves the changes`() = runTest {
        seed()
        val id = repo.addItems(
            sectionId,
            listOf(NewChecklistItem(null, null, "Rice", "en", Quantity.of(5), BuiltInUnits.KG.code, "Basmati")),
        ).single()
        val vm = viewModel(itemId = id)

        val loaded = vm.uiState.value
        assertTrue(loaded.isEditing)
        assertEquals("Rice", loaded.name)
        assertEquals("5", loaded.quantityText)
        assertEquals(BuiltInUnits.KG, loaded.selectedUnit)
        assertEquals("Basmati", loaded.notes)

        vm.onAction(ItemEditorAction.NameChanged("Brown rice"))
        vm.onAction(ItemEditorAction.QuantityChanged("1.25"))
        vm.onAction(ItemEditorAction.NotesChanged(" "))
        vm.effect.test {
            vm.onAction(ItemEditorAction.Save)
            assertEquals(ItemEditorEffect.Saved, awaitItem())
        }

        val item = repo.item(id)!!
        assertEquals("Brown rice", item.displayName)
        assertEquals(Quantity.parse("1.25"), item.quantity)
        assertEquals(BuiltInUnits.KG.code, item.unit)
        assertNull(item.notes)
    }

    @Test
    fun `editing an item that no longer exists closes the form`() = runTest {
        seed()
        val vm = viewModel(itemId = ChecklistItemId("missing"))

        vm.effect.test {
            assertEquals(ItemEditorEffect.Gone, awaitItem())
        }
    }

    @Test
    fun `a new custom unit is created and chosen`() = runTest {
        seed()
        val vm = viewModel(initialName = "Coriander")

        vm.onAction(ItemEditorAction.OpenNewUnit)
        vm.onAction(ItemEditorAction.NewUnitLabelChanged("bunch"))
        vm.onAction(ItemEditorAction.ConfirmNewUnit)

        val state = vm.uiState.value
        assertNull(state.newUnit)
        assertNotNull(state.unit)
        assertEquals("bunch", state.selectedUnit?.customLabel)
        assertFalse(state.selectedUnit!!.allowsDecimal)
    }

    @Test
    fun `a custom unit label that exists keeps the dialog open with an error`() = runTest {
        seed()
        catalog.createCustomUnit("bunch", allowsDecimal = false)
        val vm = viewModel()

        vm.onAction(ItemEditorAction.OpenNewUnit)
        vm.onAction(ItemEditorAction.NewUnitLabelChanged("Bunch"))
        vm.onAction(ItemEditorAction.NewUnitDecimalChanged(true))
        vm.onAction(ItemEditorAction.ConfirmNewUnit)

        val dialog = vm.uiState.value.newUnit!!
        assertEquals(UiText(R.string.error_duplicate_name), dialog.error)
        assertTrue(dialog.allowsDecimal)
        assertNull(vm.uiState.value.unit)
    }
}
