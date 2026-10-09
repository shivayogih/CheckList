package com.dataloom.checklist.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.dataloom.checklist.R

/**
 * Confirmation for a destructive action, with explicit verbs ("Delete" / "Cancel") rather than
 * "OK" (accessibility.md).
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { DialogTitle(title) },
        text = { Text(message, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * A one-field form in a dialog (rename a checklist, create a category). [errorText] shows under the field;
 * [enabled] is the confirm button (false while the value would be refused); [maxLength] adds a counter.
 */
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    value: String,
    errorText: String?,
    confirmLabel: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    maxLength: Int? = null,
    extraContent: @Composable () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { DialogTitle(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text(label) },
                    singleLine = true,
                    isError = errorText != null,
                    supportingText = fieldSupportingText(errorText, value.inputLength(), maxLength),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { if (errorText != null) error(errorText) },
                )
                extraContent()
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = enabled) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** A dialog title marked as a heading, so TalkBack announces what the dialog is about first. */
@Composable
fun DialogTitle(text: String) {
    Text(text, modifier = Modifier.semantics { heading() })
}
