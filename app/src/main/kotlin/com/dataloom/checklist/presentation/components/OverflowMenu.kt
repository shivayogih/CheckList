package com.dataloom.checklist.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import com.dataloom.checklist.R

/** One entry of a visible "⋮" menu. Disabled entries stay visible so the menu layout is predictable. */
data class MenuAction(val label: String, val enabled: Boolean = true, val onClick: () -> Unit)

/**
 * The visible "⋮" button every row with actions gets, so long-press is never required
 * (accessibility.md). [contentDescription] names the thing acted on ("More options for Rice").
 */
@Composable
fun OverflowMenu(contentDescription: String, actions: List<MenuAction>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        AppIconButton(painterResource(R.drawable.ic_more_vert), contentDescription, onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    enabled = action.enabled,
                    onClick = {
                        expanded = false
                        action.onClick()
                    },
                )
            }
        }
    }
}
