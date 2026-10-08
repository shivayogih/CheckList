package com.dataloom.checklist.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** What the user typed in the profile form. All fields are optional. */
data class ProfileFieldValues(
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
)

/** The texts of the profile form, resolved by the screen (it owns the string resources and the 7 translations). */
data class ProfileFieldLabels(
    val name: String,
    val phone: String,
    val email: String,
    val address: String = "",
    val nameHelper: String? = null,
)

/** Inline error texts, `null` when the field is valid. */
data class ProfileFieldErrors(
    val name: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
)

/**
 * The four profile fields in one composable, so onboarding (00e/00f) and the Profile screen (19) look and
 * behave the same (SC20). Name, phone, email and, when [showAddress] is true, address. The address is
 * off by default: the domain `UserProfile` has no address yet (UI gap report decision 2).
 */
@Composable
fun ProfileFields(
    values: ProfileFieldValues,
    onValuesChange: (ProfileFieldValues) -> Unit,
    labels: ProfileFieldLabels,
    modifier: Modifier = Modifier,
    errors: ProfileFieldErrors = ProfileFieldErrors(),
    showAddress: Boolean = false,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FormField(
            label = labels.name,
            value = values.name,
            onValueChange = { onValuesChange(values.copy(name = it)) },
            helperText = labels.nameHelper,
            errorText = errors.name,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        )
        FormField(
            label = labels.phone,
            value = values.phone,
            onValueChange = { onValuesChange(values.copy(phone = it)) },
            errorText = errors.phone,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
        )
        FormField(
            label = labels.email,
            value = values.email,
            onValueChange = { onValuesChange(values.copy(email = it)) },
            errorText = errors.email,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                capitalization = KeyboardCapitalization.None,
                imeAction = if (showAddress) ImeAction.Next else ImeAction.Done,
            ),
        )
        if (showAddress) {
            FormField(
                label = labels.address,
                value = values.address,
                onValueChange = { onValuesChange(values.copy(address = it)) },
                errorText = errors.address,
                multiLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileFieldsPreview() {
    PreviewSurface {
        ProfileFields(
            values = ProfileFieldValues(name = "Kamala Hiremath", phone = "+91 98450 12345", email = "kamala@example"),
            onValuesChange = {},
            labels = ProfileFieldLabels(name = "Name", phone = "Phone", email = "Email", nameHelper = "Optional. Shown on PDFs only if you choose."),
            errors = ProfileFieldErrors(email = "Enter a valid email like name@example.com"),
        )
    }
}
