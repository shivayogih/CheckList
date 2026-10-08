package com.dataloom.checklist.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.BuildConfig
import com.dataloom.checklist.R
import com.dataloom.checklist.domain.localization.LanguagePreference
import com.dataloom.checklist.localization.AppLocales
import com.dataloom.checklist.presentation.components.BackButton

/** Phase 1 shell: Language and About. Other settings arrive with their phases. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenLanguage: () -> Unit) {
    val languageSummary = when (val preference = remember { AppLocales.current() }) {
        LanguagePreference.SystemDefault -> stringResource(R.string.settings_language_system)
        is LanguagePreference.Specific -> preference.language.nativeName
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_language)) },
                supportingContent = { Text(languageSummary) },
                modifier = Modifier
                    .heightIn(min = 64.dp)
                    .clickable(role = Role.Button, onClick = onOpenLanguage),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_about)) },
                supportingContent = { Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)) },
                modifier = Modifier.heightIn(min = 64.dp),
            )
        }
    }
}
