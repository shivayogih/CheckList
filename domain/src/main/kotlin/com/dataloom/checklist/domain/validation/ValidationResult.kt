package com.dataloom.checklist.domain.validation

/**
 * Outcome of validating user, import or AI input. [Valid] carries the normalized value (trimmed,
 * blank optional text turned into null) so callers persist exactly what was checked. [Invalid] lists
 * every problem at once so a form can mark all bad fields in one pass.
 */
sealed interface ValidationResult<out T> {
    data class Valid<out T>(val value: T) : ValidationResult<T>

    data class Invalid(val errors: List<ValidationError>) : ValidationResult<Nothing> {
        init {
            require(errors.isNotEmpty()) { "Invalid needs at least one error" }
        }
    }
}

internal fun <T> validationOf(value: T, errors: List<ValidationError>): ValidationResult<T> =
    if (errors.isEmpty()) ValidationResult.Valid(value) else ValidationResult.Invalid(errors)
