package com.dataloom.checklist.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.dataloom.checklist.R
import com.dataloom.checklist.domain.model.UnitCode
import com.dataloom.checklist.domain.model.UnitDef
import com.dataloom.checklist.presentation.common.unitLabel
import com.dataloom.checklist.presentation.common.unitPickerLabel
import com.dataloom.checklist.presentation.theme.Dimens

/** Shows the chosen unit ("kg" or "No unit") and opens the picker. */
@Composable
fun UnitButton(unit: UnitDef?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = unit?.let { unitLabel(it) } ?: stringResource(R.string.unit_none)
    // Drawn like the form fields around it: 12 dp corners and a 2 dp outline.
    OutlinedButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(Dimens.Border2, MaterialTheme.colorScheme.outline),
        modifier = modifier.heightIn(min = 56.dp),
    ) {
        Text(stringResource(R.string.unit_button, label))
        Icon(painterResource(R.drawable.ic_arrow_drop_down), contentDescription = null)
    }
}

/**
 * A radio grid of units in a dialog: "kg (Kilogram)" so the short label is explained, and as many
 * columns as the width allows (two on most phones, more on tablets). Rows stay large and wrap at
 * 200% font. [onCreateUnit] adds a "New unit" button when the caller supports creating one.
 */
@Composable
fun UnitPickerDialog(
    units: List<UnitDef>,
    selected: UnitCode?,
    onSelect: (UnitCode?) -> Unit,
    onDismiss: () -> Unit,
    onCreateUnit: (() -> Unit)? = null,
) {
    val sortedUnits = remember(units) { units.sortedBy { it.sortOrder } }
    AlertDialog(
        onDismissRequest = onDismiss,
        // Wider than the platform default so two columns fit on a phone.
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 16.dp),
        title = { DialogTitle(stringResource(R.string.unit_picker_title)) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = UnitCellMinWidth),
                modifier = Modifier.selectableGroup(),
            ) {
                item(key = "none", contentType = "option") {
                    UnitOption(stringResource(R.string.unit_none), selected == null) { onSelect(null) }
                }
                items(sortedUnits, key = { it.code.value }, contentType = { "option" }) { unit ->
                    UnitOption(unitPickerLabel(unit), unit.code == selected) { onSelect(unit.code) }
                }
                if (onCreateUnit != null) {
                    item(key = "create", contentType = "create", span = { GridItemSpan(maxLineSpan) }) {
                        TextButton(onClick = onCreateUnit, modifier = Modifier.heightIn(min = 48.dp)) {
                            Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                            Text(stringResource(R.string.unit_create), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private val UnitCellMinWidth = 136.dp

@Composable
private fun UnitOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 4.dp, end = 8.dp))
    }
}
