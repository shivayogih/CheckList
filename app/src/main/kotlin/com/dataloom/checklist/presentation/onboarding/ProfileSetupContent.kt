package com.dataloom.checklist.presentation.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.R
import com.dataloom.checklist.presentation.common.asString
import com.dataloom.checklist.presentation.components.Banner
import com.dataloom.checklist.presentation.components.BackButton
import com.dataloom.checklist.presentation.components.BottomActionBar
import com.dataloom.checklist.presentation.components.LabeledTextField
import com.dataloom.checklist.presentation.components.PrimaryButton
import com.dataloom.checklist.presentation.components.TextActionButton

/**
 * 00e and 00f Profile setup. Every field is optional. "Add email and address" stays collapsed until
 * the user taps it. Continue validates like the Settings profile screen; "Skip for now" leaves
 * without saving.
 */
@Composable
fun ProfileSetupContent(state: OnboardingUiState, onAction: (OnboardingAction) -> Unit) {
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackButton { onAction(OnboardingAction.Back) }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { onAction(OnboardingAction.SkipProfile) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        stringResource(R.string.onboarding_skip),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        },
        bottomBar = {
            BottomActionBar {
                PrimaryButton(
                    text = stringResource(R.string.onboarding_continue),
                    onClick = { onAction(OnboardingAction.SubmitProfile) },
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextActionButton(
                    text = stringResource(R.string.onboarding_skip_for_now),
                    onClick = { onAction(OnboardingAction.SkipProfile) },
                    enabled = !state.isBusy,
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_profile_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.onboarding_profile_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Banner(
                text = stringResource(R.string.onboarding_profile_banner),
                icon = painterResource(R.drawable.ic_lock),
            )
            state.saveError?.let { message ->
                Text(
                    text = message.asString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            LabeledTextField(
                label = stringResource(R.string.profile_name),
                value = state.name,
                onValueChange = { onAction(OnboardingAction.NameChanged(it)) },
                helper = stringResource(R.string.onboarding_profile_name_hint),
                error = state.fieldErrors.name?.asString(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            )
            LabeledTextField(
                label = stringResource(R.string.profile_phone),
                value = state.phone,
                onValueChange = { onAction(OnboardingAction.PhoneChanged(it)) },
                error = state.fieldErrors.phone?.asString(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = if (state.moreExpanded) ImeAction.Next else ImeAction.Done,
                ),
            )
            TextButton(
                onClick = { onAction(OnboardingAction.ToggleMore) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(
                    painter = painterResource(if (state.moreExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(if (state.moreExpanded) R.string.onboarding_profile_less else R.string.onboarding_profile_more),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            AnimatedVisibility(visible = state.moreExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    LabeledTextField(
                        label = stringResource(R.string.profile_email),
                        value = state.email,
                        onValueChange = { onAction(OnboardingAction.EmailChanged(it)) },
                        error = state.fieldErrors.email?.asString(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    )
                    LabeledTextField(
                        label = stringResource(R.string.profile_address),
                        value = state.address,
                        onValueChange = { onAction(OnboardingAction.AddressChanged(it)) },
                        error = state.fieldErrors.address?.asString(),
                        singleLine = false,
                        minLines = 3,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Default,
                        ),
                    )
                }
            }
        }
    }
}
