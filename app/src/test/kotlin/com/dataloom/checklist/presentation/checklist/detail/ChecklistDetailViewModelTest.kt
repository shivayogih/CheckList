package com.dataloom.checklist.presentation.checklist.detail

import com.dataloom.checklist.domain.validation.FieldLimits
import app.cash.turbine.test
import com.dataloom.checklist.R
import com.dataloom.checklist.domain.model.BuiltInUnits
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.ChecklistItemId
import com.dataloom.checklist.domain.model.ItemPhoto
import com.dataloom.checklist.domain.model.NewChecklistItem
import com.dataloom.checklist.domain.model.PhotoId
import com.dataloom.checklist.domain.model.Quantity
import com.dataloom.checklist.domain.model.UnitCode
import com.dataloom.checklist.domain.usecase.DeleteChecklistItemUseCase
import com.dataloom.checklist.domain.usecase.MoveItemUseCase
import com.dataloom.checklist.domain.usecase.MoveSectionUseCase
import com.dataloom.checklist.domain.usecase.ObserveChecklistDetailUseCase
import com.dataloom.checklist.domain.usecase.ObserveUnitsUseCase
import com.dataloom.checklist.domain.usecase.RemoveSectionUseCase
import com.dataloom.checklist.domain.usecase.SetItemCompletedUseCase
import com.dataloom.checklist.domain.repository.ChecklistRepository
import com.dataloom.checklist.domain.usecase.UpdateChecklistUseCase
import com.dataloom.checklist.presentation.common.UiText
import com.dataloom.checklist.testing.FakeCatalogRepository
import com.dataloom.checklist.testing.FakeChecklistRepository
import com.dataloom.checklist.testing.FakeLanguageProvider
import com.dataloom.checklist.testing.FakePhotoStore
import com.dataloom.checklist.testing.MainDispatcherRule
import com.dataloom.checklist.testing.keepCollecting
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChecklistDetailViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val catalog = FakeCatalogRepository()
    private val repo = FakeChecklistRepository(catalog)
    private val groceries = catalog.seedCategory("groceries", "Groceries")
    private val fruits = catalog.seedCategory("fruits", "Fruits")
    // Set by the setup helper; value classes cannot be lateinit.
    private var checklistId = ChecklistId("")

    private fun newItem(name: String, quantity: Quantity? = null, unit: UnitCode? = null) =
        NewChecklistItem(null, null, name, "en", quantity, unit, null)

    private suspend fun seed() {
        checklistId = repo.createChecklist("Diwali", "For the festival", listOf(groceries.id, fruits.id))
        val (groceriesSection, fruitsSection) = repo.detail(checklistId)!!.sections.map { it.id }
        repo.addItems(groceriesSection, listOf(newItem("Rice", Quantity.of(5), BuiltInUnits.KG.code), newItem("Sugar"), newItem("Salt")))
        repo.addItems(fruitsSection, listOf(newItem("Apple")))
    }

    private val photoStore = FakePhotoStore()

    private fun TestScope.viewModel(writes: ChecklistRepository = repo): ChecklistDetailViewModel {
        val vm = ChecklistDetailViewModel(
            checklistId = checklistId.value,
            observeDetail = ObserveChecklistDetailUseCase(repo),
            observeUnits = ObserveUnitsUseCase(catalog),
            setItemCompleted = SetItemCompletedUseCase(repo),
            deleteItem = DeleteChecklistItemUseCase(writes),
            moveItem = MoveItemUseCase(repo),
            moveSection = MoveSectionUseCase(repo),
            removeSection = RemoveSectionUseCase(repo),
            updateChecklist = UpdateChecklistUseCase(repo),
            languageProvider = FakeLanguageProvider(),
            applicationScope = backgroundScope,
            photoStore = photoStore,
        )
        keepCollecting(vm.uiState)
        return vm
    }

    private fun ChecklistDetailViewModel.names() = uiState.value.sections.map { s -> s.name to s.items.map { it.name } }

    private fun ChecklistDetailViewModel.item(name: String) = uiState.value.sections.flatMap { it.items }.single { it.name == name }

    @Test
    fun `sections, items, units and progress are shown`() = runTest {
        seed()
        val vm = viewModel()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Diwali", state.title)
        assertEquals("For the festival", state.description)
        assertEquals(listOf("Groceries" to listOf("Rice", "Sugar", "Salt"), "Fruits" to listOf("Apple")), vm.names())
        val rice = vm.item("Rice")
        assertEquals(Quantity.of(5), rice.quantity)
        assertEquals(BuiltInUnits.KG, rice.unit)
        assertNull(vm.item("Sugar").unit)
        assertEquals(0, state.completedItems)
        assertEquals(4, state.totalItems)
    }

    @Test
    fun `items carry their photos in order and the screen knows when any exist`() = runTest {
        seed()
        val rice = repo.detail(checklistId)!!.sections.first().items.first { it.displayName == "Rice" }
        assertFalse(viewModel().uiState.value.hasPhotos)

        repo.setPhotos(
            rice.id,
            listOf("a.jpg", "b.jpg").mapIndexed { index, name ->
                ItemPhoto(PhotoId("p$index"), rice.id, name, 100, 50, 10L, (index + 1) * 1000, "Caption $index", 1L)
            },
        )
        val vm = viewModel()

        val photos = vm.item("Rice").photos
        assertEquals(listOf("p0", "p1"), photos.map { it.id.value })
        assertEquals(listOf("Caption 0", "Caption 1"), photos.map { it.caption })
        assertEquals(photoStore.thumbnailFile("a.jpg"), photos.first().thumbnail)
        assertTrue(vm.item("Sugar").photos.isEmpty())
        assertTrue(vm.uiState.value.hasPhotos)
    }

    @Test
    fun `ticking an item updates progress`() = runTest {
        seed()
        val vm = viewModel()

        vm.onAction(ChecklistDetailAction.ToggleItem(vm.item("Rice").id, completed = true))

        assertTrue(vm.item("Rice").isCompleted)
        assertEquals(1, vm.uiState.value.completedItems)
        assertEquals(0.25f, vm.uiState.value.progress)
    }

    @Test
    fun `a deleted item is hidden at once and comes back on undo`() = runTest {
        seed()
        val vm = viewModel()
        val sugar = vm.item("Sugar")

        vm.effect.test {
            vm.onAction(ChecklistDetailAction.DeleteItem(sugar))
            assertEquals(ChecklistDetailEffect.ItemDeleted(sugar.id, "Sugar"), awaitItem())
        }
        assertEquals(listOf("Rice", "Salt"), vm.uiState.value.sections.first().items.map { it.name })
        assertEquals(3, vm.uiState.value.totalItems)
        assertTrue("Not deleted before the Undo window closes", repo.item(sugar.id) != null)

        vm.onAction(ChecklistDetailAction.UndoDelete(sugar.id))
        assertEquals(listOf("Rice", "Sugar", "Salt"), vm.uiState.value.sections.first().items.map { it.name })
        assertTrue(repo.item(sugar.id) != null)
    }

    @Test
    fun `a deletion is committed when the undo snackbar closes`() = runTest {
        seed()
        val vm = viewModel()
        val sugar = vm.item("Sugar")

        vm.onAction(ChecklistDetailAction.DeleteItem(sugar))
        vm.onAction(ChecklistDetailAction.CommitDelete(sugar.id))

        assertNull(repo.item(sugar.id))
        assertEquals(listOf("Rice", "Salt"), vm.uiState.value.sections.first().items.map { it.name })
        // Undo after the commit has nothing left to restore.
        vm.onAction(ChecklistDetailAction.UndoDelete(sugar.id))
        assertNull(repo.item(sugar.id))
    }

    @Test
    fun `pending deletions are committed when the screen comes back`() = runTest {
        seed()
        val vm = viewModel()
        val sugar = vm.item("Sugar")
        val salt = vm.item("Salt")

        vm.onAction(ChecklistDetailAction.DeleteItem(sugar))
        vm.onAction(ChecklistDetailAction.DeleteItem(salt))
        vm.onAction(ChecklistDetailAction.CommitPendingDeletes)

        assertNull(repo.item(sugar.id))
        assertNull(repo.item(salt.id))
    }

    @Test
    fun `items move up and down within their section`() = runTest {
        seed()
        val vm = viewModel()
        assertFalse(vm.item("Rice").canMoveUp)
        assertFalse(vm.item("Salt").canMoveDown)

        vm.onAction(ChecklistDetailAction.MoveItemDown(vm.item("Rice").id))
        assertEquals(listOf("Sugar", "Rice", "Salt"), vm.uiState.value.sections.first().items.map { it.name })

        vm.onAction(ChecklistDetailAction.MoveItemUp(vm.item("Salt").id))
        assertEquals(listOf("Sugar", "Salt", "Rice"), vm.uiState.value.sections.first().items.map { it.name })
    }

    @Test
    fun `moving past an item waiting for undo lands next to the visible neighbour`() = runTest {
        seed()
        val vm = viewModel()
        vm.onAction(ChecklistDetailAction.DeleteItem(vm.item("Sugar")))

        vm.onAction(ChecklistDetailAction.MoveItemDown(vm.item("Rice").id))

        assertEquals(listOf("Salt", "Rice"), vm.uiState.value.sections.first().items.map { it.name })
    }

    @Test
    fun `sections move and the edges cannot move further`() = runTest {
        seed()
        val vm = viewModel()
        val first = vm.uiState.value.sections.first()
        assertFalse(first.canMoveUp)
        assertTrue(first.canMoveDown)

        vm.onAction(ChecklistDetailAction.MoveSectionDown(first.id))
        assertEquals(listOf("Fruits", "Groceries"), vm.uiState.value.sections.map { it.name })

        vm.onAction(ChecklistDetailAction.MoveSectionDown(first.id))
        assertEquals(listOf("Fruits", "Groceries"), vm.uiState.value.sections.map { it.name })
    }

    @Test
    fun `removing a section needs confirmation`() = runTest {
        seed()
        val vm = viewModel()
        val fruitsSection = vm.uiState.value.sections.last()

        vm.onAction(ChecklistDetailAction.RequestRemoveSection(fruitsSection))
        vm.onAction(ChecklistDetailAction.DismissRemoveSection)
        assertEquals(2, vm.uiState.value.sections.size)

        vm.onAction(ChecklistDetailAction.RequestRemoveSection(fruitsSection))
        assertEquals(fruitsSection, vm.uiState.value.pendingSectionRemoval)
        vm.onAction(ChecklistDetailAction.ConfirmRemoveSection)
        assertEquals(listOf("Groceries"), vm.uiState.value.sections.map { it.name })
        assertNull(vm.uiState.value.pendingSectionRemoval)
    }

    @Test
    fun `rename validates the title and keeps the description`() = runTest {
        seed()
        val vm = viewModel()

        vm.onAction(ChecklistDetailAction.StartRename)
        assertEquals("Diwali", vm.uiState.value.rename?.title)
        vm.onAction(ChecklistDetailAction.RenameChanged(" "))
        vm.onAction(ChecklistDetailAction.ConfirmRename)
        assertEquals(UiText(R.string.error_title_blank), vm.uiState.value.rename?.error)

        vm.onAction(ChecklistDetailAction.RenameChanged("Diwali 2026"))
        vm.onAction(ChecklistDetailAction.ConfirmRename)
        assertNull(vm.uiState.value.rename)
        assertEquals("Diwali 2026", vm.uiState.value.title)
        assertEquals("For the festival", repo.detail(checklistId)!!.checklist.description)
    }

    @Test
    fun `the screen closes when the checklist is deleted`() = runTest {
        seed()
        val vm = viewModel()

        vm.effect.test {
            repo.deleteChecklist(checklistId)
            assertEquals(ChecklistDetailEffect.ChecklistGone, awaitItem())
        }
    }

    @Test
    fun `sharing a PDF right after a delete commits the delete first`() = runTest {
        seed()
        val vm = viewModel()
        val sugar = vm.item("Sugar")

        vm.effect.test {
            vm.onAction(ChecklistDetailAction.DeleteItem(sugar))
            assertEquals(ChecklistDetailEffect.ItemDeleted(sugar.id, "Sugar"), awaitItem())
            assertTrue("Still in the database while Undo is offered", repo.item(sugar.id) != null)

            vm.onAction(ChecklistDetailAction.ExportPdf(PdfExport.SHARE))

            assertEquals(ChecklistDetailEffect.PdfReady(PdfExport.SHARE), awaitItem())
            // The PDF reads the database after PdfReady, so the item must be gone by now (CL-241).
            assertNull(repo.item(sugar.id))
        }
        assertEquals(listOf("Rice", "Salt"), vm.uiState.value.sections.first().items.map { it.name })
    }

    @Test
    fun `saving a PDF waits for a delete commit that is already running`() = runTest {
        seed()
        // The Undo snackbar closed and its delete is still writing (slow storage) when Save is tapped.
        val release = CompletableDeferred<Unit>()
        val deleted = mutableListOf<ChecklistItemId>()
        val slowDeletes = object : ChecklistRepository by repo {
            override suspend fun deleteItem(itemId: ChecklistItemId) {
                deleted += itemId
                release.await()
                repo.deleteItem(itemId)
            }
        }
        val vm = viewModel(writes = slowDeletes)
        val sugar = vm.item("Sugar")

        vm.effect.test {
            vm.onAction(ChecklistDetailAction.DeleteItem(sugar))
            awaitItem()
            vm.onAction(ChecklistDetailAction.CommitDelete(sugar.id))
            vm.onAction(ChecklistDetailAction.ExportPdf(PdfExport.SAVE))

            expectNoEvents()
            release.complete(Unit)

            assertEquals(ChecklistDetailEffect.PdfReady(PdfExport.SAVE), awaitItem())
            assertNull(repo.item(sugar.id))
        }
        assertEquals("Deleted once, not once per caller", listOf(sugar.id), deleted)
    }

    @Test
    fun `a PDF with nothing waiting for undo is ready at once and keeps undone items`() = runTest {
        seed()
        val vm = viewModel()
        val sugar = vm.item("Sugar")
        vm.onAction(ChecklistDetailAction.DeleteItem(sugar))
        vm.onAction(ChecklistDetailAction.UndoDelete(sugar.id))

        vm.effect.test {
            skipItems(1) // ItemDeleted from above
            vm.onAction(ChecklistDetailAction.ExportPdf(PdfExport.SHARE))
            assertEquals(ChecklistDetailEffect.PdfReady(PdfExport.SHARE), awaitItem())
        }
        assertTrue(repo.item(sugar.id) != null)
    }

    @Test
    fun `the rename dialog filters the title and enables Save only for a valid one`() = runTest {
        seed()
        val vm = viewModel()

        vm.onAction(ChecklistDetailAction.StartRename)
        assertTrue(vm.uiState.value.rename!!.canConfirm)

        vm.onAction(ChecklistDetailAction.RenameChanged("   "))
        assertEquals(UiText(R.string.error_title_blank), vm.uiState.value.rename?.error)
        assertFalse(vm.uiState.value.rename!!.canConfirm)

        vm.onAction(ChecklistDetailAction.RenameChanged("Di\u202Ewali\n2026"))
        assertEquals("Diwali 2026", vm.uiState.value.rename?.title)
        assertNull(vm.uiState.value.rename?.error)
        assertTrue(vm.uiState.value.rename!!.canConfirm)

        vm.onAction(ChecklistDetailAction.RenameChanged("t".repeat(FieldLimits.TITLE_MAX + 1)))
        assertFalse(vm.uiState.value.rename!!.canConfirm)
    }
}
