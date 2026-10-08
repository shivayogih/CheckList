package com.dataloom.checklist.presentation.common

import com.dataloom.checklist.domain.validation.QuantityInput
import com.dataloom.checklist.domain.validation.QuantityParse
import com.dataloom.checklist.domain.validation.ValidationResult

/**
 * Errors shown while the user types (CL-280). The rules are the domain validators', run on every
 * change, so the message under a field and the enabled state of the primary action always agree with
 * what saving would do.
 */

/** The first error of [validate] for [text], or null. An empty field has no error yet: it only blocks the action. */
fun liveError(text: String, validate: (String) -> ValidationResult<*>): UiText? {
    if (text.isEmpty()) return null
    return (validate(text) as? ValidationResult.Invalid)?.errors?.firstOrNull()?.toUiText()
}

/** True when [validate] accepts [text]. */
fun isAccepted(text: String, validate: (String) -> ValidationResult<*>): Boolean =
    validate(text) is ValidationResult.Valid

/** The message for an amount that cannot be saved; null for an empty or valid amount. */
fun quantityFieldError(text: String): UiText? = when (val parsed = QuantityInput.parse(text)) {
    QuantityParse.Empty, is QuantityParse.Valid -> null
    is QuantityParse.Invalid -> parsed.error.toUiText()
}
