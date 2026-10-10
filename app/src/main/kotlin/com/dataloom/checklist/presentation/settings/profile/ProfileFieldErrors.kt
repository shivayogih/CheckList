package com.dataloom.checklist.presentation.settings.profile

import com.dataloom.checklist.R
import com.dataloom.checklist.domain.phone.PhoneCountry
import com.dataloom.checklist.domain.usecase.DomainError
import com.dataloom.checklist.domain.validation.Field
import com.dataloom.checklist.domain.validation.FieldLimits
import com.dataloom.checklist.domain.validation.InputText
import com.dataloom.checklist.domain.validation.PhoneEntry
import com.dataloom.checklist.domain.validation.PhoneNumberInput
import com.dataloom.checklist.domain.validation.ProfileValidator
import com.dataloom.checklist.domain.validation.ValidationError
import com.dataloom.checklist.domain.validation.ValidationResult
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

fun DomainError.Invalid.toProfileFieldErrors(phoneCountry: PhoneCountry? = null): ProfileFieldErrors =
    errors.toProfileFieldErrors(phoneCountry)

/** [phoneCountry] makes the phone error quote that country's number length (CL-380). */
fun List<ValidationError>.toProfileFieldErrors(phoneCountry: PhoneCountry? = null): ProfileFieldErrors {
    fun errorFor(field: Field) = firstOrNull { it.field == field }?.toUiText()
    val phoneError = errorFor(Field.PHONE)
    return ProfileFieldErrors(
        name = errorFor(Field.DISPLAY_NAME),
        email = errorFor(Field.EMAIL),
        phone = if (phoneError != null && phoneCountry != null) phoneLengthError(phoneCountry) else phoneError,
        address = errorFor(Field.ADDRESS),
    )
}

/** "Enter a phone number with 10 digits." for India; a range where a country's numbers vary. */
fun phoneLengthError(country: PhoneCountry): UiText {
    val digits = country.nationalDigits
    return if (digits.first == digits.last) {
        UiText(R.string.error_phone_digits_exact, listOf(digits.first))
    } else {
        UiText(R.string.error_phone_invalid, listOf(digits.first, digits.last))
    }
}

/** True when no field has an error. */
val ProfileFieldErrors.isClear: Boolean get() = name == null && email == null && phone == null && address == null

/**
 * The inline errors for what is typed now (CL-280): the same [ProfileValidator] that saving runs, so
 * the message and the enabled state of Save or Continue agree with what saving would do. Every
 * field is optional, so an empty form has no errors.
 */
fun liveProfileErrors(
    name: String,
    email: String,
    phone: String,
    address: String,
    phoneCountry: String? = null,
): ProfileFieldErrors =
    when (val result = ProfileValidator.validate(name, email, phone, address, phoneCountry)) {
        is ValidationResult.Valid -> ProfileFieldErrors()
        is ValidationResult.Invalid ->
            result.errors.toProfileFieldErrors(ProfileValidator.phoneCountryOf(phone, phoneCountry))
    }

/** Keyboard filters for the profile fields: control and bidi characters never enter the form. */
object ProfileTyping {
    fun name(raw: String): String = InputText.forField(raw, FieldLimits.DISPLAY_NAME_MAX)
    fun email(raw: String): String = InputText.forEmail(raw)
    fun phone(raw: String, countryIso: String?): PhoneEntry = PhoneNumberInput.typed(raw, countryIso)
    fun address(raw: String): String = InputText.forField(raw, FieldLimits.ADDRESS_MAX, multiline = true)
}
