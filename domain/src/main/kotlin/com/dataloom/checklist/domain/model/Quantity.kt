package com.dataloom.checklist.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * An exact, positive amount stored as thousandths ("milli-units", ADR-004), so 0.1 + 0.2 is
 * exactly 0.3 and values round-trip through Room and JSON without floating-point drift.
 */
@JvmInline
value class Quantity private constructor(val milli: Long) {

    val amount: BigDecimal get() = BigDecimal.valueOf(milli, SCALE).stripTrailingZeros()

    val isWhole: Boolean get() = milli % FACTOR == 0L

    /** "5", "2.5", "0.125": no exponent, no trailing zeros. */
    fun toPlainString(): String = amount.toPlainString()

    override fun toString(): String = toPlainString()

    companion object {
        const val SCALE = 3
        const val FACTOR = 1000L
        val MAX: BigDecimal = BigDecimal("99999")

        /** Reconstructs a stored value; trusted input from the database. */
        fun fromMilli(milli: Long): Quantity {
            require(milli > 0) { "Quantity must be positive, was $milli milli" }
            return Quantity(milli)
        }

        /**
         * Parses user or file input such as "5", "2.5" or "2,5". Returns null when the text is not a
         * number, is not positive, exceeds [MAX], or has more than three decimal places.
         */
        fun parse(text: String): Quantity? {
            val normalized = text.trim().replace(',', '.')
            if (normalized.isEmpty()) return null
            val value = normalized.toBigDecimalOrNull() ?: return null
            if (value.signum() <= 0 || value > MAX) return null
            if (value.stripTrailingZeros().scale() > SCALE) return null
            return Quantity(value.setScale(SCALE, RoundingMode.UNNECESSARY).unscaledValue().longValueExact())
        }

        fun of(whole: Int): Quantity = fromMilli(whole * FACTOR)
    }
}
