package com.dataloom.checklist.domain.validation

import com.dataloom.checklist.domain.model.UserProfile
import com.dataloom.checklist.domain.phone.PhoneCountries
import com.dataloom.checklist.domain.phone.PhoneCountry

/**
 * Profile rules. Every field is optional (CL-250): blank input becomes null, and a profile with no
 * values is valid (it is simply empty). Normalization: the display name is trimmed and inner whitespace (line breaks too)
 * collapsed to one space; blank optional fields become null; phone digits typed on an Indic keyboard
 * (for example "९८७") are stored as ASCII digits so the number dials and exports the same everywhere.
 *
 * The phone is stored as a country ([UserProfile.phoneCountry]) and the national digits (CL-380). A
 * number typed with a "+" dial code picks its own country; otherwise the chosen country (India when
 * none) applies, a trunk "0" is dropped, and the length must suit that country.
 */
object ProfileValidator {

    /** Separators people type inside phone numbers. Letters, "#" and "*" are rejected. */
    private const val PHONE_SEPARATORS = " -()."

    private val WHITESPACE_RUN = Regex("\\s+")

    fun validate(
        displayName: String?,
        email: String?,
        phone: String?,
        address: String? = null,
        phoneCountry: String? = null,
    ): ValidationResult<UserProfile> {
        val (cleanName, nameError) = optionalText(
            displayName?.let { InputText.normalize(it) },
            FieldLimits.DISPLAY_NAME_MAX,
            ValidationError.DISPLAY_NAME_TOO_LONG,
        )
        val (cleanEmail, emailError) = emailOf(email)
        val (cleanPhone, phoneError) = phoneOf(phone, phoneCountry)
        val (cleanAddress, addressError) = optionalText(
            address?.let(::cleanAddress),
            FieldLimits.ADDRESS_MAX,
            ValidationError.ADDRESS_TOO_LONG,
        )
        return validationOf(
            UserProfile(cleanName, cleanEmail, cleanPhone?.second, cleanAddress, cleanPhone?.first),
            listOfNotNull(nameError, emailError, phoneError, addressError),
        )
    }

    /**
     * Addresses keep their line breaks (one line per row of the form) but each line is trimmed, inner
     * whitespace collapsed, blank lines dropped and other control characters removed.
     */
    private fun cleanAddress(raw: String): String =
        InputText.normalize(raw, multiline = true).split('\n')
            .map { line -> line.collapseWhitespace() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")

    /**
     * Deliberately loose: one "@" with something before it, a dotted domain after it, no spaces or
     * control characters. The app never sends mail, so the check only catches typos such as a missing
     * "@". Unicode is allowed (internationalized addresses).
     */
    private fun emailOf(raw: String?): Pair<String?, ValidationError?> {
        val email = raw.trimToNull() ?: return null to null
        if (email.codePointLength() > FieldLimits.EMAIL_MAX) return email to ValidationError.EMAIL_TOO_LONG
        val at = email.indexOf('@')
        if (at <= 0) return email to ValidationError.EMAIL_INVALID
        val local = email.substring(0, at)
        val domain = email.substring(at + 1)
        val valid = email.none { it.isWhitespace() || it.isISOControl() } &&
            '@' !in domain &&
            local.length <= FieldLimits.EMAIL_LOCAL_PART_MAX &&
            '.' in domain &&
            domain.split('.').none { it.isEmpty() }
        return email to if (valid) null else ValidationError.EMAIL_INVALID
    }

    /**
     * Splits a phone stored before CL-380, such as "+91 98450 12345", into its country and national
     * digits. A number that no longer validates keeps its digits under the default country, so the
     * user sees it and the inline error the next time they edit the profile.
     */
    fun splitLegacyPhone(phone: String): Pair<String, String> {
        val (split, error) = phoneOf(phone, null)
        if (split != null && error == null) return split
        return phoneCountryOf(phone, null).iso to asciiDigits(phone)
    }

    /** The country a phone error should quote lengths for: the pasted dial code's, else the chosen one. */
    fun phoneCountryOf(phone: String?, phoneCountry: String?): PhoneCountry {
        val typed = phone?.trim().orEmpty()
        if (typed.startsWith('+')) {
            PhoneCountries.splitDialCode(asciiDigits(typed))?.let { return it.first }
        }
        return PhoneCountries.orDefault(phoneCountry)
    }

    /**
     * An optional leading "+" and dial code, then digits and common separators. Returns the country ISO
     * code and the national digits, or the typed text with [ValidationError.PHONE_INVALID].
     */
    private fun phoneOf(raw: String?, countryIso: String?): Pair<Pair<String, String>?, ValidationError?> {
        val typed = raw?.let { InputText.normalize(it) }.trimToNull() ?: return null to null
        val body = typed.removePrefix("+")
        val wellFormed = typed.codePointLength() <= FieldLimits.PHONE_MAX &&
            body.all { it.isDigit() || it in PHONE_SEPARATORS } &&
            (body.firstOrNull() == '(' || body.firstOrNull()?.isDigit() == true)
        val split = if (wellFormed) countryAndDigits(typed.startsWith('+'), asciiDigits(body), countryIso) else null
        return if (split != null && split.second.length in PhoneCountries.orDefault(split.first).nationalDigits) {
            split to null
        } else {
            (countryIso.orEmpty() to typed) to ValidationError.PHONE_INVALID
        }
    }

    /** The country (from the dial code when [international]) and its national digits, trunk "0" dropped. */
    private fun countryAndDigits(international: Boolean, digits: String, countryIso: String?): Pair<String, String>? {
        val (country, national) = if (international) {
            PhoneCountries.splitDialCode(digits) ?: return null
        } else {
            PhoneCountries.orDefault(countryIso) to digits
        }
        val withoutTrunk = if (country.trunkZero && national.startsWith('0')) national.substring(1) else national
        return country.iso to withoutTrunk
    }

    private fun asciiDigits(text: String): String =
        text.filter { it.isDigit() }.map { Character.forDigit(Character.digit(it, DECIMAL), DECIMAL) }.joinToString("")

    private const val DECIMAL = 10

    private fun String.collapseWhitespace(): String = trim().replace(WHITESPACE_RUN, " ")
}
