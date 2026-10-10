package com.dataloom.checklist.presentation.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.R
import com.dataloom.checklist.presentation.components.PrimaryButton

@Composable
internal fun FirstUse(onCreateChecklist: () -> Unit, modifier: Modifier = Modifier) {
    // Scrolls when 200% text no longer fits the screen (the button was clipped in the audit), and
    // stays vertically centred when it does fit.
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        FirstUseContent(onCreateChecklist, Modifier.verticalScroll(rememberScrollState()).heightIn(min = maxHeight))
    }
}

/** First run, before any checklist exists: a welcome with what the app does, so Home is never blank. */
@Composable
private fun FirstUseContent(onCreateChecklist: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_app_logo),
            contentDescription = null,
            modifier = Modifier.size(88.dp),
        )
        Text(
            text = stringResource(R.string.home_welcome_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WelcomeTip("🛒", stringResource(R.string.home_welcome_tip_categories))
                WelcomeTip("⏰", stringResource(R.string.home_welcome_tip_reminders))
                WelcomeTip("📄", stringResource(R.string.home_welcome_tip_share))
            }
        }
        PrimaryButton(
            text = stringResource(R.string.home_create_checklist),
            onClick = onCreateChecklist,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun WelcomeTip(emoji: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // Emoji are decorative; the text carries the meaning.
        Text(emoji, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clearAndSetSemantics { })
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}
