package com.dataloom.checklist.presentation.settings.profile

import com.dataloom.checklist.domain.usecase.DomainError
import com.dataloom.checklist.domain.validation.Field
import com.dataloom.checklist.presentation.common.UiText
import com.dataloom.checklist.presentation.common.toUiText

/**
 * The inline error under each profile field. Shared by the Settings profile screen and first-run
 * profile setup (CL-250), so both show the same text for the same rule.
 */
data class ProfileFieldErrors(
    val name: UiText? = null,
    val email: UiText? = null,
    val phone: UiText? = null,
    val address: UiText? = null,
)

fun DomainError.Invalid.toProfileFieldErrors(): ProfileFieldErrors {
    fun errorFor(field: Field) = errors.firstOrNull { it.field == field }?.toUiText()
    return ProfileFieldErrors(
        name = errorFor(Field.DISPLAY_NAME),
        email = errorFor(Field.EMAIL),
        phone = errorFor(Field.PHONE),
        address = errorFor(Field.ADDRESS),
    )
}
