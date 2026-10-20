package com.dataloom.checklist.presentation.draft

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dataloom.checklist.R
import com.dataloom.checklist.domain.validation.FieldLimits
import com.dataloom.checklist.domain.validation.InputText
import com.dataloom.checklist.localization.AppLanguageProvider
import com.dataloom.checklist.presentation.common.UiText
import com.dataloom.checklist.settings.AiPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class DraftPromptUiState(
    /** Shown only while the assistant is on in Settings (on by default). */
    val isAvailable: Boolean = false,
    val text: String = "",
    /** Under the field: an empty request, or why voice input did not work. */
    val message: UiText? = null,
) {
    val canSubmit: Boolean get() = isAvailable && text.isNotBlank()
}

/**
 * The "Describe a checklist" bar on Home (CL-400). The user types or speaks a request; submitting hands
 * it to the draft review screen. The typed text survives rotation and process death.
 */
@HiltViewModel
class DraftPromptViewModel @Inject constructor(
    preferences: AiPreferences,
    private val languageProvider: AppLanguageProvider,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val state = MutableStateFlow(DraftPromptUiState(text = savedState.get<String>(KEY_TEXT).orEmpty()))

    val uiState: StateFlow<DraftPromptUiState> = combine(
        preferences.settings.map { it.enabled }.distinctUntilChanged(),
        state,
    ) { enabled, current -> current.copy(isAvailable = enabled) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), state.value)

    /** The app language, for the speech recognizer. */
    val language: String get() = languageProvider.language.value

    fun onTextChanged(text: String) {
        val clean = InputText.forField(text, FieldLimits.AI_COMMAND_MAX)
        savedState[KEY_TEXT] = clean
        state.update { it.copy(text = clean, message = null) }
    }

    /** What the speech recognizer heard replaces the field, so the user can still correct it. */
    fun onVoiceResult(text: String) = onTextChanged(text.trim())

    fun onVoiceProblem(message: UiText) = state.update { it.copy(message = message) }

    /** The request to open the review screen with, or null (and a message) when the field is empty. */
    fun submit(): String? {
        val text = state.value.text.trim()
        if (text.isEmpty()) {
            state.update { it.copy(message = UiText(R.string.draft_empty)) }
            return null
        }
        if (text.codePointCount(0, text.length) > FieldLimits.AI_COMMAND_MAX) {
            state.update { it.copy(message = UiText(R.string.ai_too_long)) }
            return null
        }
        onTextChanged("")
        return text
    }

    internal companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val KEY_TEXT = "draft_prompt"
    }
}
