package com.dataloom.checklist.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.R
import com.dataloom.checklist.presentation.theme.CheckListText
import com.dataloom.checklist.presentation.theme.Dimens

/**
 * The header of one category inside a checklist (UI-SPEC section 3): the emoji at 26 sp, the name at
 * 21 sp Bold, "2 of 4 done" under it, Move up and Move down buttons and a [menu] slot for the "⋮", all
 * over a 2 dp primary underline. The name is a heading for TalkBack; the emoji is decorative.
 *
 * Pass `null` for [onMoveUp] or [onMoveDown] when the category is first or last: the button stays
 * (so the layout does not jump) but is disabled. Every button is 48 x 48 dp.
 */
@Composable
fun CategoryHeader(
    emoji: String,
    name: String,
    progressText: String,
    moveUpLabel: String,
    moveDownLabel: String,
    modifier: Modifier = Modifier,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    menu: @Composable () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(
            modifier = Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = emoji,
                style = CheckListText.emojiLarge,
                modifier = Modifier.width(36.dp).clearAndSetSemantics { },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp)
                    .semantics(mergeDescendants = true) { heading() },
            ) {
                Text(name, style = CheckListText.categoryName, color = colors.onSurface)
                Text(progressText, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            HeaderIconButton(R.drawable.ic_arrow_upward, moveUpLabel, onMoveUp)
            HeaderIconButton(R.drawable.ic_arrow_downward, moveDownLabel, onMoveDown)
            menu()
        }
        HorizontalDivider(thickness = Dimens.Border2, color = colors.primary)
    }
}

@Composable
private fun HeaderIconButton(icon: Int, contentDescription: String, onClick: (() -> Unit)?) {
    val tint = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .size(Dimens.MinTouchTarget)
            .clip(CircleShape)
            .clickable(enabled = onClick != null, role = Role.Button, onClick = { onClick?.invoke() }),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = if (onClick != null) tint else tint.copy(alpha = 0.38f),
            modifier = Modifier.size(Dimens.Icon),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CategoryHeaderPreview() {
    PreviewSurface {
        CategoryHeader(
            emoji = "🛒",
            name = "Groceries",
            progressText = "2 of 4 done",
            moveUpLabel = "Move up",
            moveDownLabel = "Move down",
            onMoveDown = {},
            menu = { OverflowMenu("More options for Groceries", listOf(MenuAction("Rename") {})) },
        )
    }
}
