package com.dataloom.checklist.data.repository

import app.cash.turbine.test
import com.dataloom.checklist.data.FakeClock
import com.dataloom.checklist.data.FakeSeedSource
import com.dataloom.checklist.data.SequentialIds
import com.dataloom.checklist.data.inMemoryDatabase
import com.dataloom.checklist.data.seed.SeedLoader
import com.dataloom.checklist.domain.model.Category
import com.dataloom.checklist.domain.model.ChecklistDetail
import com.dataloom.checklist.domain.model.ChecklistFilter
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.ChecklistItemId
import com.dataloom.checklist.domain.model.ChecklistItemUpdate
import com.dataloom.checklist.domain.model.ChecklistQuery
import com.dataloom.checklist.domain.model.ChecklistSort
import com.dataloom.checklist.domain.model.MasterItem
import com.dataloom.checklist.domain.model.NewChecklistItem
import com.dataloom.checklist.domain.model.Quantity
import com.dataloom.checklist.domain.model.SectionId
import com.dataloom.checklist.domain.model.UnitCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomChecklistRepositoryTest {

    private val clock = FakeClock(now = 10_000)
    private val ids = SequentialIds()
    private val db = inMemoryDatabase()
    private val repository = RoomChecklistRepository(db, clock, ids)
    private val catalog = RoomCatalogRepository(db, clock, ids)

    private lateinit var groceries: Category
    private lateinit var vegetables: Category
    private lateinit var rice: MasterItem

    @Before
    fun setUp() = runTest {
        SeedLoader(FakeSeedSource(), clock, SequentialIds("seed")).seedIfNeeded(db.openHelper.writableDatabase)
        val categories = catalog.observeCategories("en").first()
        groceries = categories.single { it.canonicalKey == "groceries" }
        vegetables = categories.single { it.canonicalKey == "vegetables" }
        rice = catalog.searchMasterItems("rice", "en").first { it.canonicalKey == "rice" }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun createdChecklistHasSectionsInOrderWithLocalizedCategories() = runTest {
        val id = repository.createChecklist("Weekly shop", "Saturday", listOf(groceries.id, vegetables.id, groceries.id))

        val detail = detail(id, locale = "kn")
        assertEquals("Weekly shop", detail.checklist.title)
        assertEquals("Saturday", detail.checklist.description)
        assertEquals(listOf("ದಿನಸಿ", "Vegetables"), detail.sections.map { it.category.displayName })
        assertEquals(listOf(1000, 2000), detail.sections.map { it.displayOrder })
        assertEquals(10_000L, detail.checklist.createdAt)
    }

    @Test
    fun addItemsSnapshotsValuesBumpsUseCountAndUpdatesProgress() = runTest {
        val id = repository.createChecklist("Shop", null, listOf(groceries.id))
        val section = detail(id).sections.single().id

        repository.observeChecklist(id, "en").test {
            assertEquals(0, awaitItem()!!.totalItems)

            val added = repository.addItems(
                section,
                listOf(fromMaster(rice, Quantity.parse("2.5")), typed("Jaggery")),
            )
            val withItems = awaitItem()!!
            assertEquals(listOf("Rice", "Jaggery"), withItems.sections.single().items.map { it.displayName })
            assertEquals(listOf(1000, 2000), withItems.sections.single().items.map { it.position })
            assertEquals("2.5", withItems.sections.single().items.first().quantity?.toPlainString())
            assertEquals(0f, withItems.progress, 0f)

            repository.setItemCompleted(added.first(), true)
            assertEquals(0.5f, awaitItem()!!.progress, 0f)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, catalog.getMasterItem(rice.id, "en")?.useCount)
    }

    @Test
    fun duplicateCopiesEverythingWithNewIdsAndUntickedItems() = runTest {
        val id = repository.createChecklist("Trip", "Goa", listOf(groceries.id, vegetables.id))
        val sections = detail(id).sections
        val items = repository.addItems(sections[0].id, listOf(fromMaster(rice, Quantity.of(1)), typed("Snacks")))
        repository.addItems(sections[1].id, listOf(typed("Onion")))
        repository.setItemCompleted(items[0], true)
        repository.setArchived(id, true)

        val copyId = repository.duplicateChecklist(id, "Trip (copy)")

        val original = detail(id)
        val copy = detail(copyId)
        assertNotEquals(id, copyId)
        assertEquals("Trip (copy)", copy.checklist.title)
        assertEquals("Goa", copy.checklist.description)
        assertFalse(copy.checklist.isArchived)
        assertEquals(original.sections.map { it.category.id }, copy.sections.map { it.category.id })
        assertEquals(
            original.sections.map { s -> s.items.map { it.displayName } },
            copy.sections.map { s -> s.items.map { it.displayName } },
        )
        assertTrue(copy.sections.flatMap { it.items }.none { it.isCompleted })
        assertTrue(copy.sections.map { it.id }.intersect(original.sections.map { it.id }.toSet()).isEmpty())
        assertEquals(rice.id, copy.sections[0].items[0].masterItemId)
        assertEquals(1, original.completedItems)
    }

    @Test
    fun addSectionsSkipsCategoriesAlreadyPresent() = runTest {
        val id = repository.createChecklist("Shop", null, listOf(groceries.id))

        val added = repository.addSections(id, listOf(groceries.id, vegetables.id))

        assertEquals(1, added.size)
        assertEquals(listOf(1000, 2000), detail(id).sections.map { it.displayOrder })
        assertTrue(repository.addSections(id, listOf(vegetables.id)).isEmpty())
    }

    @Test
    fun removeSectionDeletesItsItems() = runTest {
        val id = repository.createChecklist("Shop", null, listOf(groceries.id, vegetables.id))
        val section = detail(id).sections.first().id
        repository.addItems(section, listOf(typed("Salt")))

        repository.removeSection(section)

        val detail = detail(id)
        assertEquals(listOf(vegetables.id), detail.sections.map { it.category.id })
        assertEquals(0, detail.totalItems)
    }

    @Test
    fun moveItemRewritesOnlyTheMovedRowWhileGapsRemain() = runTest {
        val section = newSection()
        val added = repository.addItems(section, listOf(typed("A"), typed("B"), typed("C")))

        repository.moveItem(added[2], 0)

        val items = sectionItems(section)
        assertEquals(listOf("C", "A", "B"), items.map { it.displayName })
        assertEquals(listOf(0, 1000, 2000), items.map { it.position })

        repository.moveItem(added[0], 2)
        assertEquals(listOf("C", "B", "A"), sectionItems(section).map { it.displayName })
    }

    @Test
    fun moveSectionReordersSections() = runTest {
        val id = repository.createChecklist("Shop", null, listOf(groceries.id, vegetables.id))
        val second = detail(id).sections[1].id

        repository.moveSection(second, 0)

        assertEquals(listOf(vegetables.id, groceries.id), detail(id).sections.map { it.category.id })
    }

    @Test
    fun updateItemChangesOnlyRequestedFields() = runTest {
        val section = newSection()
        val item = repository.addItems(
            section,
            listOf(NewChecklistItem(null, null, "Milk", "en", Quantity.of(2), UnitCode("KG"), "Full cream")),
        ).single()

        repository.updateItem(item, ChecklistItemUpdate(displayName = "Toned milk"))
        sectionItems(section).single().let {
            assertEquals("Toned milk", it.displayName)
            assertEquals(Quantity.of(2), it.quantity)
            assertEquals(UnitCode("KG"), it.unit)
            assertEquals("Full cream", it.notes)
        }

        repository.updateItem(item, ChecklistItemUpdate(clearUnit = true, clearNotes = true))
        sectionItems(section).single().let {
            assertEquals(Quantity.of(2), it.quantity)
            assertNull(it.unit)
            assertNull(it.notes)
        }

        repository.updateItem(item, ChecklistItemUpdate(quantity = Quantity.of(3), unit = UnitCode("PIECE")))
        repository.updateItem(item, ChecklistItemUpdate(clearQuantity = true))
        sectionItems(section).single().let {
            assertNull(it.quantity)
            assertNull(it.unit)
        }
    }

    @Test
    fun writesToMissingIdsAreNoOps() = runTest {
        val missingItem = ChecklistItemId("missing")
        repository.setArchived(ChecklistId("missing"), true)
        repository.deleteChecklist(ChecklistId("missing"))
        repository.removeSection(SectionId("missing"))
        repository.moveSection(SectionId("missing"), 0)
        repository.updateItem(missingItem, ChecklistItemUpdate(displayName = "x"))
        repository.setItemCompleted(missingItem, true)
        repository.deleteItem(missingItem)
        repository.moveItem(missingItem, 0)
    }

    @Test
    fun deletedChecklistEmitsNull() = runTest {
        val id = repository.createChecklist("Temp", null, emptyList())

        repository.observeChecklist(id, "en").test {
            assertEquals("Temp", awaitItem()?.checklist?.title)
            repository.deleteChecklist(id)
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeChecklistsFiltersSearchesAndSorts() = runTest {
        val beta = repository.createChecklist("Beta trip", "Mountains", listOf(groceries.id))
        clock.now += 1
        val alpha = repository.createChecklist("Alpha 100% list", null, listOf(groceries.id))
        clock.now += 1
        val archived = repository.createChecklist("Gamma", null, emptyList())
        repository.setArchived(archived, true)
        val section = detail(beta).sections.single().id
        clock.now += 1
        val done = repository.addItems(section, listOf(typed("Boots")))
        repository.setItemCompleted(done.single(), true)

        fun titles(query: ChecklistQuery) = repository.observeChecklists(query)

        assertEquals(listOf(beta, alpha), titles(ChecklistQuery()).first().map { it.checklist.id })
        assertEquals(listOf(alpha, beta), titles(ChecklistQuery(sort = ChecklistSort.TITLE)).first().map { it.checklist.id })
        assertEquals(listOf(beta, alpha), titles(ChecklistQuery(sort = ChecklistSort.PROGRESS)).first().map { it.checklist.id })
        assertEquals(listOf(archived), titles(ChecklistQuery(filter = ChecklistFilter.ARCHIVED)).first().map { it.checklist.id })
        assertEquals(3, titles(ChecklistQuery(filter = ChecklistFilter.ALL)).first().size)
        assertEquals(listOf(beta), titles(ChecklistQuery(search = "mountain")).first().map { it.checklist.id })
        // LIKE wildcards in the search are literal.
        assertEquals(listOf(alpha), titles(ChecklistQuery(search = "100%")).first().map { it.checklist.id })
        assertTrue(titles(ChecklistQuery(search = "_")).first().isEmpty())

        val summary = titles(ChecklistQuery()).first().first()
        assertEquals(1, summary.totalItems)
        assertEquals(1, summary.completedItems)
    }

    @Test
    fun titleExistsIgnoresCaseAndExcludedChecklist() = runTest {
        val id = repository.createChecklist("Diwali", null, emptyList())

        assertTrue(repository.titleExists("diwali"))
        assertFalse(repository.titleExists("DIWALI", excluding = id))
        assertFalse(repository.titleExists("Holi"))
    }

    private suspend fun detail(id: ChecklistId, locale: String = "en"): ChecklistDetail =
        repository.observeChecklist(id, locale).first()!!

    private suspend fun newSection(): SectionId {
        val id = repository.createChecklist("List ${ids.newId()}", null, listOf(groceries.id))
        return detail(id).sections.single().id
    }

    private suspend fun sectionItems(section: SectionId) =
        repository.observeChecklists(ChecklistQuery(filter = ChecklistFilter.ALL)).first()
            .map { detail(it.checklist.id) }
            .flatMap { it.sections }
            .single { it.id == section }
            .items

    private fun fromMaster(item: MasterItem, quantity: Quantity?) =
        NewChecklistItem(item.id, item.canonicalKey, item.displayName, "en", quantity, item.defaultUnit, null)

    private fun typed(name: String) = NewChecklistItem(null, null, name, "en", null, null, null)
}
