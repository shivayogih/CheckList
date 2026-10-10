package com.dataloom.checklist.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The top app bar of every screen (accessibility.md):
 * - the title is a heading, so TalkBack users can jump to it;
 * - at large font scales the bar grows to two title lines instead of clipping the text, because
 *   Material's bar has a fixed 64dp height that a 200% title no longer fits in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    roomyTitle: Boolean = false,
    /** Drawn before the title, e.g. the app logo on Home. Decorative: the title text carries the meaning. */
    titleIcon: (@Composable () -> Unit)? = null,
) {
    val density = LocalDensity.current
    // Titles built from a category name ("New item in Meat, Poultry & Seafood") get two lines at any
    // size, and three when the font is also large.
    val largeText = density.fontScale > LARGE_FONT_SCALE
    val lines = when {
        roomyTitle && largeText -> 3
        roomyTitle || largeText -> 2
        else -> 1
    }
    val lineHeight = with(density) { MaterialTheme.typography.titleLarge.lineHeight.toDp() }
    val height: Dp = if (lines > 1) {
        maxOf(TopAppBarDefaults.TopAppBarExpandedHeight, lineHeight * lines + 16.dp)
    } else {
        TopAppBarDefaults.TopAppBarExpandedHeight
    }
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                titleIcon?.invoke()
                Text(
                    text = title,
                    maxLines = lines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
            }
        },
        navigationIcon = navigationIcon,
        actions = actions,
        expandedHeight = height,
    )
}

/** Above this scale (about "Largest" in system settings) a title gets room for two lines. */
private const val LARGE_FONT_SCALE = 1.3f
