package com.dataloom.checklist.domain.usecase

import com.dataloom.checklist.domain.model.MasterItemId
import com.dataloom.checklist.domain.validation.ValidationError
import com.dataloom.checklist.domain.validation.ValidationResult

/**
 * Result of every write use case. One shared type keeps ViewModels, the importer and the AI
 * executor handling outcomes the same way; expected business outcomes are values, not exceptions.
 * Unexpected failures (I/O, a broken database) still throw and are handled at the caller's boundary.
 */
sealed interface DomainResult<out T> {
    data class Success<out T>(val value: T) : DomainResult<T>

    data class Failure(val error: DomainError) : DomainResult<Nothing>
}

/** Typed reasons a write was refused. The UI maps each to a string resource. */
sealed interface DomainError {
    data class Invalid(val errors: List<ValidationError>) : DomainError

    /** One entry of a multi-item add failed validation; nothing was added. */
    data class InvalidSelection(val masterItemId: MasterItemId, val errors: List<ValidationError>) : DomainError

    /** The checklist, section or category no longer exists (e.g. deleted on another screen). */
    data object NotFound : DomainError

    /** A category or custom unit with this name already exists in the current language. */
    data object DuplicateName : DomainError

    /** Checklists still use the category; the UI offers "Hide from suggestions" instead. */
    data class CategoryInUse(val checklistCount: Int) : DomainError

    /** Seeded categories come back with the next seed update, so they are hidden, never deleted. */
    data object SeededCategoryNotDeletable : DomainError

    /** Only seeded categories have a translated name to go back to. */
    data object CustomCategoryHasNoDefaultName : DomainError

    /**
     * The profile could not be encrypted because the device's secure key storage failed (Phase 5).
     * Nothing was stored. The UI suggests trying again, or clearing the profile to start with new keys.
     */
    data object SecureStorageUnavailable : DomainError
}

internal fun ValidationResult.Invalid.toFailure(): DomainResult.Failure = DomainResult.Failure(DomainError.Invalid(errors))

internal fun failure(error: DomainError): DomainResult.Failure = DomainResult.Failure(error)

internal fun <T> success(value: T): DomainResult.Success<T> = DomainResult.Success(value)
