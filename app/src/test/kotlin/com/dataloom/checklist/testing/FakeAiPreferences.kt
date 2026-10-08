package com.dataloom.checklist.testing

import com.dataloom.checklist.ai.policy.AiSettings
import com.dataloom.checklist.ai.policy.AiSettingsSource
import com.dataloom.checklist.settings.AiPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory AI settings with the same rules as the DataStore store; off by default. */
class FakeAiPreferences(initial: AiSettings = AiSettings()) : AiPreferences, AiSettingsSource {

    override val settings = MutableStateFlow(initial)

    override suspend fun current(): AiSettings = settings.value

    override suspend fun setEnabled(enabled: Boolean) {
        settings.update { it.copy(enabled = enabled, autoExecuteSimple = it.autoExecuteSimple && enabled) }
    }

    override suspend fun setAutoExecuteSimple(enabled: Boolean) {
        settings.update { if (it.enabled) it.copy(autoExecuteSimple = enabled) else it }
    }
}
