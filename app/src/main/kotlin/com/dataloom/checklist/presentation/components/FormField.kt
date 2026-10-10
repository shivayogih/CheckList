package com.dataloom.checklist.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.R
import com.dataloom.checklist.presentation.common.bringIntoViewWhenFocused
import com.dataloom.checklist.presentation.common.formatCount
import com.dataloom.checklist.presentation.theme.Dimens

/**
 * A text field the way the mockups draw it (UI-SPEC section 3, SC18): the **label above** the box (16 sp
 * SemiBold), a box at least 60 dp tall (100 dp when [multiLine]) with a 2 dp outline and 12 dp corners.
 * Focus raises the border to 3 dp primary. An error turns the label and a 3 dp border red and shows the
 * [errorText] under the box with an error icon, so the error is never colour alone. [helperText]
 * (12 sp) shows under the box when there is no error.
 *
 * [maxLength] adds a "length / max" counter on the end under the box (hidden from TalkBack: the limit is also
 * in the "too long" error); it only displays, the caller still enforces the limit (CL-280).
 *
 * [leading] puts a separate box before the text box in the same row, sharing the label and error.
 *
 * The label is an extra accessibility name for the field, and the error text is exposed as the field's
 * `error` semantics so TalkBack announces it.
 */
@Composable
fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    helperText: String? = null,
    errorText: String? = null,
    multiLine: Boolean = false,
    enabled: Boolean = true,
    maxLength: Int? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val focused = interactionSource.collectIsFocusedAsState().value
    val hasError = errorText != null
    val borderColor = when {
        hasError -> colors.error
        focused -> colors.primary
        else -> colors.outline
    }
    val borderWidth = if (hasError || focused) Dimens.Border3 else Dimens.Border2
    Column(modifier = modifier.fillMaxWidth().bringIntoViewWhenFocused()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (hasError) colors.error else colors.onSurface,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        val textField: @Composable (Modifier) -> Unit = { fieldModifier ->
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = !multiLine,
                minLines = if (multiLine) 3 else 1,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                interactionSource = interactionSource,
                modifier = fieldModifier
                    .semantics {
                        contentDescription = label
                        if (errorText != null) error(errorText)
                    },
                decorationBox = { inner ->
                    FieldBox(value.isEmpty(), placeholder, multiLine, BorderStroke(borderWidth, borderColor), inner)
                },
            )
        }
        WithLeading(leading, textField)
        FormFieldMessage(errorText, helperText)
        FormFieldCounter(value, maxLength)
    }
}

/** The outlined box around the text, with the placeholder while it is empty. */
@Composable
private fun FieldBox(
    empty: Boolean,
    placeholder: String?,
    multiLine: Boolean,
    border: BorderStroke,
    inner: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (multiLine) Dimens.FieldMultiLineHeight else Dimens.FieldHeight)
            .border(border, RoundedCornerShape(Dimens.Corner12))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = if (multiLine) Alignment.TopStart else Alignment.CenterStart,
    ) {
        if (empty && placeholder != null) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        inner()
    }
}

/** The text box alone, or after [leading] in one row (CL-380: the phone country button); both grow together. */
@Composable
private fun WithLeading(leading: (@Composable () -> Unit)?, textField: @Composable (Modifier) -> Unit) {
    if (leading == null) {
        textField(Modifier.fillMaxWidth())
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            leading()
            textField(Modifier.weight(1f))
        }
    }
}

@Composable
private fun FormFieldMessage(errorText: String?, helperText: String?) {
    val colors = MaterialTheme.colorScheme
    if (errorText != null) {
        Row(
            modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_error),
                contentDescription = null,
                tint = colors.error,
                modifier = Modifier.padding(top = 2.dp).size(18.dp),
            )
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = colors.error,
            )
        }
    } else if (helperText != null) {
        Text(
            text = helperText,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp),
        )
    }
}

@Composable
private fun FormFieldCounter(value: String, maxLength: Int?) {
    val colors = MaterialTheme.colorScheme
    if (maxLength != null) {
        val length = value.inputLength()
        Text(
            text = stringResource(R.string.input_counter, formatCount(length), formatCount(maxLength)),
            style = MaterialTheme.typography.bodySmall,
            color = if (length > maxLength) colors.error else colors.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth().padding(end = 4.dp, top = 4.dp).clearAndSetSemantics { },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FormFieldPreview() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FormField(
                label = "Checklist name *",
                value = "Diwali Shopping",
                onValueChange = {},
                helperText = "For example: Diwali Shopping",
            )
            FormField(label = "Checklist name *", value = "", onValueChange = {}, errorText = "Please enter a name")
            FormField(
                label = "Description (optional)",
                value = "",
                onValueChange = {},
                placeholder = "Who is it for? When do you need it?",
                multiLine = true,
            )
        }
    }
}
