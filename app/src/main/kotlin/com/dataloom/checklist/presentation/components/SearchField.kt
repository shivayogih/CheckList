package com.dataloom.checklist.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.R
import com.dataloom.checklist.presentation.common.rememberDismissKeyboardActions
import com.dataloom.checklist.presentation.theme.Dimens

/**
 * The pill-shaped search field of the mockups (UI-SPEC section 3): at least 56 dp tall, fully rounded,
 * `surfaceContainer` background, a leading search icon, a [placeholder] (not a floating label) and, once
 * something is typed, a clear button labelled [clearContentDescription]. The field grows with the font
 * size and the placeholder wraps rather than being cut.
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    clearContentDescription: String,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val dismiss = rememberDismissKeyboardActions()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            dismiss.onSearch?.invoke(this)
            onSearch?.invoke()
        }),
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = placeholder },
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.SearchFieldHeight)
                    .clip(CircleShape)
                    .background(colors.surfaceContainer)
                    .padding(start = 18.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = null,
                    tint = colors.onSurface,
                    modifier = Modifier.size(Dimens.Icon),
                )
                Box(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    inner()
                }
                if (value.isNotEmpty()) ClearButton(clearContentDescription) { onValueChange("") }
            }
        },
    )
}

@Composable
private fun ClearButton(contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(Dimens.MinTouchTarget)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_close),
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(Dimens.Icon),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchFieldPreview() {
    PreviewSurface {
        SearchField(
            value = "",
            onValueChange = {},
            placeholder = "Search checklists",
            clearContentDescription = "Clear search",
        )
        SearchField(
            value = "ri",
            onValueChange = {},
            placeholder = "Search items",
            clearContentDescription = "Clear search",
        )
    }
}
