package com.dataloom.checklist.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.R
import com.dataloom.checklist.presentation.theme.Dimens

/**
 * The look of every dialog (UI-SPEC section 3): `surfaceContainer`, 28 dp corners, a 24 sp SemiBold title,
 * the body in the secondary colour and the buttons at the end, wrapping onto a second line when the
 * labels are long (Tamil, Kannada, 200 % text). Use it inside [BasicAlertDialog]; it is a plain composable
 * so screenshot tests can draw it without a dialog window.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DialogSurface(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.widthIn(min = 280.dp, max = 560.dp),
        shape = RoundedCornerShape(Dimens.Corner28),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            content()
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) { actions() }
        }
    }
}

/**
 * Confirmation for a destructive action, with explicit verbs ("Delete" / "Cancel") rather than
 * "OK" (accessibility.md). With [destructive] the confirm button is filled with the error colour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        ConfirmDialogContent(title, message, confirmLabel, onConfirm, onDismiss, destructive)
    }
}

/** The card inside [ConfirmDialog], separate so a screenshot can draw it. */
@Composable
internal fun ConfirmDialogContent(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean,
    cancelLabel: String = stringResource(R.string.action_cancel),
) {
    DialogSurface(
        title = title,
        actions = {
            TextActionButton(cancelLabel, onClick = onDismiss, compact = true)
            if (destructive) {
                DangerButton(confirmLabel, onClick = onConfirm, compact = true)
            } else {
                TextActionButton(confirmLabel, onClick = onConfirm, compact = true)
            }
        },
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A one-field form in a dialog (rename a checklist, create a category). [errorText] shows under the field;
 * [enabled] is the confirm button (false while the value would be refused); [maxLength] adds a counter.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    BasicAlertDialog(onDismissRequest = onDismiss) {
        DialogSurface(
            title = title,
            actions = {
                TextActionButton(stringResource(R.string.action_cancel), onClick = onDismiss, compact = true)
                TextActionButton(confirmLabel, onClick = onConfirm, enabled = enabled, compact = true)
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(
                    label = label,
                    value = value,
                    onValueChange = onValueChange,
                    errorText = errorText,
                    maxLength = maxLength,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                )
                extraContent()
            }
        }
    }
}

/** A dialog title marked as a heading, so TalkBack announces what the dialog is about first. */
@Composable
fun DialogTitle(text: String) {
    Text(text, modifier = Modifier.semantics { heading() })
}

@Preview(showBackground = true)
@Composable
private fun DialogPreview() {
    PreviewSurface {
        ConfirmDialogContent(
            title = "Delete 'Goa Trip'?",
            message = "This removes the checklist and all its 24 items. You can't undo this.",
            confirmLabel = "Delete",
            onConfirm = {},
            onDismiss = {},
            destructive = true,
            cancelLabel = "Cancel",
        )
    }
}
