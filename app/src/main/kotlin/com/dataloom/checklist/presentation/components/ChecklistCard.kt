package com.dataloom.checklist.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.R
import com.dataloom.checklist.presentation.theme.CheckListText
import com.dataloom.checklist.presentation.theme.Dimens

/**
 * A checklist on the Home screen (UI-SPEC section 3): `surfaceVariant` with a 1 dp `outlineVariant`
 * border and 16 dp corners, a 21 sp SemiBold title, an optional description, a row of category emoji,
 * an 8 dp progress bar, then "3 of 12 done" on the left and the date on the right.
 *
 * The card is one TalkBack item (title, description, count, date, progress); the [menu] slot (the
 * visible "⋮" [OverflowMenu]) stays its own item. [complete] shows a check before the count.
 *
 * @param progress 0..1.
 * @param doneText "3 of 12 done" or "All 5 done"; the caller owns the plural resources.
 * @param emojis category icons, decorative.
 */
@Composable
fun ChecklistCard(
    title: String,
    progress: Float,
    doneText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    emojis: List<String> = emptyList(),
    dateText: String? = null,
    complete: Boolean = false,
    menu: @Composable () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.Corner16),
        color = colors.surfaceVariant,
        border = BorderStroke(Dimens.Border1, colors.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f).padding(top = 6.dp),
                )
                menu()
            }
            Column(modifier = Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (description != null) {
                    Text(description, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                if (emojis.isNotEmpty()) {
                    // Decorative: the title already says what the list is.
                    Text(
                        text = emojis.joinToString(" "),
                        style = CheckListText.emojiSmall,
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                }
                AppProgressBar(progress = progress)
                ChecklistCardFooter(doneText, dateText, complete)
            }
        }
    }
}

@Composable
private fun ChecklistCardFooter(doneText: String, dateText: String?, complete: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (complete) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_circle),
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(doneText, style = CheckListText.progressLabel, color = colors.onSurface)
        }
        if (dateText != null) {
            Text(
                text = dateText,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChecklistCardPreview() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChecklistCard(
                title = "Diwali Shopping",
                progress = 0.25f,
                doneText = "3 of 12 done",
                dateText = "Updated today",
                emojis = listOf("🛒", "🥕", "🎁"),
                onClick = {},
                menu = { OverflowMenu("More options for Diwali Shopping", listOf(MenuAction("Delete") {})) },
            )
            ChecklistCard(
                title = "Exam Day",
                description = "10th standard board exam",
                progress = 1f,
                doneText = "All 5 done",
                complete = true,
                dateText = "Updated 5 days ago",
                onClick = {},
                menu = { OverflowMenu("More options for Exam Day", listOf(MenuAction("Delete") {})) },
            )
        }
    }
}
