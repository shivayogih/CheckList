package com.dataloom.checklist.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.presentation.theme.CheckListText
import com.dataloom.checklist.presentation.theme.Dimens

/**
 * A row you tick in a list of choices (categories, items, checklists to export), UI-SPEC section 3.
 * At least 64 dp tall; an optional emoji lead (26 sp, decorative), the [label] at 18 sp, an optional
 * [supporting] line, an optional [unitText] chip and the 32 dp checkbox **at the end**, then a divider.
 *
 * The whole row toggles and reads as one TalkBack item. [stateDescription] is read instead of the plain
 * "checked / not checked" ("Selected", "Already added"); pass the strings from the screen. A row with
 * [enabled] = false is drawn at 55 % and does not react ("Already added").
 */
@Composable
fun SelectableRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    lead: String? = null,
    supporting: String? = null,
    unitText: String? = null,
    enabled: Boolean = true,
    stateDescription: String? = null,
    showDivider: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.55f)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.RowMinHeight)
                .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
                .semantics(mergeDescendants = true) {
                    if (stateDescription != null) this.stateDescription = stateDescription
                }
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (lead != null) {
                Text(
                    text = lead,
                    style = CheckListText.emojiLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(36.dp).clearAndSetSemantics { },
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = CheckListText.rowTitle, color = colors.onSurface)
                if (supporting != null) {
                    Text(supporting, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            if (unitText != null) UnitChip(unitText)
            AppCheckbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(horizontal = 6.dp))
        }
        if (showDivider) HorizontalDivider(color = colors.outlineVariant)
    }
}

@Preview(showBackground = true)
@Composable
private fun SelectableRowPreview() {
    PreviewSurface {
        SelectableRow("Groceries", checked = true, onCheckedChange = {}, lead = "🛒")
        SelectableRow("Rice", checked = true, onCheckedChange = {}, unitText = "KG")
        SelectableRow("Dry fruits", checked = false, onCheckedChange = {}, supporting = "Your item", unitText = "KG")
        SelectableRow(
            "Rava (semolina)",
            checked = false,
            onCheckedChange = {},
            supporting = "Already added",
            unitText = "KG",
            enabled = false,
        )
    }
}
