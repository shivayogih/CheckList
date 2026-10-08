package com.dataloom.checklist.presentation.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dataloom.checklist.R

// The three tutorial pictures (00b to 00d). They are drawn in Compose from the same tokens as the
// real screens, so they follow the theme, the font size and the language. They are decoration: the
// title and body under them carry the meaning, so TalkBack skips them.

/** The coloured panel (primaryContainer, 32 dp corners) that holds one illustration. */
@Composable
fun IllustrationPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 240.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(24.dp)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun DemoCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

@Composable
fun FirstIllustration() {
    DemoCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🛒", fontSize = 22.sp)
            Text(
                stringResource(R.string.onboarding_demo_list_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        LinearProgressIndicator(
            progress = { 0.5f },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .heightIn(min = 8.dp),
        )
        DemoRow(stringResource(R.string.onboarding_demo_rice), quantity(5, R.string.unit_kg), checked = true)
        DemoRow(stringResource(R.string.onboarding_demo_sugar), quantity(2, R.string.unit_kg), checked = true)
        DemoRow(stringResource(R.string.onboarding_demo_diyas), quantity(1, R.string.unit_dozen), checked = false)
    }
}

@Composable
fun SecondIllustration() {
    DemoCard {
        Text(
            stringResource(R.string.add_items_title, stringResource(R.string.onboarding_demo_groceries)),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(painterResource(R.drawable.ic_search), contentDescription = null, modifier = Modifier.size(24.dp))
            Text(
                stringResource(R.string.onboarding_demo_search),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Icon(painterResource(R.drawable.ic_close), contentDescription = null, modifier = Modifier.size(24.dp))
        }
        DemoRow(stringResource(R.string.onboarding_demo_rice), stringResource(R.string.unit_kg), checked = true)
        DemoRow(stringResource(R.string.onboarding_demo_rice_flour), stringResource(R.string.unit_kg), checked = false)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepperButton("−")
            Box(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .size(width = 64.dp, height = 56.dp)
                    .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("5", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            StepperButton("+")
        }
    }
}

@Composable
fun ThirdIllustration() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FeaturePill(stringResource(R.string.detail_share_pdf)) { PdfGlyph() }
        FeaturePill(stringResource(R.string.onboarding_demo_offline)) {
            Icon(
                painterResource(R.drawable.ic_wifi_off),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        }
        FeaturePill(stringResource(R.string.onboarding_demo_private)) {
            Icon(
                painterResource(R.drawable.ic_lock),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun quantity(amount: Int, unit: Int): String =
    stringResource(R.string.item_quantity_with_unit, amount.toString(), stringResource(unit))

@Composable
private fun DemoRow(name: String, unit: String, checked: Boolean) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DemoCheckbox(checked)
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (checked) TextDecoration.LineThrough else null,
                color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    unit,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun DemoCheckbox(checked: Boolean) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(shape)
            .then(
                if (checked) {
                    Modifier.background(MaterialTheme.colorScheme.primary)
                } else {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.outline, shape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun StepperButton(symbol: String) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, fontSize = 28.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun FeaturePill(label: String, icon: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 72.dp)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(modifier = Modifier.width(40.dp), contentAlignment = Alignment.Center) { icon() }
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** "PDF" in a small outlined box. "PDF" is the file type's name, the same in every language. */
@Composable
private fun PdfGlyph() {
    Box(
        modifier = Modifier
            .size(32.dp)
            .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "PDF",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
