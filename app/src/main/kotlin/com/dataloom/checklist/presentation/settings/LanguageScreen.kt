package com.dataloom.checklist.presentation.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.R
import com.dataloom.checklist.domain.localization.LanguagePreference
import com.dataloom.checklist.domain.localization.SupportedLanguages
import com.dataloom.checklist.localization.AppLocales
import com.dataloom.checklist.presentation.components.AppTopBar
import com.dataloom.checklist.presentation.components.BackButton

/**
 * Language picker. Each language is shown in its own script so users can find theirs whatever the
 * current UI language is. The whole row is the touch target, not just the radio button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageScreen(onBack: () -> Unit) {
    val current = remember { AppLocales.current() }
    val options: List<LanguagePreference> = remember {
        listOf(LanguagePreference.SystemDefault) + SupportedLanguages.all.map { LanguagePreference.Specific(it) }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.language_title),
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .selectableGroup(),
        ) {
            items(options) { option ->
                val selected = option == current
                // Each native name carries its language, so TalkBack reads "ಕನ್ನಡ" with a Kannada voice
                // even while the app is still in English.
                val label = when (option) {
                    LanguagePreference.SystemDefault ->
                        AnnotatedString(stringResource(R.string.settings_language_system))
                    is LanguagePreference.Specific -> AnnotatedString(
                        text = option.language.nativeName,
                        spanStyle = SpanStyle(localeList = LocaleList(option.language.tag)),
                    )
                }
                ListItem(
                    headlineContent = { Text(label) },
                    leadingContent = {
                        // onClick = null: the row handles selection and its semantics.
                        RadioButton(selected = selected, onClick = null)
                    },
                    modifier = Modifier
                        .heightIn(min = 64.dp)
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { if (!selected) AppLocales.apply(option) },
                        ),
                )
            }
        }
    }
}
