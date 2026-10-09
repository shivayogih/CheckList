package com.dataloom.checklist.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * The bar at the bottom of a screen that holds one or two full-width buttons (UI-SPEC section 3):
 * background colour, a top divider and 16 dp padding. Put it in `Scaffold(bottomBar = ...)`. It keeps
 * clear of the navigation bar; the keyboard is handled once, by `Scaffold(modifier = Modifier.keyboardAwareScreen())`.
 *
 * Buttons stacked in a column; use [BottomActionBarRow] for two buttons side by side ("Cancel" and "Import").
 */
@Composable
fun BottomActionBar(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

/**
 * Like [BottomActionBar] but the buttons sit next to each other and share the width.
 * Give each `Modifier.weight(1f)`.
 */
@Composable
fun BottomActionBarRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BottomActionBarPreview() {
    PreviewSurface {
        BottomActionBar {
            PrimaryButton("Create", onClick = {}, modifier = Modifier.fillMaxWidth())
            OutlinedActionButton("Create with AI instead", onClick = {}, modifier = Modifier.fillMaxWidth())
        }
        BottomActionBarRow(modifier = Modifier.padding(top = 16.dp)) {
            OutlinedActionButton("Cancel", onClick = {}, modifier = Modifier.weight(1f))
            PrimaryButton("Import", onClick = {}, modifier = Modifier.weight(1f))
        }
    }
}
