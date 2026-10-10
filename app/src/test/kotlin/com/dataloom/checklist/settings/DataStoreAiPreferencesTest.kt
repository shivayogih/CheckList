package com.dataloom.checklist.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.dataloom.checklist.ai.policy.AiSettings
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The real Preferences DataStore on a temporary file: defaults, persistence and the toggle rules. */
class DataStoreAiPreferencesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun closeStores() = scopes.forEach { it.cancel() }

    /** A store over [file]; a second store on the same file must wait until the first one's scope is closed. */
    private fun store(file: File): DataStoreAiPreferences {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it }
        return DataStoreAiPreferences(PreferenceDataStoreFactory.create(scope = scope) { file })
    }

    private fun newFile() = File(folder.root, "ai_settings.preferences_pb")

    @Test
    fun `everything is off on a new install`() = runBlocking {
        assertEquals(AiSettings(), store(newFile()).current())
    }

    @Test
    fun `the toggle survives a restart`() = runBlocking {
        val file = newFile()
        store(file).setEnabled(true)
        // Closing the first store frees the file for the next one, as a process restart would.
        scopes.removeAt(0).coroutineContext.job.cancelAndJoin()

        assertTrue(store(file).current().enabled)
    }

    @Test
    fun `add without asking needs the assistant on and goes off with it`() = runBlocking {
        val preferences = store(newFile())

        preferences.setAutoExecuteSimple(true)
        assertFalse("Ignored while the assistant is off", preferences.current().autoExecuteSimple)

        preferences.setEnabled(true)
        preferences.setAutoExecuteSimple(true)
        assertEquals(AiSettings(enabled = true, autoExecuteSimple = true), preferences.current())

        preferences.setEnabled(false)
        assertEquals(AiSettings(), preferences.current())
        preferences.setEnabled(true)
        assertFalse(preferences.current().autoExecuteSimple)
    }
}
