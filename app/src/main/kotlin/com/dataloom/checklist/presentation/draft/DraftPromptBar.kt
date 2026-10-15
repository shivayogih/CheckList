package com.dataloom.checklist.presentation.draft

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.R
import com.dataloom.checklist.presentation.common.UiText
import com.dataloom.checklist.presentation.common.asString
import com.dataloom.checklist.presentation.components.AppIconButton

/**
 * Home's assistant bar (CL-400): "Describe a checklist", with a microphone and a send button, both
 * icon-only with TalkBack labels. Sending opens the draft review screen; nothing is saved from here.
 */
@Composable
fun DraftPromptBar(
    state: DraftPromptUiState,
    language: String,
    onTextChanged: (String) -> Unit,
    onVoiceResult: (String) -> Unit,
    onVoiceProblem: (UiText) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val voice = rememberVoiceInput(language, onVoiceResult, onVoiceProblem)
    val send = {
        focusManager.clearFocus()
        onSubmit()
    }
    OutlinedTextField(
        value = state.text,
        onValueChange = onTextChanged,
        label = { Text(stringResource(R.string.draft_prompt_label)) },
        supportingText = {
            val message = state.message
            if (message != null) {
                Text(message.asString(), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            } else {
                Text(stringResource(R.string.draft_prompt_hint))
            }
        },
        isError = state.message != null,
        trailingIcon = {
            Row {
                AppIconButton(
                    icon = painterResource(R.drawable.ic_mic),
                    contentDescription = stringResource(R.string.draft_prompt_voice),
                    onClick = voice,
                )
                AppIconButton(
                    icon = painterResource(R.drawable.ic_send),
                    contentDescription = stringResource(R.string.draft_prompt_submit),
                    onClick = send,
                    enabled = state.canSubmit,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        // Multi-line so long requests in any language stay readable at 200% text; Go still sends.
        maxLines = 4,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { send() }),
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * Starts Android's own speech recognizer (RecognizerIntent): the app needs no microphone or internet
 * permission, offline recognition is preferred, and the words come back as text. When the phone has
 * no recognizer, or it fails (often a missing offline language pack), the user is told and can type.
 */
@Composable
private fun rememberVoiceInput(
    language: String,
    onResult: (String) -> Unit,
    onProblem: (UiText) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val available = remember(context) { hasSpeechRecognizer(context) }
    val prompt = stringResource(R.string.draft_prompt_voice_prompt)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val heard = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull { it.isNotBlank() }
        when {
            result.resultCode == Activity.RESULT_OK && heard != null -> onResult(heard)
            // The user closed the recognizer: nothing to say.
            result.resultCode == Activity.RESULT_CANCELED -> Unit
            else -> onProblem(UiText(R.string.draft_voice_failed))
        }
    }
    return {
        if (!available) {
            onProblem(UiText(R.string.draft_voice_unavailable))
        } else {
            try {
                launcher.launch(speechIntent(language, prompt))
            } catch (_: ActivityNotFoundException) {
                onProblem(UiText(R.string.draft_voice_unavailable))
            }
        }
    }
}

private fun speechIntent(language: String, prompt: String): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        // Indian English and Indian-language recognizers ("kn-IN"); the app's languages are all Indian.
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "${language.substringBefore('-')}-IN")
        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)

/** Needs the `<queries>` entry in the manifest to see recognizers on Android 11+. */
private fun hasSpeechRecognizer(context: Context): Boolean =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(context.packageManager) != null
