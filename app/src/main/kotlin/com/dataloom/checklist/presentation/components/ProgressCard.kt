package com.dataloom.checklist.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.presentation.theme.CheckListText
import com.dataloom.checklist.presentation.theme.Dimens

/**
 * The progress summary at the top of a checklist (UI-SPEC section 3): "5 of 12 done" in 24 sp Bold, the
 * percentage in 32 sp primary, a 12 dp bar and, if [hideCompleted] is not null, the "Hide completed
 * items" switch row.
 *
 * Variants: without the switch (leave [hideCompleted] null, mockup 28); empty list (pass
 * `progress = 0f`, `percentText = null` and a [subText] that invites adding items, mockup 09).
 * Texts are resolved by the caller (plurals live there). The count, the sub text and the percentage
 * merge into one TalkBack item together with the bar.
 */
@Composable
fun ProgressCard(
    countText: String,
    progress: Float,
    modifier: Modifier = Modifier,
    subText: String? = null,
    percentText: String? = null,
    hideCompleted: Boolean? = null,
    onHideCompletedChange: (Boolean) -> Unit = {},
    hideCompletedLabel: String = "",
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.Corner16),
        color = colors.surfaceVariant,
        border = BorderStroke(Dimens.Border1, colors.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.semantics(mergeDescendants = true) { }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(countText, style = CheckListText.bigCount, color = colors.onSurface)
                        if (subText != null) {
                            Text(subText, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        }
                    }
                    if (percentText != null) {
                        Text(
                            text = percentText,
                            style = CheckListText.percent,
                            color = colors.primary,
                            textAlign = TextAlign.End,
                        )
                    }
                }
                AppProgressBar(
                    progress = progress,
                    height = Dimens.ProgressBarLarge,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            if (hideCompleted != null) {
                HideCompletedRow(hideCompleted, onHideCompletedChange, hideCompletedLabel)
            }
        }
    }
}

@Composable
private fun HideCompletedRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .padding(top = 4.dp)
            .fillMaxWidth()
            .heightIn(min = Dimens.MinTouchTarget)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurface,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
        )
        AppSwitch(checked = checked, onCheckedChange = null)
    }
}

@Preview(showBackground = true)
@Composable
private fun ProgressCardPreview() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ProgressCard(
                countText = "5 of 12 done",
                subText = "7 items left",
                percentText = "42%",
                progress = 5f / 12f,
                hideCompleted = false,
                onHideCompletedChange = {},
                hideCompletedLabel = "Hide completed items",
            )
            ProgressCard(
                countText = "0 of 0 done",
                subText = "Add items to start ticking them off.",
                progress = 0f,
            )
        }
    }
}
