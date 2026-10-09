package com.dataloom.checklist.domain.validation

import com.dataloom.checklist.domain.model.Quantity
import java.math.BigDecimal
import java.math.RoundingMode

private const val RADIX = 10

/** Outcome of reading a typed or pasted amount. */
sealed interface QuantityParse {
    /** Nothing typed: no quantity (a valid state, the amount is optional). */
    data object Empty : QuantityParse

    data class Valid(val quantity: Quantity) : QuantityParse

    /** [error] is one of the QUANTITY_* codes of [ValidationError]. */
    data class Invalid(val error: ValidationError) : QuantityParse
}

/**
 * The one rule set for amounts (CL-280), used by the item forms, import and AI.
 *
 * Accepted: digits, optionally one decimal separator ("." or ","; also the Arabic decimal separator
 * U+066B), digits from any Unicode script ("१२" and "٣" read as 12 and 3), surrounding spaces.
 * Rejected: letters, signs ("-3", "+3"), exponents ("1e5"), grouping, a second separator, symbols.
 * Range: more than zero and at most [Quantity.MAX]; at most three decimal places (milli-units).
 *
 * "1,500" is read as 1.5 because "," is the decimal separator in several of the app's locales;
 * grouping separators are not supported and the amount field never produces them.
 */
object QuantityInput {

    private const val MAX_INTEGER_DIGITS = 5
    private const val MAX_TEXT = 64

    /**
     * The keyboard filter: keeps digits (any script, converted to ASCII 0-9) and the first decimal
     * separator (converted to "."), drops everything else, and stops at five integer and three
     * decimal digits. Safe to apply on every change event, and idempotent.
     */
    fun sanitize(raw: String): String {
        val out = StringBuilder()
        var separatorSeen = false
        var integerDigits = 0
        var fractionDigits = 0
        for (ch in raw) {
            val digit = digitOf(ch)
            when {
                digit >= 0 && !separatorSeen -> if (integerDigits < MAX_INTEGER_DIGITS) {
                    out.append('0' + digit)
                    integerDigits++
                }
                digit >= 0 -> if (fractionDigits < Quantity.SCALE) {
                    out.append('0' + digit)
                    fractionDigits++
                }
                isSeparator(ch) && !separatorSeen -> {
                    out.append('.')
                    separatorSeen = true
                }
            }
        }
        return out.toString()
    }

    fun parse(text: String): QuantityParse {
        val trimmed = InputText.normalize(text)
        if (trimmed.isEmpty()) return QuantityParse.Empty
        val ascii = asciiNumber(trimmed) ?: return QuantityParse.Invalid(ValidationError.QUANTITY_NOT_A_NUMBER)
        // Only digits and one separator are left, so a very long value is a huge number, not garbage.
        if (ascii.length > MAX_TEXT) return QuantityParse.Invalid(ValidationError.QUANTITY_OUT_OF_RANGE)
        val value = BigDecimal(ascii)
        return when {
            value.signum() == 0 -> QuantityParse.Invalid(ValidationError.QUANTITY_NOT_POSITIVE)
            value > Quantity.MAX -> QuantityParse.Invalid(ValidationError.QUANTITY_OUT_OF_RANGE)
            value.stripTrailingZeros().scale() > Quantity.SCALE ->
                QuantityParse.Invalid(ValidationError.QUANTITY_TOO_PRECISE)
            else -> {
                val milli = value.setScale(Quantity.SCALE, RoundingMode.UNNECESSARY).unscaledValue().longValueExact()
                QuantityParse.Valid(Quantity.fromMilli(milli))
            }
        }
    }

    /**
     * The text with every digit as 0-9 and the separator as ".", or null when it holds anything else
     * (letters, signs, a second separator, spaces inside) or no digit at all.
     */
    private fun asciiNumber(text: String): String? {
        var separatorSeen = false
        var digits = 0
        val ascii = StringBuilder(text.length)
        for (ch in text) {
            val digit = digitOf(ch)
            when {
                digit >= 0 -> {
                    ascii.append('0' + digit)
                    digits++
                }
                isSeparator(ch) && !separatorSeen -> {
                    ascii.append('.')
                    separatorSeen = true
                }
                else -> return null
            }
        }
        return ascii.toString().takeIf { digits > 0 }
    }

    /** Null for empty or invalid input; for callers that only need the value. */
    fun parseOrNull(text: String): Quantity? = (parse(text) as? QuantityParse.Valid)?.quantity

    private fun digitOf(ch: Char): Int = if (ch.isDigit()) Character.digit(ch, RADIX) else -1

    private fun isSeparator(ch: Char): Boolean = ch == '.' || ch == ',' || ch == '\u066B'
}

/** Phone number typing filter (CL-280). Validation stays in [ProfileValidator]. */
object PhoneInput {

    private const val SEPARATORS = " -()."

    /** Separators may not lead (except "(") and a space may not follow a space. */
    private fun isAllowedSeparator(ch: Char, out: StringBuilder): Boolean =
        ch in SEPARATORS && (out.isNotEmpty() || ch == '(') && !(ch == ' ' && out.lastOrNull() == ' ')

    /**
     * Keeps digits (any script, converted to ASCII), a leading "+" and the separators people type
     * inside numbers; drops letters, "#", "*" and every other symbol. Capped at [FieldLimits.PHONE_MAX].
     */
    fun sanitize(raw: String): String {
        val out = StringBuilder()
        for (ch in raw) {
            val digit = if (ch.isDigit()) Character.digit(ch, RADIX) else -1
            when {
                digit >= 0 -> out.append('0' + digit)
                ch == '+' && out.isEmpty() -> out.append('+')
                isAllowedSeparator(ch, out) -> out.append(ch)
            }
            if (out.length >= FieldLimits.PHONE_MAX) break
        }
        return out.toString()
    }
}
