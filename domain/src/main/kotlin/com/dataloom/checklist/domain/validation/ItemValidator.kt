package com.dataloom.checklist.domain.validation

import com.dataloom.checklist.domain.model.Quantity
import com.dataloom.checklist.domain.model.UnitCode
import com.dataloom.checklist.domain.model.UnitDef

/** Normalized checklist item fields, ready to persist. */
data class ItemFields(
    val name: String,
    val quantity: Quantity?,
    val unit: UnitCode?,
    val notes: String?,
)

object ItemValidator {

    /**
     * [unit] is the already resolved unit definition (null when none was chosen). Resolving a code
     * needs the catalog, so use cases do it; this object stays pure and synchronous.
     */
    fun validate(name: String, quantity: Quantity?, unit: UnitDef?, notes: String?): ValidationResult<ItemFields> {
        val (cleanName, nameError) = requiredText(
            name,
            FieldLimits.ITEM_NAME_MAX,
            ValidationError.ITEM_NAME_BLANK,
            ValidationError.ITEM_NAME_TOO_LONG,
        )
        val (cleanNotes, notesError) = optionalText(notes, FieldLimits.NOTES_MAX, ValidationError.NOTES_TOO_LONG)
        val errors = listOfNotNull(nameError, notesError) + QuantityValidator.validate(quantity, unit)
        return validationOf(ItemFields(cleanName, quantity, unit?.code, cleanNotes), errors)
    }
}

object QuantityValidator {

    private val MAX_MILLI: Long = Quantity.MAX.movePointRight(Quantity.SCALE).longValueExact()

    /**
     * An amount alone is fine ("3 bananas"); a unit alone is not; whole-number units reject
     * fractions. [Quantity.parse] already bounds typed input, but values built with
     * [Quantity.fromMilli] (import, AI) can exceed the maximum, so the range is checked here too.
     */
    fun validate(quantity: Quantity?, unit: UnitDef?): List<ValidationError> = buildList {
        if (quantity == null) {
            if (unit != null) add(ValidationError.UNIT_WITHOUT_QUANTITY)
            return@buildList
        }
        if (quantity.milli > MAX_MILLI) add(ValidationError.QUANTITY_OUT_OF_RANGE)
        if (unit != null && !unit.allowsDecimal && !quantity.isWhole) add(ValidationError.QUANTITY_MUST_BE_WHOLE)
    }
}
