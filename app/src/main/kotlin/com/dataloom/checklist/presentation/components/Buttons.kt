package com.dataloom.checklist.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.presentation.theme.Dimens

// Buttons from UI-SPEC section 3: 56 dp tall (grows with the font size so wrapped Kannada or Tamil
// text is never clipped), 28 dp corners, 18 sp SemiBold. Full width inside bottom bars.

private val ButtonHeight = Dimens.ButtonHeight

// Dialog and sheet buttons (the mockups' "sm" button): 48 dp, still the minimum touch target.
private val CompactHeight = Dimens.MinTouchTarget
private val ButtonShape = RoundedCornerShape(28.dp)

@Composable
private fun buttonTextStyle(): TextStyle =
    MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)

/** The filled main action. [leadingIcon] is decorative (the text carries the meaning). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: Painter? = null,
    compact: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        modifier = modifier.heightIn(min = if (compact) CompactHeight else ButtonHeight),
    ) {
        ButtonContent(text, leadingIcon)
    }
}

/** A secondary action with a 2 dp outline. */
@Composable
fun OutlinedActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: Painter? = null,
    compact: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.heightIn(min = if (compact) CompactHeight else ButtonHeight),
    ) {
        ButtonContent(text, leadingIcon)
    }
}

/** A quiet action such as "Skip for now". Same height as the other buttons. */
@Composable
fun TextActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: Painter? = null,
    compact: Boolean = false,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        modifier = modifier.heightIn(min = if (compact) CompactHeight else ButtonHeight),
    ) {
        ButtonContent(text, leadingIcon)
    }
}

/**
 * The destructive confirm button ("Delete"): filled with the error colour, never colour alone since the
 * label says what it does. Same 56 dp (or [compact] 48 dp) size and shape as the other buttons.
 */
@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: Painter? = null,
    compact: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
        modifier = modifier.heightIn(min = if (compact) CompactHeight else ButtonHeight),
    ) {
        ButtonContent(text, leadingIcon)
    }
}

@Preview(showBackground = true)
@Composable
private fun ButtonsPreview() {
    PreviewSurface {
        PrimaryButton("Create checklist", onClick = {}, modifier = Modifier.fillMaxWidth())
        OutlinedActionButton("Create with AI", onClick = {}, modifier = Modifier.fillMaxWidth())
        TextActionButton("Skip for now", onClick = {})
        DangerButton("Delete", onClick = {}, compact = true)
    }
}

@Composable
private fun ButtonContent(text: String, leadingIcon: Painter?) {
    if (leadingIcon != null) {
        Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
    }
    Text(text, style = buttonTextStyle(), textAlign = TextAlign.Center)
}
