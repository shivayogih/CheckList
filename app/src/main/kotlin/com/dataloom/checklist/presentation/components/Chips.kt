package com.dataloom.checklist.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.R
import com.dataloom.checklist.presentation.theme.CheckListText
import com.dataloom.checklist.presentation.theme.Dimens

/**
 * A filter or quick-start chip (UI-SPEC section 3): 44 dp tall, 12 dp corners, 2 dp outline. Selected
 * chips fill with `primaryContainer` and show a check, so the state is never colour alone. The touch
 * target is 48 dp; the extra 4 dp is invisible. [leadingEmoji] is decorative.
 */
@Composable
fun SelectableChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingEmoji: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(Dimens.Corner12)
    Box(
        modifier = modifier
            .heightIn(min = Dimens.MinTouchTarget)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() }),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = Dimens.ChipHeight)
                .clip(shape)
                .background(if (selected) colors.primaryContainer else colors.background)
                .border(Dimens.Border2, if (selected) colors.primaryContainer else colors.outline, shape)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = colors.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (leadingEmoji != null) {
                Text(leadingEmoji, style = CheckListText.chip, modifier = Modifier.clearAndSetSemantics { })
            }
            Text(
                text = text,
                style = CheckListText.chip,
                color = if (selected) colors.onPrimaryContainer else colors.onSurface,
            )
        }
    }
}

/** Chips that wrap onto as many lines as the width and the font size need. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) { content() }
}

/**
 * The small unit label next to an item name ("5 KG"): 30 dp tall, 8 dp corners, 15 sp SemiBold. [done]
 * switches to the grey variant for a ticked item. It is not interactive and merges into its row.
 */
@Composable
fun UnitChip(text: String, modifier: Modifier = Modifier, done: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .heightIn(min = Dimens.UnitChipHeight)
            .clip(RoundedCornerShape(Dimens.Corner8))
            .background(if (done) colors.surfaceVariant else colors.primaryContainer)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = CheckListText.unitChip,
            color = if (done) colors.onSurfaceVariant else colors.onPrimaryContainer,
        )
    }
}

/**
 * The joined Active / Archived switch of Home: 44 dp tall segments with a 2 dp outline, the chosen
 * one filled and ticked. Each segment is a radio button inside one group; the touch target is 48 dp.
 */
@Composable
fun SegmentedChoice(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(Dimens.ChipHeight / 2)
    Box(modifier = modifier.heightIn(min = Dimens.MinTouchTarget), contentAlignment = Alignment.CenterStart) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .heightIn(min = Dimens.ChipHeight)
                .clip(shape)
                .border(Dimens.Border2, colors.outline, shape)
                .selectableGroup(),
        ) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                if (index > 0) {
                    Box(Modifier.width(Dimens.Border2).fillMaxHeight().background(colors.outline))
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .heightIn(min = Dimens.MinTouchTarget)
                        .background(if (selected) colors.primaryContainer else colors.background)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) })
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                ) {
                    if (selected) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            tint = colors.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = label,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f, fill = false),
                        style = CheckListText.chip.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                        color = if (selected) colors.onPrimaryContainer else colors.onSurface,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChipsPreview() {
    PreviewSurface {
        ChipFlow {
            SelectableChip("Shopping", selected = true, onClick = {}, leadingEmoji = "🛒")
            SelectableChip("Travel", selected = false, onClick = {}, leadingEmoji = "✈️")
            SelectableChip("Exam", selected = false, onClick = {})
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
            UnitChip("5 KG")
            UnitChip("2 KG", done = true)
        }
        SegmentedChoice(listOf("Active", "Archived"), selectedIndex = 0, onSelect = {}, modifier = Modifier.padding(top = 12.dp))
    }
}
