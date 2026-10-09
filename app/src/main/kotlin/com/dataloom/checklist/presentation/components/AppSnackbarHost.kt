package com.dataloom.checklist.presentation.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.presentation.theme.Dimens

/**
 * The snackbar host in the look of the mockups (UI-SPEC section 3): inverse colours, 8 dp corners, the
 * action ("Undo") at 18 sp in the inverse primary colour. Put it in `Scaffold(snackbarHost = ...)`.
 * Use `SnackbarDuration.Long` (or Indefinite with an action) for the "Undo" after a delete so people who
 * need more time can still tap it.
 */
@Composable
fun AppSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data -> AppSnackbar(data) }
}

/** One snackbar. Public so screenshot tests can draw it with fixed [SnackbarData]. */
@Composable
fun AppSnackbar(data: SnackbarData, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val actionLabel = data.visuals.actionLabel
    Snackbar(
        modifier = modifier.padding(horizontal = 12.dp),
        // The stock action button is 42 dp tall; this one keeps the 48 dp touch target.
        action = actionLabel?.let { label ->
            {
                TextButton(
                    onClick = { data.performAction() },
                    modifier = Modifier.heightIn(min = Dimens.MinTouchTarget),
                ) { Text(label, style = MaterialTheme.typography.labelLarge) }
            }
        },
        shape = RoundedCornerShape(Dimens.Corner8),
        containerColor = colors.inverseSurface,
        contentColor = colors.inverseOnSurface,
        actionContentColor = colors.inversePrimary,
    ) { Text(data.visuals.message, style = MaterialTheme.typography.bodyLarge) }

}
/** A fixed [SnackbarData] for previews and screenshot tests. */
internal class FakeSnackbarData(
    text: String,
    action: String?,
) : SnackbarData {
    override val visuals: SnackbarVisuals = object : SnackbarVisuals {
        override val message: String = text
        override val actionLabel: String? = action
        override val withDismissAction: Boolean = false
        override val duration = androidx.compose.material3.SnackbarDuration.Indefinite
    }

    override fun performAction() = Unit

    override fun dismiss() = Unit
}

@Preview(showBackground = true)
@Composable
private fun SnackbarPreview() {
    PreviewSurface {
        AppSnackbar(FakeSnackbarData("'Tomato' deleted", "Undo"))
    }
}
