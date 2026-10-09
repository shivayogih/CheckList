package com.dataloom.checklist.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.presentation.theme.CheckListTheme

/**
 * Wraps a preview or a screenshot test in the app theme on a screen-coloured background, with the
 * 16 dp side padding every screen has. Shared by the `@Preview`s and the screenshot tests so both show
 * the same thing.
 */
@Composable
internal fun PreviewSurface(
    darkTheme: Boolean = false,
    highContrast: Boolean = false,
    content: @Composable () -> Unit,
) {
    CheckListTheme(darkTheme = darkTheme, highContrast = highContrast) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) { content() }
        }
    }
}
