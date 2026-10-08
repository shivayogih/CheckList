package com.dataloom.checklist.domain.validation

/** Normalized checklist fields, ready to persist. */
data class ChecklistFields(val title: String, val description: String?)

object ChecklistValidator {

    fun validate(title: String, description: String?): ValidationResult<ChecklistFields> {
        val (cleanTitle, titleError) = titleOf(title)
        val (cleanDescription, descriptionError) =
            optionalText(description, FieldLimits.DESCRIPTION_MAX, ValidationError.DESCRIPTION_TOO_LONG)
        return validationOf(ChecklistFields(cleanTitle, cleanDescription), listOfNotNull(titleError, descriptionError))
    }

    /** Title only, e.g. for the duplicate dialog. */
    fun validateTitle(title: String): ValidationResult<String> {
        val (cleanTitle, error) = titleOf(title)
        return validationOf(cleanTitle, listOfNotNull(error))
    }

    private fun titleOf(raw: String) =
        requiredText(raw, FieldLimits.TITLE_MAX, ValidationError.TITLE_BLANK, ValidationError.TITLE_TOO_LONG)
}
