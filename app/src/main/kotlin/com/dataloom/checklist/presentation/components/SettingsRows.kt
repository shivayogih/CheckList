package com.dataloom.checklist.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.R
import com.dataloom.checklist.presentation.theme.CheckListText
import com.dataloom.checklist.presentation.theme.Dimens
import com.dataloom.checklist.presentation.theme.bannerContainer

/*
 * Rows for Settings, Language, Text size and Export (UI-SPEC section 3, SC9). Every row is one TalkBack
 * item: the title, the value or subtitle and the state are read together.
 */

/** A group title such as "General" or "Your data": 16 sp Bold in the primary colour, a heading for TalkBack. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = CheckListText.sectionHeader,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 6.dp)
            .semantics { heading() },
    )
}

/**
 * A row that opens something: at least 64 dp, a 26 dp [icon], the [title] at 18 sp, a [value] at 16 sp
 * under it and a chevron at the end. Leave [onClick] null for a row that only shows information.
 */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.RowMinHeight)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .semantics(mergeDescendants = true) { }
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(Dimens.SettingsIcon))
            }
            RowText(title, value, Modifier.weight(1f))
            if (trailing != null) {
                trailing()
            } else if (onClick != null) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = colors.onSurface,
                    modifier = Modifier.size(Dimens.Icon),
                )
            }
        }
        if (showDivider) HorizontalDivider(color = colors.outlineVariant)
    }
}

/** A setting with an on/off switch. The whole row toggles; the 56 x 32 switch is only the picture. */
@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
    showDivider: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.RowMinHeight)
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                .semantics(mergeDescendants = true) { }
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(Dimens.SettingsIcon))
            }
            RowText(title, subtitle, Modifier.weight(1f))
            AppSwitch(checked = checked, onCheckedChange = null)
        }
        if (showDivider) HorizontalDivider(color = colors.outlineVariant)
    }
}

/**
 * One choice of a single-choice list (Language, Text size): a 28 dp radio, [title] and [subtitle]. The
 * chosen row gets the info tint, a check at the end and the radio dot, so it is never colour alone.
 */
@Composable
fun RadioRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showDivider: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.RowMinHeight)
                .clip(RoundedCornerShape(Dimens.Corner12))
                .background(if (selected) colors.bannerContainer else colors.background)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
                .semantics(mergeDescendants = true) { }
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppRadio(selected = selected, onClick = null)
            RowText(title, subtitle, Modifier.weight(1f), big = true)
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(Dimens.Icon),
                )
            }
        }
        if (showDivider && !selected) HorizontalDivider(color = colors.outlineVariant)
    }
}

/**
 * A card-style choice with a 2 dp border (Export format: backup file or PDF): [icon], [title],
 * [subtitle] and a radio at the end. Selected = 3 dp primary border and the info tint.
 */
@Composable
fun OptionCard(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: Painter? = null,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.Corner14),
        color = if (selected) colors.bannerContainer else colors.background,
        border = BorderStroke(if (selected) Dimens.Border3 else Dimens.Border2, if (selected) colors.primary else colors.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = Dimens.RowMinHeight)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
                .semantics(mergeDescendants = true) { }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(Dimens.SettingsIcon))
            }
            RowText(title, subtitle, Modifier.weight(1f))
            AppRadio(selected = selected, onClick = null)
        }
    }
}

@Composable
private fun RowText(title: String, secondary: String?, modifier: Modifier = Modifier, big: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier) {
        Text(
            text = title,
            style = if (big) MaterialTheme.typography.titleMedium else CheckListText.rowTitle,
            color = colors.onSurface,
        )
        if (secondary != null) {
            Text(secondary, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsRowsPreview() {
    PreviewSurface {
        SectionHeader("General")
        SettingsRow("Language", icon = painterResource(R.drawable.ic_translate), value = "System default", onClick = {})
        SettingsRow("Text size", icon = painterResource(R.drawable.ic_format_size), value = "Large", onClick = {})
        SettingsSwitchRow("High contrast", checked = false, onCheckedChange = {}, icon = painterResource(R.drawable.ic_contrast))
        RadioRow("English", selected = true, onSelect = {}, subtitle = "English")
        RadioRow("Kannada", selected = false, onSelect = {}, subtitle = "Kannada")
        OptionCard("Backup file (.json)", selected = true, onSelect = {}, subtitle = "Move to another phone", icon = painterResource(R.drawable.ic_data_object))
    }
}
