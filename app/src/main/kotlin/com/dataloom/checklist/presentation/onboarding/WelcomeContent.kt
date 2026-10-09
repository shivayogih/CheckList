package com.dataloom.checklist.presentation.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dataloom.checklist.R
import com.dataloom.checklist.domain.localization.LanguagePreference
import com.dataloom.checklist.domain.localization.SupportedLanguages
import com.dataloom.checklist.presentation.components.AppLogo
import com.dataloom.checklist.presentation.components.BottomActionBar
import com.dataloom.checklist.presentation.components.PrimaryButton
import com.dataloom.checklist.presentation.theme.bannerContainer
import java.util.Locale

/**
 * 00a Welcome and language: a two-column grid of language tiles. A tile applies its language at once
 * (the caller does that in [onSelect]), so the next screens already appear in it.
 */
@Composable
fun WelcomeContent(
    selected: LanguagePreference,
    onSelect: (LanguagePreference) -> Unit,
    onContinue: () -> Unit,
) {
    val options = remember {
        SupportedLanguages.all.map { LanguagePreference.Specific(it) } + LanguagePreference.SystemDefault
    }
    Scaffold(
        bottomBar = {
            BottomActionBar {
                PrimaryButton(
                    text = stringResource(R.string.onboarding_continue),
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppLogo(size = 96.dp)
            Text(
                text = stringResource(R.string.onboarding_welcome_title, stringResource(R.string.app_name)),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 24.dp)
                    .semantics { heading() },
            )
            Text(
                text = stringResource(R.string.onboarding_welcome_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                options.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        pair.forEach { option ->
                            val (title, subtitle) = option.tileTexts()
                            LanguageTile(
                                title = title,
                                subtitle = subtitle,
                                selected = option == selected,
                                onClick = { onSelect(option) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguagePreference.tileTexts(): Pair<String, String> = when (this) {
    LanguagePreference.SystemDefault ->
        stringResource(R.string.onboarding_phone_language) to stringResource(R.string.settings_language_system)
    is LanguagePreference.Specific ->
        language.nativeName to Locale.forLanguageTag(language.tag).getDisplayLanguage(Locale.ENGLISH)
}

@Composable
private fun LanguageTile(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxHeight()
            .heightIn(min = 84.dp)
            .clip(shape)
            .background(if (selected) colors.bannerContainer else colors.background)
            .border(if (selected) 3.dp else 2.dp, if (selected) colors.primary else colors.outlineVariant, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(16.dp),
    ) {
        Column(modifier = Modifier.align(Alignment.CenterStart).padding(end = if (selected) 28.dp else 0.dp)) {
            Text(
                text = title,
                fontSize = 22.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
            )
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
