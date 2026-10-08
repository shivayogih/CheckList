package com.dataloom.checklist.domain.validation

import com.dataloom.checklist.domain.model.UserProfile

/**
 * Profile rules. Every field is optional (CL-250): blank input becomes null, and a profile with no
 * values is valid (it is simply empty). Normalization: the display name is trimmed and inner whitespace (line breaks too)
 * collapsed to one space; blank optional fields become null; phone digits typed on an Indic keyboard
 * (for example "९८७") are stored as ASCII digits so the number dials and exports the same everywhere.
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
    ): ValidationResult<UserProfile> {
        val (cleanName, nameError) = optionalText(
            displayName?.collapseWhitespace(),
            FieldLimits.DISPLAY_NAME_MAX,
            ValidationError.DISPLAY_NAME_TOO_LONG,
        )
        val (cleanEmail, emailError) = emailOf(email)
        val (cleanPhone, phoneError) = phoneOf(phone)
        val (cleanAddress, addressError) = optionalText(
            address?.let(::cleanAddress),
            FieldLimits.ADDRESS_MAX,
            ValidationError.ADDRESS_TOO_LONG,
        )
        return validationOf(
            UserProfile(cleanName, cleanEmail, cleanPhone, cleanAddress),
            listOfNotNull(nameError, emailError, phoneError, addressError),
        )
    }

    /**
     * Addresses keep their line breaks (one line per row of the form) but each line is trimmed, inner
     * whitespace collapsed, blank lines dropped and other control characters removed.
     */
    private fun cleanAddress(raw: String): String =
        raw.replace("\r\n", "\n").replace('\r', '\n').split('\n')
            .map { line -> line.filterNot { it.isISOControl() && !it.isWhitespace() }.collapseWhitespace() }
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

    /** An optional leading "+", then digits and common separators; 7 to 15 digits (the E.164 maximum). */
    private fun phoneOf(raw: String?): Pair<String?, ValidationError?> {
        val typed = raw?.collapseWhitespace().trimToNull() ?: return null to null
        if (typed.codePointLength() > FieldLimits.PHONE_MAX) return typed to ValidationError.PHONE_INVALID
        val phone = typed.map { c -> if (c.isDigit()) Character.forDigit(Character.digit(c, 10), 10) else c }
            .joinToString("")
        val body = phone.removePrefix("+")
        val digits = body.count { it in '0'..'9' }
        val valid = body.all { it in '0'..'9' || it in PHONE_SEPARATORS } &&
            (body.firstOrNull() == '(' || body.firstOrNull() in '0'..'9') &&
            digits in FieldLimits.PHONE_DIGITS_MIN..FieldLimits.PHONE_DIGITS_MAX
        return phone to if (valid) null else ValidationError.PHONE_INVALID
    }

    private fun String.collapseWhitespace(): String = trim().replace(WHITESPACE_RUN, " ")
}
