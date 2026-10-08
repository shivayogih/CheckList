package com.dataloom.checklist.domain.usecase

import app.cash.turbine.test
import com.dataloom.checklist.domain.fake.FakeCatalogRepository
import com.dataloom.checklist.domain.fake.FakeChecklistRepository
import com.dataloom.checklist.domain.model.CategoryId
import com.dataloom.checklist.domain.validation.ValidationError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryUseCasesTest {

    private val catalog = FakeCatalogRepository()
    private val checklists = FakeChecklistRepository(catalog)
    private val groceries = catalog.seedCategory("groceries", "Groceries")

    private val create = CreateCategoryUseCase(catalog)
    private val rename = RenameCategoryUseCase(catalog)

    private suspend fun custom(name: String): CategoryId = (create(name, "gift", "en") as DomainResult.Success).value

    @Test
    fun `create trims and stores a custom category`() = runTest {
        val id = custom("  Wedding ")
        val category = catalog.categoryById(id)!!
        assertEquals("Wedding", category.displayName)
        assertTrue(category.isCustom)
    }

    @Test
    fun `create rejects invalid and duplicate names`() = runTest {
        assertEquals(DomainResult.Failure(DomainError.Invalid(listOf(ValidationError.CATEGORY_NAME_BLANK))), create(" ", "x", "en"))
        assertEquals(DomainResult.Failure(DomainError.Invalid(listOf(ValidationError.CATEGORY_NAME_TOO_LONG))), create("c".repeat(51), "x", "en"))
        assertEquals(DomainResult.Failure(DomainError.DuplicateName), create(" groceries ", "x", "en"))
        assertEquals(1, catalog.allCategories().size)
    }

    @Test
    fun `rename changes the name and rejects another category's name`() = runTest {
        val wedding = custom("Wedding")
        assertEquals(DomainResult.Success(Unit), rename(wedding, " Marriage ", "en"))
        assertEquals("Marriage", catalog.categoryById(wedding)!!.displayName)

        assertEquals(DomainResult.Failure(DomainError.DuplicateName), rename(wedding, "GROCERIES", "en"))
        assertEquals(DomainResult.Failure(DomainError.Invalid(listOf(ValidationError.CATEGORY_NAME_BLANK))), rename(wedding, "", "en"))
    }

    @Test
    fun `rename allows a case-only change of its own name`() = runTest {
        val wedding = custom("wedding")
        assertEquals(DomainResult.Success(Unit), rename(wedding, "Wedding", "en"))
        assertEquals("Wedding", catalog.categoryById(wedding)!!.displayName)
    }

    @Test
    fun `renaming to the identical name writes nothing`() = runTest {
        assertEquals(DomainResult.Success(Unit), rename(groceries.id, "Groceries", "en"))
        assertTrue(catalog.renameCalls.isEmpty())
    }

    @Test
    fun `rename of a missing category is not found`() = runTest {
        assertEquals(DomainResult.Failure(DomainError.NotFound), rename(CategoryId("nope"), "X", "en"))
    }

    @Test
    fun `reset restores a seeded name but not a custom one`() = runTest {
        val reset = ResetCategoryNameUseCase(catalog)
        rename(groceries.id, "Kirana", "en")
        assertEquals(DomainResult.Success(Unit), reset(groceries.id))
        assertNull(catalog.categoryById(groceries.id)!!.customName)

        assertEquals(DomainResult.Failure(DomainError.CustomCategoryHasNoDefaultName), reset(custom("Wedding")))
        assertEquals(DomainResult.Failure(DomainError.NotFound), reset(CategoryId("nope")))
    }

    @Test
    fun `delete removes an unused custom category`() = runTest {
        val wedding = custom("Wedding")
        assertEquals(DomainResult.Success(Unit), DeleteCategoryUseCase(catalog)(wedding))
        assertNull(catalog.categoryById(wedding))
    }

    @Test
    fun `delete of a used category reports usage and keeps it`() = runTest {
        val wedding = custom("Wedding")
        CreateChecklistUseCase(checklists)("A", categoryIds = listOf(wedding))
        CreateChecklistUseCase(checklists)("B", categoryIds = listOf(wedding, groceries.id))

        assertEquals(DomainResult.Failure(DomainError.CategoryInUse(2)), DeleteCategoryUseCase(catalog)(wedding))
        assertNotNull(catalog.categoryById(wedding))
    }

    @Test
    fun `seeded categories are never deleted`() = runTest {
        assertEquals(DomainResult.Failure(DomainError.SeededCategoryNotDeletable), DeleteCategoryUseCase(catalog)(groceries.id))
        assertEquals(DomainResult.Failure(DomainError.NotFound), DeleteCategoryUseCase(catalog)(CategoryId("nope")))
    }

    @Test
    fun `hide removes the category from suggestions only`() = runTest {
        val observe = ObserveCategoriesUseCase(catalog)
        observe("en").test {
            assertEquals(listOf(groceries.id), awaitItem().map { it.id })
            HideCategoryUseCase(catalog)(groceries.id)
            assertTrue(awaitItem().isEmpty())
            HideCategoryUseCase(catalog)(groceries.id, hidden = false)
            assertEquals(listOf(groceries.id), awaitItem().map { it.id })
        }
        HideCategoryUseCase(catalog)(groceries.id)
        observe("en", includeHidden = true).test {
            assertEquals(listOf(groceries.id), awaitItem().map { it.id })
        }
    }
}
