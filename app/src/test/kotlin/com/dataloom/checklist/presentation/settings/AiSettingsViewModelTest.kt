package com.dataloom.checklist.presentation.settings

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.dataloom.checklist.ai.policy.AiSettings
import com.dataloom.checklist.testing.FakeAiPreferences
import com.dataloom.checklist.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AiSettingsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val preferences = FakeAiPreferences()
    // Created inside each test, after the rule has replaced the main dispatcher.
    private val viewModel by lazy { AiSettingsViewModel(preferences) }

    @Test
    fun `the assistant is off by default and add without asking is unavailable`() = runTest {
        assertEquals(AiSettingsUiState(), viewModel.uiState.value)
        viewModel.uiState.test {
            val loaded = awaitLoaded()
            assertFalse(loaded.enabled)
            assertFalse(loaded.autoAdd)
            assertFalse(loaded.autoAddAvailable)
        }
        assertEquals(AiSettings(), preferences.current())
    }

    @Test
    fun `turning the assistant on stores it and offers add without asking`() = runTest {
        viewModel.uiState.test {
            awaitLoaded()

            viewModel.setEnabled(true)

            val on = awaitItem()
            assertTrue(on.enabled)
            assertTrue(on.autoAddAvailable)
            assertFalse("Add without asking stays off until chosen", on.autoAdd)
            assertTrue(preferences.current().enabled)
        }
    }

    @Test
    fun `add without asking is ignored while the assistant is off`() = runTest {
        viewModel.uiState.test {
            awaitLoaded()

            viewModel.setAutoAdd(true)

            expectNoEvents()
            assertFalse(preferences.current().autoExecuteSimple)
        }
    }

    @Test
    fun `turning the assistant off also turns add without asking off`() = runTest {
        viewModel.uiState.test {
            awaitLoaded()
            viewModel.setEnabled(true)
            assertTrue(awaitItem().enabled)
            viewModel.setAutoAdd(true)
            assertTrue(awaitItem().autoAdd)
            assertTrue(preferences.current().autoExecuteSimple)

            viewModel.setEnabled(false)

            val off = awaitItem()
            assertFalse(off.enabled)
            assertFalse(off.autoAdd)
            assertEquals(AiSettings(), preferences.current())

            // Back on: add without asking does not come back by itself.
            viewModel.setEnabled(true)
            assertFalse(awaitItem().autoAdd)
        }
    }

    /** The initial "not read yet" state may or may not be seen, depending on dispatch order. */
    private suspend fun ReceiveTurbine<AiSettingsUiState>.awaitLoaded(): AiSettingsUiState {
        var state = awaitItem()
        while (!state.isLoaded) state = awaitItem()
        return state
    }
}
