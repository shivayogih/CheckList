package com.dataloom.checklist.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.R
import com.dataloom.checklist.presentation.theme.CheckListText
import com.dataloom.checklist.presentation.theme.Dimens

/**
 * The quantity control (UI-SPEC section 3): round "-" and "+" buttons in `primaryContainer` (64 dp, or
 * 52 dp when [compact]) with a value box between them (3 dp primary border, 30 sp Bold; 24 sp compact).
 *
 * The value is **text**, not a number: units such as kg and litre allow decimals and the user may type
 * "1.5", so the box is an editable decimal field. The buttons only ask the caller to change the
 * value ([onDecrement], [onIncrement]); the caller owns the parsing, the step and the rules per unit.
 * [isError] turns the border red and sets the error semantics ([errorText] is what TalkBack reads).
 *
 * @param valueDescription accessibility name of the box ("Quantity"); [decrementDescription] and
 * [incrementDescription] name the buttons ("Less", "More").
 */
@Composable
fun QuantityStepper(
    value: String,
    onValueChange: (String) -> Unit,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    valueDescription: String,
    decrementDescription: String,
    incrementDescription: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    isError: Boolean = false,
    errorText: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val buttonSize = if (compact) Dimens.StepperButtonCompact else Dimens.StepperButton
    val borderColor = if (isError) colors.error else colors.primary
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        StepperButton(R.drawable.ic_remove, decrementDescription, buttonSize, onDecrement)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = (if (compact) MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) else CheckListText.stepperValue)
                .copy(color = colors.onSurface, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .widthIn(min = if (compact) Dimens.StepperValueMinWidthCompact else Dimens.StepperValueMinWidth)
                .semantics {
                    contentDescription = valueDescription
                    if (isError && errorText != null) error(errorText)
                },
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .heightIn(min = if (compact) Dimens.StepperValueHeightCompact else Dimens.StepperValueHeight)
                        .widthIn(min = if (compact) Dimens.StepperValueMinWidthCompact else Dimens.StepperValueMinWidth)
                        .clip(RoundedCornerShape(Dimens.Corner14))
                        .border(Dimens.Border3, borderColor, RoundedCornerShape(Dimens.Corner14))
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) { inner() }
            },
        )
        StepperButton(R.drawable.ic_add, incrementDescription, buttonSize, onIncrement)
    }
}

@Composable
private fun StepperButton(icon: Int, description: String, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.primaryContainer)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = colors.onPrimaryContainer,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun QuantityStepperPreview() {
    PreviewSurface {
        QuantityStepper(
            value = "5",
            onValueChange = {},
            onDecrement = {},
            onIncrement = {},
            valueDescription = "Quantity",
            decrementDescription = "Less",
            incrementDescription = "More",
            modifier = Modifier.fillMaxWidth(),
        )
        QuantityStepper(
            value = "1.5",
            onValueChange = {},
            onDecrement = {},
            onIncrement = {},
            valueDescription = "Quantity",
            decrementDescription = "Less",
            incrementDescription = "More",
            compact = true,
            isError = true,
            errorText = "Pieces must be a whole number",
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
    }
}
