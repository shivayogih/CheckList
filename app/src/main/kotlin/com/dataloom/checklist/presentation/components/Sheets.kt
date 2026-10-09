package com.dataloom.checklist.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.presentation.theme.Dimens

private val SheetShape = RoundedCornerShape(topStart = Dimens.Corner28, topEnd = Dimens.Corner28)

/**
 * A modal bottom sheet in the look of the mockups (UI-SPEC section 3): `surfaceContainer`, 28 dp top
 * corners, a 40 x 4 dp handle, 20 dp side padding and a 24 sp SemiBold [title]. Buttons and fields go in
 * [content]. The sheet scrolls with the keyboard (it is padded for the IME and the navigation bar).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppModalBottomSheet(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        dragHandle = { SheetHandle() },
    ) {
        SheetBody(title = title, content = content)
    }
}

/** The inside of a sheet: title and padded content. Separate so a screenshot can draw it without a window. */
@Composable
internal fun SheetBody(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

/** The grab handle at the top of a sheet. Decorative; the sheet is dismissed with the system back action. */
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(vertical = 12.dp)
            .width(40.dp)
            .size(width = 40.dp, height = 4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.outline),
    )
}

@Preview(showBackground = true)
@Composable
private fun SheetPreview() {
    PreviewSurface {
        androidx.compose.material3.Surface(
            shape = SheetShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                SheetHandle()
                SheetBody("Share as PDF") {
                    Text("Diwali Shopping · 12 items", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PrimaryButton("Share", onClick = {}, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
