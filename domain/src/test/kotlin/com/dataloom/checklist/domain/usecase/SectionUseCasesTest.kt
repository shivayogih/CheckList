package com.dataloom.checklist.domain.usecase

import com.dataloom.checklist.domain.fake.FakeCatalogRepository
import com.dataloom.checklist.domain.fake.FakeChecklistRepository
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.validation.ValidationError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionUseCasesTest {

    private val catalog = FakeCatalogRepository()
    private val repo = FakeChecklistRepository(catalog)
    private val groceries = catalog.seedCategory("groceries", "Groceries")
    private val fruits = catalog.seedCategory("fruits", "Fruits")
    private val gifts = catalog.seedCategory("gifts", "Gifts")

    private suspend fun checklist(): ChecklistId =
        (CreateChecklistUseCase(repo)("Weekly", categoryIds = listOf(groceries.id)) as DomainResult.Success).value.id

    @Test
    fun `adding categories appends new sections and skips present ones`() = runTest {
        val id = checklist()
        val result = AddCategoriesToChecklistUseCase(repo)(id, listOf(groceries.id, fruits.id, fruits.id, gifts.id))

        assertEquals(2, (result as DomainResult.Success).value.size)
        assertEquals(listOf(groceries.id, fruits.id, gifts.id), repo.detail(id)!!.sections.map { it.category.id })
    }

    @Test
    fun `adding no categories is a no-op`() = runTest {
        val id = checklist()
        assertEquals(DomainResult.Success(emptyList<Any>()), AddCategoriesToChecklistUseCase(repo)(id, emptyList()))
        assertEquals(1, repo.detail(id)!!.sections.size)
    }

    @Test
    fun `remove section deletes it`() = runTest {
        val id = checklist()
        val section = repo.detail(id)!!.sections.single()
        RemoveSectionUseCase(repo)(section.id)
        assertTrue(repo.detail(id)!!.sections.isEmpty())
    }

    @Test
    fun `move section reorders and rejects negative positions`() = runTest {
        val id = checklist()
        AddCategoriesToChecklistUseCase(repo)(id, listOf(fruits.id, gifts.id))
        val move = MoveSectionUseCase(repo)
        val giftsSection = repo.detail(id)!!.sections.last()

        assertEquals(DomainResult.Failure(DomainError.Invalid(listOf(ValidationError.NEGATIVE_POSITION))), move(giftsSection.id, -1))

        move(giftsSection.id, 0)
        assertEquals(listOf(gifts.id, groceries.id, fruits.id), repo.detail(id)!!.sections.map { it.category.id })
    }
}
