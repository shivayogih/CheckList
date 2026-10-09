package com.dataloom.checklist.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.R
import com.dataloom.checklist.presentation.theme.Dimens

/*
 * Toggles from the mockups (UI-SPEC section 3, SC19): a 32 dp checkbox with a 3 dp border and 8 dp
 * corners, a 56 x 32 switch with a check in the thumb, and a 28 dp radio. Each one has two uses:
 *  - decorative (onChange = null): a row owns the click and the semantics, as `CheckRow` does;
 *  - standalone (onChange set): the control is its own toggleable with a 48 dp touch target.
 */

private val TouchTarget = Dimens.MinTouchTarget

/** The tick box of the mockups. Pass [onCheckedChange] only when no parent row handles the click. */
@Composable
fun AppCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val box = @Composable {
        val colors = MaterialTheme.colorScheme
        Box(
            modifier = Modifier
                .size(Dimens.CheckboxSize)
                .clip(RoundedCornerShape(Dimens.Corner8))
                .background(if (checked) colors.primary else colors.background)
                .border(
                    Dimens.Border2,
                    if (checked) colors.primary else colors.outline,
                    RoundedCornerShape(Dimens.Corner8),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
    if (onCheckedChange == null) {
        Box(modifier, contentAlignment = Alignment.Center) { box() }
    } else {
        Box(
            modifier = modifier
                .size(TouchTarget)
                .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange),
            contentAlignment = Alignment.Center,
        ) { box() }
    }
}

/** The 56 x 32 switch of the mockups; a check mark sits in the thumb when it is on. */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val track = @Composable {
        val colors = MaterialTheme.colorScheme
        Box(
            modifier = Modifier
                .size(Dimens.SwitchWidth, Dimens.SwitchHeight)
                .clip(CircleShape)
                .background(if (checked) colors.primary else colors.surfaceVariant)
                .border(Dimens.Border2, if (checked) colors.primary else colors.outline, CircleShape),
        ) {
            if (checked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = (-4).dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(colors.onPrimary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = 4.dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(colors.outline),
                )
            }
        }
    }
    if (onCheckedChange == null) {
        Box(modifier, contentAlignment = Alignment.Center) { track() }
    } else {
        Box(
            modifier = modifier
                .heightIn(min = TouchTarget)
                .widthIn(min = Dimens.SwitchWidth)
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
            contentAlignment = Alignment.Center,
        ) { track() }
    }
}

/** The 28 dp radio circle; a dot shows when [selected]. */
@Composable
fun AppRadio(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val circle = @Composable {
        val colors = MaterialTheme.colorScheme
        Box(
            modifier = Modifier
                .size(Dimens.RadioSize)
                .border(Dimens.Border3, if (selected) colors.primary else colors.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(colors.primary))
            }
        }
    }
    if (onClick == null) {
        Box(modifier, contentAlignment = Alignment.Center) { circle() }
    } else {
        Box(
            modifier = modifier
                .size(TouchTarget)
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { circle() }
    }
}

@Preview(showBackground = true)
@Composable
private fun TogglesPreview() {
    PreviewSurface {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppCheckbox(checked = false, onCheckedChange = {})
            AppCheckbox(checked = true, onCheckedChange = {})
            AppSwitch(checked = false, onCheckedChange = {})
            AppSwitch(checked = true, onCheckedChange = {})
            AppRadio(selected = false, onClick = {})
            AppRadio(selected = true, onClick = {})
        }
    }
}
