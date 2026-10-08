package com.dataloom.checklist.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.dataloom.checklist.presentation.theme.Dimens

/**
 * The flat progress bar of the mockups: a 8 dp (or 12 dp) rounded track in `outlineVariant` with a
 * `primary` fill. M3's own indicator draws a gap and a stop dot that the mockups do not have.
 * [progress] is 0..1. TalkBack reads it as a progress bar with its value; pass [contentDescription]
 * (for example the "5 of 12 done" text) when the bar is read on its own.
 */
@Composable
fun AppProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = Dimens.ProgressBar,
    contentDescription: String? = null,
) {
    val value = progress.coerceIn(0f, 1f)
    val shape = RoundedCornerShape(height / 2)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(MaterialTheme.colorScheme.outlineVariant)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                if (contentDescription != null) this.contentDescription = contentDescription
            },
    ) {
        if (value > 0f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(value)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProgressBarPreview() {
    PreviewSurface {
        AppProgressBar(progress = 0.42f)
    }
}
