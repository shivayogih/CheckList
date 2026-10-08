package com.dataloom.checklist.domain.usecase

import com.dataloom.checklist.domain.model.Category
import com.dataloom.checklist.domain.model.CategoryId
import com.dataloom.checklist.domain.repository.CatalogRepository
import com.dataloom.checklist.domain.validation.CategoryValidator
import com.dataloom.checklist.domain.validation.ValidationResult
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveCategoriesUseCase @Inject constructor(private val catalog: CatalogRepository) {
    /** Most used first, then by name. Hidden categories only for the "manage categories" screen. */
    operator fun invoke(locale: String, includeHidden: Boolean = false): Flow<List<Category>> =
        catalog.observeCategories(locale, includeHidden)
}

/**
 * Creates a custom category. A name that already resolves to a visible category in [locale] is
 * refused: two "Gifts" in the picker could not be told apart.
 */
class CreateCategoryUseCase @Inject constructor(private val catalog: CatalogRepository) {
    suspend operator fun invoke(name: String, iconKey: String, locale: String): DomainResult<CategoryId> {
        val cleanName = when (val result = CategoryValidator.validateName(name)) {
            is ValidationResult.Invalid -> return result.toFailure()
            is ValidationResult.Valid -> result.value
        }
        if (catalog.categoryNameExists(cleanName, locale)) return failure(DomainError.DuplicateName)
        return success(catalog.createCategory(cleanName, iconKey))
    }
}

/**
 * Renames a category everywhere (section 7). Renaming a seeded category stores a custom name that
 * overrides its translations; [ResetCategoryNameUseCase] undoes that.
 */
class RenameCategoryUseCase @Inject constructor(private val catalog: CatalogRepository) {
    suspend operator fun invoke(id: CategoryId, name: String, locale: String): DomainResult<Unit> {
        val cleanName = when (val result = CategoryValidator.validateName(name)) {
            is ValidationResult.Invalid -> return result.toFailure()
            is ValidationResult.Valid -> result.value
        }
        val category = catalog.getCategory(id, locale) ?: return failure(DomainError.NotFound)
        // Unchanged name: writing it would freeze a seeded category's translation into a custom name.
        if (cleanName == category.displayName) return success(Unit)
        // A case-only change ("gifts" -> "Gifts") matches the category itself, not a duplicate.
        val isOwnName = cleanName.equals(category.displayName, ignoreCase = true)
        if (!isOwnName && catalog.categoryNameExists(cleanName, locale)) return failure(DomainError.DuplicateName)
        catalog.renameCategory(id, cleanName)
        return success(Unit)
    }
}

/** Restores a seeded category's translated name after a rename. */
class ResetCategoryNameUseCase @Inject constructor(private val catalog: CatalogRepository) {
    suspend operator fun invoke(id: CategoryId): DomainResult<Unit> {
        val category = catalog.getCategory(id, LOOKUP_LOCALE) ?: return failure(DomainError.NotFound)
        if (category.isCustom) return failure(DomainError.CustomCategoryHasNoDefaultName)
        catalog.renameCategory(id, null)
        return success(Unit)
    }
}

/**
 * Deletes an unused custom category (and its custom master items). Never deletes a category that a
 * checklist uses: it returns [DomainError.CategoryInUse] so the UI can offer hiding instead.
 */
class DeleteCategoryUseCase @Inject constructor(private val catalog: CatalogRepository) {
    suspend operator fun invoke(id: CategoryId): DomainResult<Unit> {
        val category = catalog.getCategory(id, LOOKUP_LOCALE) ?: return failure(DomainError.NotFound)
        if (!category.isCustom) return failure(DomainError.SeededCategoryNotDeletable)
        val usage = catalog.countChecklistsUsing(id)
        if (usage > 0) return failure(DomainError.CategoryInUse(usage))
        catalog.deleteCategory(id)
        return success(Unit)
    }
}

/** Hides a category from suggestions without touching checklists that use it. */
class HideCategoryUseCase @Inject constructor(private val catalog: CatalogRepository) {
    suspend operator fun invoke(id: CategoryId, hidden: Boolean = true): DomainResult<Unit> =
        success(catalog.setCategoryHidden(id, hidden))
}
