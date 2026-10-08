package com.dataloom.checklist.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Where the app opens. Decided once per process, so finishing the flow never swaps the graph. */
enum class StartDestination { ONBOARDING, HOME }

/**
 * Reads the first-run flag once at launch. [start] is null for the few milliseconds the read takes;
 * the activity draws only its background until then. A configuration change (a language switch
 * recreates the activity) keeps this ViewModel, so the answer is instant the second time.
 */
@HiltViewModel
class StartViewModel @Inject constructor(private val store: OnboardingStore) : ViewModel() {

    private val state = MutableStateFlow<StartDestination?>(null)
    val start: StateFlow<StartDestination?> = state.asStateFlow()

    init {
        viewModelScope.launch {
            state.value = if (store.completed.first()) StartDestination.HOME else StartDestination.ONBOARDING
        }
    }
}
