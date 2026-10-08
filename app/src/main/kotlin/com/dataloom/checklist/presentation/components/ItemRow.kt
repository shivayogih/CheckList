package com.dataloom.checklist.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.R
import com.dataloom.checklist.presentation.theme.CheckListText
import com.dataloom.checklist.presentation.theme.Dimens

/**
 * One item of a checklist (UI-SPEC section 3). At least 72 dp tall; the **whole row** toggles, not only
 * the 32 dp box. Layout: checkbox, then the name (18 sp), then a line with the [unitText] chip and, when
 * ticked, the "Completed" label, then an optional [note] line with a note icon; at the end an optional
 * [trailingThumbnail] and the visible "⋮" [menu].
 *
 * A ticked item shows four cues, never colour alone: the tick, a strikethrough, muted text and the
 * "Completed" label (plus a grey unit chip).
 *
 * Accessibility: the row is one TalkBack item. Pass [accessibilityLabel] ("Rice, 5 kilograms,
 * completed") to replace the default reading of the merged texts; [customActions] adds TalkBack actions
 * (edit, delete, move); the [menu] button stays a separate item.
 *
 * @param trailingThumbnail slot for the item photo (CL-210 photos). Nothing is drawn when null.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemRow(
    name: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    unitText: String? = null,
    note: String? = null,
    completedLabel: String = "",
    accessibilityLabel: String? = null,
    customActions: List<CustomAccessibilityAction> = emptyList(),
    trailingThumbnail: (@Composable () -> Unit)? = null,
    menu: @Composable () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.ItemRowMinHeight),
            verticalAlignment = Alignment.Top,
        ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = Dimens.ItemRowMinHeight)
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
                .semantics(mergeDescendants = true) {
                    if (accessibilityLabel != null) contentDescription = accessibilityLabel
                    if (customActions.isNotEmpty()) this.customActions = customActions
                }
                .padding(start = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppCheckbox(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 2.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = name,
                    style = CheckListText.rowTitle,
                    color = if (checked) colors.onSurfaceVariant else colors.onSurface,
                    textDecoration = if (checked) TextDecoration.LineThrough else null,
                )
                if (unitText != null || checked) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (unitText != null) UnitChip(unitText, done = checked)
                        if (checked && completedLabel.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_task_alt),
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = completedLabel,
                                    style = CheckListText.unitChip,
                                    color = colors.primary,
                                )
                            }
                        }
                    }
                }
                if (note != null) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_sticky_note),
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.padding(top = 3.dp).size(18.dp),
                        )
                        Text(note, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                }
            }
            if (trailingThumbnail != null) trailingThumbnail()
        }
        // The menu is outside the toggling area: tapping "⋮" must not tick the item.
        Box(modifier = Modifier.padding(top = 4.dp)) { menu() }
        }
        HorizontalDivider(color = colors.outlineVariant)
    }
}

@Preview(showBackground = true)
@Composable
private fun ItemRowPreview() {
    PreviewSurface {
        ItemRow(name = "Rice", checked = false, onCheckedChange = {}, unitText = "5 KG", completedLabel = "Completed", menu = { PreviewMenu("Rice") })
        ItemRow(
            name = "Sugar",
            checked = true,
            onCheckedChange = {},
            unitText = "2 KG",
            completedLabel = "Completed",
            menu = { PreviewMenu("Sugar") },
        )
        ItemRow(
            name = "Dry fruits",
            checked = false,
            onCheckedChange = {},
            unitText = "2 KG",
            note = "For guests",
            completedLabel = "Completed",
            menu = { PreviewMenu("Dry fruits") },
        )
    }
}

@Composable
private fun PreviewMenu(name: String) {
    OverflowMenu("More options for $name", listOf(MenuAction("Delete") {}))
}
