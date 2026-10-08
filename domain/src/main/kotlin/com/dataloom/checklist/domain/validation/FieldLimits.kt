package com.dataloom.checklist.domain.validation

/**
 * Length limits from the architecture (section 4). Lengths count Unicode code points, not UTF-16
 * units, so an emoji or a supplementary-plane character counts as one, closer to what the user sees.
 */
object FieldLimits {
    const val TITLE_MAX = 100
    const val DESCRIPTION_MAX = 500
    const val ITEM_NAME_MAX = 80
    const val NOTES_MAX = 500
    const val CATEGORY_NAME_MAX = 50

    /** Unit labels sit next to numbers in tight rows, so they stay short. */
    const val UNIT_LABEL_MAX = 20

    /** Profile display name (Phase 5): short enough for a PDF header line. */
    const val DISPLAY_NAME_MAX = 50

    /** RFC 5321 limits for an address and its local part. */
    const val EMAIL_MAX = 254
    const val EMAIL_LOCAL_PART_MAX = 64

    /** Postal address (CL-250): a few lines, counted in code points including line breaks. */
    const val ADDRESS_MAX = 200

    /** Typed phone number including "+" and separators. */
    const val PHONE_MAX = 25

    /** Digits in a phone number; 15 is the E.164 maximum. */
    const val PHONE_DIGITS_MIN = 7
    const val PHONE_DIGITS_MAX = 15
}

internal fun String.codePointLength(): Int = codePointCount(0, length)

/** Trims and maps blank text to null: an empty description or note means "none". */
internal fun String?.trimToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

/** Checks a required text field; returns the trimmed text and at most one error. */
internal fun requiredText(
    raw: String,
    max: Int,
    blank: ValidationError,
    tooLong: ValidationError,
): Pair<String, ValidationError?> {
    val text = raw.trim()
    val error = when {
        text.isEmpty() -> blank
        text.codePointLength() > max -> tooLong
        else -> null
    }
    return text to error
}

/** Checks an optional text field; blank becomes null. */
internal fun optionalText(raw: String?, max: Int, tooLong: ValidationError): Pair<String?, ValidationError?> {
    val text = raw.trimToNull()
    return text to if (text != null && text.codePointLength() > max) tooLong else null
}
