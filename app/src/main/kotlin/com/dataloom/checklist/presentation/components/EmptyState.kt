package com.dataloom.checklist.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dataloom.checklist.presentation.theme.Dimens

/**
 * What a screen shows when there is nothing to show (UI-SPEC section 3): a 150 dp `primaryContainer`
 * circle with a 72 sp [emoji] (decorative), a 26 sp [title], a [body] in the secondary colour and the
 * [actions], usually one filled and one outlined full-width button. Content is centred and the whole
 * thing can scroll when the font is large, so nothing is cut.
 */
@Composable
fun EmptyState(
    emoji: String,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.EmptyStateArt)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, fontSize = 72.sp, lineHeight = 88.sp, textAlign = TextAlign.Center)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 28.dp),
        )
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), content = actions)
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyStatePreview() {
    PreviewSurface {
        EmptyState(
            emoji = "📝",
            title = "No checklists yet",
            body = "Create your first checklist for shopping, travel, exams or anything else.",
        ) {
            PrimaryButton("Create checklist", onClick = {}, modifier = Modifier.fillMaxWidth())
            OutlinedActionButton("Create with AI", onClick = {}, modifier = Modifier.fillMaxWidth())
        }
    }
}
