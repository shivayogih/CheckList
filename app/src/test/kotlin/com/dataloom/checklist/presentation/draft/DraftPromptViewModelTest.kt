package com.dataloom.checklist.presentation.draft

import androidx.lifecycle.SavedStateHandle
import com.dataloom.checklist.R
import com.dataloom.checklist.ai.policy.AiSettings
import com.dataloom.checklist.presentation.common.UiText
import com.dataloom.checklist.testing.FakeAiPreferences
import com.dataloom.checklist.testing.FakeLanguageProvider
import com.dataloom.checklist.testing.MainDispatcherRule
import com.dataloom.checklist.testing.keepCollecting
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DraftPromptViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val preferences = FakeAiPreferences(AiSettings(enabled = true))

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) =
        DraftPromptViewModel(preferences, FakeLanguageProvider("kn"), savedState)

    @Test
    fun `the bar shows only while the assistant is on`() = runTest {
        val vm = viewModel()
        keepCollecting(vm.uiState)
        assertTrue(vm.uiState.value.isAvailable)

        preferences.setEnabled(false)
        assertFalse(vm.uiState.value.isAvailable)
    }

    @Test
    fun `an empty request is not sent and says why`() = runTest {
        val vm = viewModel()
        keepCollecting(vm.uiState)
        vm.onTextChanged("   ")

        assertNull(vm.submit())
        assertEquals(UiText(R.string.draft_empty), vm.uiState.value.message)
        assertFalse(vm.uiState.value.canSubmit)
    }

    @Test
    fun `a request is sent trimmed and the field is cleared`() = runTest {
        val vm = viewModel()
        keepCollecting(vm.uiState)
        vm.onVoiceResult("  Diwali shopping ")

        assertEquals("Diwali shopping", vm.submit())
        assertEquals("", vm.uiState.value.text)
    }

    @Test
    fun `voice problems are shown and typing clears them`() = runTest {
        val vm = viewModel()
        keepCollecting(vm.uiState)
        vm.onVoiceProblem(UiText(R.string.draft_voice_unavailable))
        assertEquals(UiText(R.string.draft_voice_unavailable), vm.uiState.value.message)

        vm.onTextChanged("Goa trip")
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun `the typed request survives process death and uses the app language`() {
        val handle = SavedStateHandle()
        viewModel(handle).onTextChanged("ಗೋವಾ ಪ್ರವಾಸ")

        val restored = viewModel(handle)
        assertEquals("ಗೋವಾ ಪ್ರವಾಸ", restored.uiState.value.text)
        assertEquals("kn", restored.language)
    }
}
