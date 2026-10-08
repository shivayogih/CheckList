package com.dataloom.checklist.domain.validation

/** The form field an error belongs to, so a screen can show the message under the right input. */
enum class Field { TITLE, DESCRIPTION, ITEM_NAME, NOTES, QUANTITY, UNIT, CATEGORY_NAME, UNIT_LABEL, POSITION }

/**
 * Typed validation failures. The domain never produces user-visible text: the UI maps each code to a
 * string resource, so every language (and the AI and import paths) shares the same rules.
 */
enum class ValidationError(val field: Field) {
    TITLE_BLANK(Field.TITLE),
    TITLE_TOO_LONG(Field.TITLE),
    DESCRIPTION_TOO_LONG(Field.DESCRIPTION),
    ITEM_NAME_BLANK(Field.ITEM_NAME),
    ITEM_NAME_TOO_LONG(Field.ITEM_NAME),
    NOTES_TOO_LONG(Field.NOTES),
    QUANTITY_OUT_OF_RANGE(Field.QUANTITY),

    /** The unit counts whole things (Piece, Dozen...) but the amount has a fraction. */
    QUANTITY_MUST_BE_WHOLE(Field.QUANTITY),

    /** "kg" alone means nothing on a list; an amount without a unit ("3") is fine. */
    UNIT_WITHOUT_QUANTITY(Field.UNIT),

    /** The unit code is not a built-in unit and no custom unit has it. */
    UNKNOWN_UNIT(Field.UNIT),
    CATEGORY_NAME_BLANK(Field.CATEGORY_NAME),
    CATEGORY_NAME_TOO_LONG(Field.CATEGORY_NAME),
    UNIT_LABEL_BLANK(Field.UNIT_LABEL),
    UNIT_LABEL_TOO_LONG(Field.UNIT_LABEL),
    NEGATIVE_POSITION(Field.POSITION),
}
