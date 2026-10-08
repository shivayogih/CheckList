package com.dataloom.checklist.domain.usecase

import app.cash.turbine.test
import com.dataloom.checklist.domain.fake.FakeCatalogRepository
import com.dataloom.checklist.domain.fake.FakeChecklistRepository
import com.dataloom.checklist.domain.model.BuiltInUnits
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.ChecklistItemId
import com.dataloom.checklist.domain.model.ChecklistSection
import com.dataloom.checklist.domain.model.Quantity
import com.dataloom.checklist.domain.model.SectionId
import com.dataloom.checklist.domain.model.UnitCode
import com.dataloom.checklist.domain.validation.ValidationError
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemUseCasesTest {

    private val catalog = FakeCatalogRepository()
    private val repo = FakeChecklistRepository(catalog)
    private val groceries = catalog.seedCategory("groceries", "Groceries")
    private val fruits = catalog.seedCategory("fruits", "Fruits")
    private val rice = catalog.seedMasterItem(groceries.id, "rice", "Rice", BuiltInUnits.KG.code, useCount = 5)
    private val eggs = catalog.seedMasterItem(groceries.id, "eggs", "Eggs", BuiltInUnits.DOZEN.code, useCount = 9)
    private val salt = catalog.seedMasterItem(groceries.id, "salt", "Salt")
    private val apple = catalog.seedMasterItem(fruits.id, "apple", "Apple")

    private val addMaster = AddMasterItemsToSectionUseCase(repo, catalog)
    private val addCustom = AddCustomItemUseCase(repo, catalog)

    // The fakes complete immediately, so blocking once here keeps every test free of setup noise.
    private val checklistId: ChecklistId = runBlocking {
        (CreateChecklistUseCase(repo)("Weekly", categoryIds = listOf(groceries.id)) as DomainResult.Success).value.id
    }

    private fun section(): ChecklistSection = repo.detail(checklistId)!!.sections.single()

    private suspend fun addOne(selection: MasterItemSelection): AddItemsOutcome =
        (addMaster(checklistId, section().id, listOf(selection), "kn") as DomainResult.Success).value

    // ---- Observe / search ----

    @Test
    fun `observe master items lists a category's visible suggestions`() = runTest {
        ObserveMasterItemsUseCase(catalog)(groceries.id, "en").test {
            assertEquals(listOf(eggs.id, rice.id, salt.id), awaitItem().map { it.id })
        }
    }

    @Test
    fun `search trims the query before searching`() = runTest {
        val result = SearchMasterItemsUseCase(catalog)("  ric ", "en")
        assertEquals(listOf(rice.id), result.map { it.id })
        assertEquals(listOf("ric"), catalog.searchQueries)
    }

    @Test
    fun `search is scoped to a category when given`() = runTest {
        assertEquals(listOf(apple.id), SearchMasterItemsUseCase(catalog)("p", "en", fruits.id).map { it.id })
    }

    @Test
    fun `blank query returns the category suggestions most used first`() = runTest {
        val result = SearchMasterItemsUseCase(catalog)("   ", "en", groceries.id, limit = 2)
        assertEquals(listOf(eggs.id, rice.id), result.map { it.id })
        assertTrue(catalog.searchQueries.isEmpty())
    }

    @Test
    fun `blank query without a category returns nothing`() = runTest {
        assertTrue(SearchMasterItemsUseCase(catalog)("", "en").isEmpty())
        assertTrue(catalog.searchQueries.isEmpty())
    }

    // ---- Add master items ----

    @Test
    fun `adding master items snapshots name, key, locale, unit and quantity`() = runTest {
        val sectionId = section().id
        val result = addMaster(
            checklistId,
            sectionId,
            listOf(MasterItemSelection(rice, Quantity.of(5)), MasterItemSelection(salt)),
            "kn",
        )

        assertEquals(2, (result as DomainResult.Success).value.addedItemIds.size)
        val items = section().items
        val riceItem = items.first { it.masterItemId == rice.id }
        assertEquals("Rice", riceItem.displayName)
        assertEquals("rice", riceItem.canonicalKey)
        assertEquals("kn", riceItem.displayNameLocale)
        assertEquals(Quantity.of(5), riceItem.quantity)
        assertEquals(BuiltInUnits.KG.code, riceItem.unit)
        val saltItem = items.first { it.masterItemId == salt.id }
        assertNull(saltItem.quantity)
        assertNull(saltItem.unit)
    }

    @Test
    fun `default unit is not copied when no quantity is given`() = runTest {
        addOne(MasterItemSelection(rice))
        val item = section().items.single()
        assertNull(item.quantity)
        assertNull(item.unit)
    }

    @Test
    fun `selected unit overrides the default unit`() = runTest {
        addOne(MasterItemSelection(rice, Quantity.of(500), BuiltInUnits.GRAM.code))
        assertEquals(BuiltInUnits.GRAM.code, section().items.single().unit)
    }

    @Test
    fun `snapshot survives later master item changes`() = runTest {
        addOne(MasterItemSelection(rice, Quantity.of(1)))
        catalog.setMasterItemHidden(rice.id, true)
        assertEquals("Rice", section().items.single().displayName)
    }

    @Test
    fun `items already in the section are reported, not duplicated`() = runTest {
        addOne(MasterItemSelection(rice, Quantity.of(1)))
        val existing = section().items.single()

        val outcome = addOne(MasterItemSelection(rice, Quantity.of(2)))

        assertTrue(outcome.addedItemIds.isEmpty())
        assertEquals(listOf(existing), outcome.alreadyPresent)
        assertEquals(1, section().items.size)
        assertEquals(1, repo.addItemsCalls.size) // nothing written the second time
    }

    @Test
    fun `mixed selection adds new items and reports present ones`() = runTest {
        val sectionId = section().id
        addOne(MasterItemSelection(rice, Quantity.of(1)))

        val outcome = (addMaster(checklistId, sectionId, listOf(MasterItemSelection(rice), MasterItemSelection(salt)), "en") as DomainResult.Success).value

        assertEquals(1, outcome.addedItemIds.size)
        assertEquals(listOf(rice.id), outcome.alreadyPresent.map { it.masterItemId })
    }

    @Test
    fun `the same master item picked twice is added once`() = runTest {
        val sectionId = section().id
        addMaster(checklistId, sectionId, listOf(MasterItemSelection(salt), MasterItemSelection(salt, Quantity.of(2))), "en")
        assertEquals(1, section().items.size)
    }

    @Test
    fun `a fractional amount of a whole-number unit rejects the whole batch`() = runTest {
        val sectionId = section().id
        val result = addMaster(
            checklistId,
            sectionId,
            listOf(MasterItemSelection(rice, Quantity.of(1)), MasterItemSelection(eggs, Quantity.parse("1.5"))),
            "en",
        )

        assertEquals(DomainResult.Failure(DomainError.InvalidSelection(eggs.id, listOf(ValidationError.QUANTITY_MUST_BE_WHOLE))), result)
        assertTrue(section().items.isEmpty())
    }

    @Test
    fun `unknown unit and unit without quantity are rejected`() = runTest {
        val sectionId = section().id
        assertEquals(
            DomainResult.Failure(DomainError.InvalidSelection(salt.id, listOf(ValidationError.UNKNOWN_UNIT))),
            addMaster(checklistId, sectionId, listOf(MasterItemSelection(salt, Quantity.of(1), UnitCode("BOGUS"))), "en"),
        )
        assertEquals(
            DomainResult.Failure(DomainError.InvalidSelection(salt.id, listOf(ValidationError.UNIT_WITHOUT_QUANTITY))),
            addMaster(checklistId, sectionId, listOf(MasterItemSelection(salt, unit = BuiltInUnits.KG.code)), "en"),
        )
    }

    @Test
    fun `adding to a missing section is not found`() = runTest {
        assertEquals(
            DomainResult.Failure(DomainError.NotFound),
            addMaster(checklistId, SectionId("gone"), listOf(MasterItemSelection(salt)), "en"),
        )
        assertEquals(
            DomainResult.Failure(DomainError.NotFound),
            addMaster(ChecklistId("gone"), SectionId("gone"), listOf(MasterItemSelection(salt)), "en"),
        )
    }

    // ---- Custom items ----

    @Test
    fun `custom item is validated, trimmed and not linked by default`() = runTest {
        val sectionId = section().id
        val result = addCustom(checklistId, sectionId, " Jaggery ", "kn", Quantity.parse("0.5"), BuiltInUnits.KG.code, "  organic ")

        val added = (result as DomainResult.Success).value as CustomItemOutcome.Added
        assertNull(added.masterItemId)
        val item = repo.item(added.itemId)!!
        assertEquals("Jaggery", item.displayName)
        assertEquals("kn", item.displayNameLocale)
        assertEquals("organic", item.notes)
        assertNull(item.masterItemId)
        assertEquals(4, catalog.allMasterItems().size)
    }

    @Test
    fun `invalid custom item writes nothing`() = runTest {
        val sectionId = section().id
        val result = addCustom(checklistId, sectionId, "", "en", quantity = Quantity.parse("2.5"), unit = BuiltInUnits.PIECE.code, saveToMasterList = true)
        assertEquals(
            DomainResult.Failure(DomainError.Invalid(listOf(ValidationError.ITEM_NAME_BLANK, ValidationError.QUANTITY_MUST_BE_WHOLE))),
            result,
        )
        assertTrue(section().items.isEmpty())
        assertEquals(4, catalog.allMasterItems().size)
    }

    @Test
    fun `custom item quantity without unit is allowed`() = runTest {
        val sectionId = section().id
        assertTrue(addCustom(checklistId, sectionId, "Bananas", "en", quantity = Quantity.of(6)) is DomainResult.Success)
    }

    @Test
    fun `save to master list creates a suggestion in the section's category`() = runTest {
        val sectionId = section().id
        val added = (addCustom(checklistId, sectionId, "Jaggery", "en", Quantity.of(1), BuiltInUnits.KG.code, saveToMasterList = true) as DomainResult.Success).value
            as CustomItemOutcome.Added

        val master = catalog.allMasterItems().single { it.id == added.masterItemId }
        assertEquals(groceries.id, master.categoryId)
        assertEquals("Jaggery", master.displayName)
        assertEquals(BuiltInUnits.KG.code, master.defaultUnit)
        assertEquals(added.masterItemId, repo.item(added.itemId)!!.masterItemId)
    }

    @Test
    fun `save to master list reuses an existing master item with the same name`() = runTest {
        val sectionId = section().id
        val added = (addCustom(checklistId, sectionId, " salt ", "en", saveToMasterList = true) as DomainResult.Success).value
            as CustomItemOutcome.Added

        assertEquals(salt.id, added.masterItemId)
        assertEquals("salt", repo.item(added.itemId)!!.canonicalKey)
        assertEquals(4, catalog.allMasterItems().size)
    }

    @Test
    fun `save to master list reports a matching item already in the section`() = runTest {
        addOne(MasterItemSelection(salt))
        val existing = section().items.single()

        val result = addCustom(checklistId, existing.sectionId, "Salt", "en", saveToMasterList = true)

        assertEquals(DomainResult.Success(CustomItemOutcome.AlreadyPresent(existing)), result)
        assertEquals(1, section().items.size)
    }

    @Test
    fun `custom item in a missing section is not found`() = runTest {
        assertEquals(DomainResult.Failure(DomainError.NotFound), addCustom(checklistId, SectionId("gone"), "X", "en"))
    }

    // ---- Edit, complete, delete, move ----

    private suspend fun riceItemId(): ChecklistItemId {
        return addOne(MasterItemSelection(rice, Quantity.of(2))).addedItemIds.single()
    }

    @Test
    fun `update saves the full validated state`() = runTest {
        val id = riceItemId()
        val result = UpdateChecklistItemUseCase(repo, catalog)(id, " Basmati rice ", Quantity.parse("2.5"), BuiltInUnits.KG.code, " long grain ")

        assertEquals(DomainResult.Success(Unit), result)
        val item = repo.item(id)!!
        assertEquals("Basmati rice", item.displayName)
        assertEquals(Quantity.parse("2.5"), item.quantity)
        assertEquals("long grain", item.notes)
    }

    @Test
    fun `update with no quantity and blank notes clears them`() = runTest {
        val id = riceItemId()
        UpdateChecklistItemUseCase(repo, catalog)(id, "Rice", null, null, " ")

        val item = repo.item(id)!!
        assertNull(item.quantity)
        assertNull(item.unit)
        assertNull(item.notes)
        assertTrue(repo.updates.single().second.clearQuantity)
        assertTrue(repo.updates.single().second.clearNotes)
    }

    @Test
    fun `invalid update writes nothing`() = runTest {
        val id = riceItemId()
        val update = UpdateChecklistItemUseCase(repo, catalog)
        assertEquals(
            DomainResult.Failure(DomainError.Invalid(listOf(ValidationError.UNIT_WITHOUT_QUANTITY))),
            update(id, "Rice", null, BuiltInUnits.KG.code, null),
        )
        assertEquals(
            DomainResult.Failure(DomainError.Invalid(listOf(ValidationError.QUANTITY_MUST_BE_WHOLE))),
            update(id, "Rice", Quantity.parse("0.5"), BuiltInUnits.BOX.code, null),
        )
        assertEquals(
            DomainResult.Failure(DomainError.Invalid(listOf(ValidationError.NOTES_TOO_LONG))),
            update(id, "Rice", null, null, "n".repeat(501)),
        )
        assertTrue(repo.updates.isEmpty())
    }

    @Test
    fun `set completed toggles the item and progress`() = runTest {
        val id = riceItemId()
        SetItemCompletedUseCase(repo)(id, true)
        assertTrue(repo.item(id)!!.isCompleted)
        assertEquals(1f, repo.detail(checklistId)!!.progress)
        SetItemCompletedUseCase(repo)(id, false)
        assertFalse(repo.item(id)!!.isCompleted)
    }

    @Test
    fun `delete removes the item`() = runTest {
        val id = riceItemId()
        assertEquals(DomainResult.Success(Unit), DeleteChecklistItemUseCase(repo)(id))
        assertNull(repo.item(id))
    }

    @Test
    fun `move reorders items and rejects negative positions`() = runTest {
        val riceId = riceItemId()
        val saltId = addOne(MasterItemSelection(salt)).addedItemIds.single()
        val move = MoveItemUseCase(repo)

        assertEquals(DomainResult.Failure(DomainError.Invalid(listOf(ValidationError.NEGATIVE_POSITION))), move(saltId, -1))

        move(saltId, 0)
        assertEquals(listOf(saltId, riceId), section().items.map { it.id })
    }
}
