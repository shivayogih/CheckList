package com.dataloom.checklist.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dataloom.checklist.settings.AiPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AiSettingsUiState(
    /** False until the stored value is read, so the switches do not flash "off" then "on". */
    val isLoaded: Boolean = false,
    val enabled: Boolean = false,
    val autoAdd: Boolean = false,
) {
    /** "Add without asking" only means something while the assistant is on. */
    val autoAddAvailable: Boolean get() = isLoaded && enabled
}

/** The "AI assistant (offline)" and "Let AI add items without asking" switches in Settings (CL-240). */
@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val preferences: AiPreferences,
) : ViewModel() {

    val uiState: StateFlow<AiSettingsUiState> = preferences.settings
        .map { AiSettingsUiState(isLoaded = true, enabled = it.enabled, autoAdd = it.autoExecuteSimple) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AiSettingsUiState())

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setEnabled(enabled) }
    }

    fun setAutoAdd(enabled: Boolean) {
        if (!uiState.value.autoAddAvailable) return
        viewModelScope.launch { preferences.setAutoExecuteSimple(enabled) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
