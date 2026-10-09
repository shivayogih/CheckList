package com.dataloom.checklist.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.R
import com.dataloom.checklist.presentation.theme.Dimens

private const val DASH_LENGTH_PX = 14f
private const val DASH_GAP_PX = 10f

/**
 * The "+ Add item to Groceries" button (UI-SPEC section 3): at least 56 dp tall, a 2 dp **dashed**
 * `outlineVariant` border with 12 dp corners, primary text and a + icon. Used for add item, create
 * category and create new item. The text wraps; the button grows with it.
 */
@Composable
fun DashedAddButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val borderColor = colors.outlineVariant
    val shape = RoundedCornerShape(Dimens.Corner12)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.ButtonHeight)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .drawBehind {
                val stroke = Dimens.Border2.toPx()
                drawRoundRect(
                    color = borderColor,
                    topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(Dimens.Corner12.toPx()),
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_LENGTH_PX, DASH_GAP_PX)),
                    ),
                )
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_add),
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(Dimens.Icon),
        )
        Text(text, style = MaterialTheme.typography.labelLarge, color = colors.primary)
    }
}

@Preview(showBackground = true)
@Composable
private fun DashedAddButtonPreview() {
    PreviewSurface {
        DashedAddButton("Add item to Groceries", onClick = {})
    }
}
