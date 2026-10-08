package com.dataloom.checklist.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The bar at the bottom of a screen that holds one or two full-width buttons (UI-SPEC section 3):
 * background colour, a top divider and 16 dp padding. Put it in `Scaffold(bottomBar = ...)`. It
 * keeps clear of the navigation bar, so it is safe with edge-to-edge.
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
