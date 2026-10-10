package com.dataloom.checklist.domain.usecase

import app.cash.turbine.test
import com.dataloom.checklist.domain.fake.FakeCatalogRepository
import com.dataloom.checklist.domain.fake.FakeChecklistRepository
import com.dataloom.checklist.domain.model.ChecklistFilter
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.ChecklistQuery
import com.dataloom.checklist.domain.validation.ValidationError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistUseCasesTest {

    private val catalog = FakeCatalogRepository()
    private val repo = FakeChecklistRepository(catalog)
    private val groceries = catalog.seedCategory("groceries", "Groceries")
    private val fruits = catalog.seedCategory("fruits", "Fruits")

    private val create = CreateChecklistUseCase(repo)

    private suspend fun createdId(title: String = "Weekly"): ChecklistId =
        (create(title) as DomainResult.Success).value.id

    @Test
    fun `create trims fields and stores sections without duplicates`() = runTest {
        val result = create("  Weekly  ", "  ", listOf(groceries.id, fruits.id, groceries.id))

        val saved = (result as DomainResult.Success).value
        assertFalse(saved.titleAlreadyUsed)
        val detail = repo.detail(saved.id)!!
        assertEquals("Weekly", detail.checklist.title)
        assertNull(detail.checklist.description)
        assertEquals(listOf(groceries.id, fruits.id), detail.sections.map { it.category.id })
    }

    @Test
    fun `create without categories is allowed`() = runTest {
        val saved = (create("Trip") as DomainResult.Success).value
        assertTrue(repo.detail(saved.id)!!.sections.isEmpty())
    }

    @Test
    fun `create allows a duplicate title but reports it`() = runTest {
        createdId("Weekly")
        val second = (create("weekly") as DomainResult.Success).value
        assertTrue(second.titleAlreadyUsed)
        assertEquals(2, repo.checklistCount())
    }

    @Test
    fun `invalid create writes nothing`() = runTest {
        val result = create(" ", "d".repeat(501))
        assertEquals(
            DomainResult.Failure(DomainError.Invalid(listOf(ValidationError.TITLE_BLANK, ValidationError.DESCRIPTION_TOO_LONG))),
            result,
        )
        assertEquals(0, repo.checklistCount())
    }

    @Test
    fun `update validates and ignores its own title when checking duplicates`() = runTest {
        val id = createdId("Weekly")
        val update = UpdateChecklistUseCase(repo)

        val same = (update(id, " WEEKLY ", "notes") as DomainResult.Success).value
        assertFalse(same.titleAlreadyUsed)
        assertEquals("WEEKLY", repo.checklist(id)!!.title)
        assertEquals("notes", repo.checklist(id)!!.description)

        createdId("Party")
        assertTrue((update(id, "party", null) as DomainResult.Success).value.titleAlreadyUsed)

        assertEquals(DomainResult.Failure(DomainError.Invalid(listOf(ValidationError.TITLE_TOO_LONG))), update(id, "t".repeat(101), null))
        assertEquals("party", repo.checklist(id)!!.title)
    }

    @Test
    fun `archive and unarchive toggle the flag`() = runTest {
        val id = createdId()
        ArchiveChecklistUseCase(repo)(id)
        assertTrue(repo.checklist(id)!!.isArchived)
        UnarchiveChecklistUseCase(repo)(id)
        assertFalse(repo.checklist(id)!!.isArchived)
    }

    @Test
    fun `delete removes the checklist and detail emits null`() = runTest {
        val id = createdId()
        ObserveChecklistDetailUseCase(repo)(id, "en").test {
            assertEquals(id, awaitItem()!!.checklist.id)
            assertEquals(DomainResult.Success(Unit), DeleteChecklistUseCase(repo)(id))
            assertNull(awaitItem())
        }
    }

    @Test
    fun `duplicate uses the given title and validates it`() = runTest {
        val id = createdId("Weekly")
        val duplicate = DuplicateChecklistUseCase(repo)

        assertEquals(DomainResult.Failure(DomainError.Invalid(listOf(ValidationError.TITLE_BLANK))), duplicate(id, "  "))

        val copy = (duplicate(id, " Weekly (copy) ") as DomainResult.Success).value
        assertFalse(copy.titleAlreadyUsed)
        assertEquals("Weekly (copy)", repo.checklist(copy.id)!!.title)

        assertTrue((duplicate(id, "weekly") as DomainResult.Success).value.titleAlreadyUsed)
    }

    @Test
    fun `observe checklists trims the search text and emits on change`() = runTest {
        val observe = ObserveChecklistsUseCase(repo)
        observe(ChecklistQuery(search = "  week ", filter = ChecklistFilter.ALL)).test {
            assertTrue(awaitItem().isEmpty())
            createdId("Weekly")
            assertEquals(listOf("Weekly"), awaitItem().map { it.checklist.title })
        }
        assertEquals("week", repo.lastQuery!!.search)
    }

    @Test
    fun `title used check trims, ignores case, skips blank and the excluded checklist`() = runTest {
        val isUsed = IsChecklistTitleUsedUseCase(repo)
        val id = createdId("Weekly")

        assertTrue(isUsed("  weekly "))
        assertFalse(isUsed("Monthly"))
        assertFalse(isUsed("   "))
        assertFalse(isUsed("Weekly", excluding = id))
    }
}
