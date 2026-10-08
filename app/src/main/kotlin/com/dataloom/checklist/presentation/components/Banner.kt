package com.dataloom.checklist.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.R
import com.dataloom.checklist.presentation.theme.bannerContainer

/** How a [Banner] looks: calm information, or a problem. */
enum class BannerVariant { Info, Error }

/**
 * A calm information strip (UI-SPEC section 3): 12 dp corners, a tinted primary background and a
 * leading icon. [BannerVariant.Error] uses the error container instead (for example the profile reset
 * notice). The icon is decorative; the text carries the meaning, so TalkBack reads one item.
 */
@Composable
fun Banner(
    text: String,
    icon: Painter,
    modifier: Modifier = Modifier,
    variant: BannerVariant = BannerVariant.Info,
) {
    val colors = MaterialTheme.colorScheme
    val background = if (variant == BannerVariant.Info) colors.bannerContainer else colors.errorContainer
    val textColor = if (variant == BannerVariant.Info) colors.onSurface else colors.onErrorContainer
    val iconColor = if (variant == BannerVariant.Info) colors.primary else colors.onErrorContainer
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .padding(14.dp)
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, color = textColor)
    }
}

@Preview(showBackground = true)
@Composable
private fun BannerPreview() {
    PreviewSurface {
        Banner("Saved only on this phone, locked with encryption.", painterResource(R.drawable.ic_lock))
        Banner(
            "Your profile could not be read and was reset.",
            painterResource(R.drawable.ic_warning),
            modifier = Modifier.padding(top = 12.dp),
            variant = BannerVariant.Error,
        )
    }
}
