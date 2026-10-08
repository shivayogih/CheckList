package com.dataloom.checklist.domain.validation

object CategoryValidator {

    fun validateName(name: String): ValidationResult<String> {
        val (clean, error) = requiredText(
            name,
            FieldLimits.CATEGORY_NAME_MAX,
            ValidationError.CATEGORY_NAME_BLANK,
            ValidationError.CATEGORY_NAME_TOO_LONG,
        )
        return validationOf(clean, listOfNotNull(error))
    }
}

object UnitValidator {

    fun validateLabel(label: String): ValidationResult<String> {
        val (clean, error) = requiredText(
            label,
            FieldLimits.UNIT_LABEL_MAX,
            ValidationError.UNIT_LABEL_BLANK,
            ValidationError.UNIT_LABEL_TOO_LONG,
        )
        return validationOf(clean, listOfNotNull(error))
    }
}
