package com.dataloom.checklist.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.R
import com.dataloom.checklist.presentation.theme.CheckListText
import com.dataloom.checklist.presentation.theme.Dimens

/**
 * The top app bar of the mockups (UI-SPEC section 3): at least 68 dp tall, a 24 sp SemiBold title and an
 * optional 15 sp subtitle. The title and the subtitle **wrap** to as many lines as they need and the
 * bar grows with them, so a long Tamil or Kannada title is never clipped or cut with "...".
 *
 * [navigationIcon] and [actions] are slots for [TopBarIconButton] and [TopBarTextAction]; each is at
 * least 48 dp. Put the bar in `Scaffold(topBar = ...)`; it keeps clear of the status bar itself.
 */
@Composable
fun CheckListTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
            .heightIn(min = Dimens.TopBarHeight)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        navigationIcon?.invoke()
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (navigationIcon == null) 12.dp else 4.dp, end = 4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = CheckListText.subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        actions()
    }
}

/**
 * A 48 x 48 dp icon button for the bar (back, close, share, search, sort). Icon only, no visible text;
 * [contentDescription] is required, read by TalkBack and shown as a tooltip. See [AppIconButton].
 */
@Composable
fun TopBarIconButton(
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    AppIconButton(icon, contentDescription, onClick, modifier, enabled)
}

/** The back arrow (mirrors in right-to-left layouts). */
@Composable
fun TopBarBackButton(contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TopBarIconButton(painterResource(R.drawable.ic_arrow_back), contentDescription, onClick, modifier)
}

/** The close "x" for full-screen forms. */
@Composable
fun TopBarCloseButton(contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TopBarIconButton(painterResource(R.drawable.ic_close), contentDescription, onClick, modifier)
}

/**
 * A text action such as "Save" at the end of the bar; keep text only where an icon would be ambiguous.
 * 18 sp SemiBold in the primary colour, 48 dp tall.
 */
@Composable
fun TopBarTextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val color = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = Dimens.MinTouchTarget, minHeight = Dimens.MinTouchTarget)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (enabled) color else color.copy(alpha = 0.38f),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CheckListTopBarPreview() {
    PreviewSurface {
        CheckListTopBar(
            title = "Add to Groceries",
            subtitle = "Diwali Shopping",
            navigationIcon = { TopBarCloseButton("Close", onClick = {}) },
            actions = { TopBarTextAction("Save", onClick = {}) },
        )
    }
}
