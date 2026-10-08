package com.dataloom.checklist.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.R
import com.dataloom.checklist.presentation.theme.Dimens

/**
 * An icon-only action: **no visible text**, a 48 x 48 dp touch target, a translated [contentDescription]
 * for TalkBack and the same text as a tooltip on long press.
 *
 * Use it wherever the icon alone is clear (search, share, sort, filter, more, close, back). Keep a text label
 * only where the icon would be ambiguous or the mockup shows a label on a primary action ("Save", "Create").
 * [contentDescription] is required and has no default on purpose: an icon button without a label is
 * invisible to TalkBack.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppIconButton(
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(contentDescription) } },
        state = rememberTooltipState(),
    ) {
        Box(
            modifier = modifier
                .size(Dimens.MinTouchTarget)
                .clip(CircleShape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = icon,
                contentDescription = contentDescription,
                tint = if (enabled) tint else tint.copy(alpha = 0.38f),
                modifier = Modifier.size(Dimens.Icon),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AppIconButtonPreview() {
    PreviewSurface {
        androidx.compose.foundation.layout.Row {
            AppIconButton(painterResource(R.drawable.ic_search), "Search", {})
            AppIconButton(painterResource(R.drawable.ic_filter_list), "Filter", {})
            AppIconButton(painterResource(R.drawable.ic_sort), "Sort", {})
            AppIconButton(painterResource(R.drawable.ic_share), "Share", {})
        }
    }
}
