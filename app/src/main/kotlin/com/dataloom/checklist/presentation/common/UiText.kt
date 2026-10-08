package com.dataloom.checklist.presentation.common

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dataloom.checklist.R
import com.dataloom.checklist.domain.usecase.DomainError
import com.dataloom.checklist.domain.validation.FieldLimits
import com.dataloom.checklist.domain.validation.ValidationError

/**
 * A user-visible message as a string resource plus its arguments. ViewModels hold these instead of
 * text, so they stay free of Context and every message is translated. Integer arguments are
 * formatted for the app language by [LocaleNumbers] when the text is resolved.
 */
data class UiText(@param:StringRes val resId: Int, val args: List<Any> = emptyList())

@Composable
fun UiText.asString(): String = stringResource(resId, *LocaleNumbers.formatArgs(args, currentAppLocale()))

/** Maps a domain validation code to the message shown under its field. */
fun ValidationError.toUiText(): UiText = when (this) {
    ValidationError.TITLE_BLANK -> UiText(R.string.error_title_blank)
    ValidationError.TITLE_TOO_LONG -> tooLong(FieldLimits.TITLE_MAX)
    ValidationError.DESCRIPTION_TOO_LONG -> tooLong(FieldLimits.DESCRIPTION_MAX)
    ValidationError.ITEM_NAME_BLANK -> UiText(R.string.error_item_name_blank)
    ValidationError.ITEM_NAME_TOO_LONG -> tooLong(FieldLimits.ITEM_NAME_MAX)
    ValidationError.NOTES_TOO_LONG -> tooLong(FieldLimits.NOTES_MAX)
    ValidationError.QUANTITY_OUT_OF_RANGE -> UiText(R.string.error_quantity_invalid)
    ValidationError.QUANTITY_MUST_BE_WHOLE -> UiText(R.string.error_quantity_whole)
    ValidationError.UNIT_WITHOUT_QUANTITY -> UiText(R.string.error_unit_without_quantity)
    ValidationError.UNKNOWN_UNIT -> UiText(R.string.error_unit_unknown)
    ValidationError.CATEGORY_NAME_BLANK -> UiText(R.string.error_category_name_blank)
    ValidationError.CATEGORY_NAME_TOO_LONG -> tooLong(FieldLimits.CATEGORY_NAME_MAX)
    ValidationError.UNIT_LABEL_BLANK -> UiText(R.string.error_unit_label_blank)
    ValidationError.UNIT_LABEL_TOO_LONG -> tooLong(FieldLimits.UNIT_LABEL_MAX)
    ValidationError.NEGATIVE_POSITION -> UiText(R.string.error_generic)
    ValidationError.DISPLAY_NAME_BLANK -> UiText(R.string.error_display_name_blank)
    ValidationError.DISPLAY_NAME_TOO_LONG -> tooLong(FieldLimits.DISPLAY_NAME_MAX)
    ValidationError.EMAIL_TOO_LONG -> tooLong(FieldLimits.EMAIL_MAX)
    ValidationError.EMAIL_INVALID -> UiText(R.string.error_email_invalid)
    ValidationError.ADDRESS_TOO_LONG -> tooLong(FieldLimits.ADDRESS_MAX)
    ValidationError.PHONE_INVALID -> UiText(
        R.string.error_phone_invalid,
        listOf(FieldLimits.PHONE_DIGITS_MIN, FieldLimits.PHONE_DIGITS_MAX),
    )
}

/** Maps a refused write to a message; field-level errors are shown next to their fields instead. */
fun DomainError.toUiText(): UiText = when (this) {
    is DomainError.Invalid -> errors.firstOrNull()?.toUiText() ?: UiText(R.string.error_generic)
    is DomainError.InvalidSelection -> errors.firstOrNull()?.toUiText() ?: UiText(R.string.error_generic)
    DomainError.NotFound -> UiText(R.string.error_not_found)
    DomainError.DuplicateName -> UiText(R.string.error_duplicate_name)
    is DomainError.CategoryInUse -> UiText(R.string.error_generic)
    DomainError.SeededCategoryNotDeletable -> UiText(R.string.error_generic)
    DomainError.CustomCategoryHasNoDefaultName -> UiText(R.string.error_generic)
    DomainError.SecureStorageUnavailable -> UiText(R.string.error_secure_storage)
}

private fun tooLong(max: Int) = UiText(R.string.error_too_long, listOf(max))

/** For code outside composition (snackbars shown from an effect collector). */
fun UiText.resolve(resources: Resources): String =
    resources.getString(resId, *LocaleNumbers.formatArgs(args, resources.appLocale()))
