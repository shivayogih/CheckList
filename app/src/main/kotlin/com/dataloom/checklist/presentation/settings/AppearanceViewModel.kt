package com.dataloom.checklist.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dataloom.checklist.settings.Appearance
import com.dataloom.checklist.settings.AppearancePreferences
import com.dataloom.checklist.settings.TextSize
import com.dataloom.checklist.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Theme, Text size and High contrast: read by the activity to theme the app, changed from Settings. */
@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val preferences: AppearancePreferences,
) : ViewModel() {

    val appearance: StateFlow<Appearance> = preferences.appearance
        .stateIn(viewModelScope, SharingStarted.Eagerly, Appearance())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setTextSize(size: TextSize) {
        viewModelScope.launch { preferences.setTextSize(size) }
    }

    fun setHighContrast(enabled: Boolean) {
        viewModelScope.launch { preferences.setHighContrast(enabled) }
    }
}
