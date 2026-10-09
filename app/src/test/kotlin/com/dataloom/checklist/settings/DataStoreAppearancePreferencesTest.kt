package com.dataloom.checklist.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Theme, text size and high contrast in the real Preferences DataStore on a temporary file. */
class DataStoreAppearancePreferencesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun closeStores() = scopes.forEach { it.cancel() }

    private fun store(file: File): DataStoreAppearancePreferences {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it }
        return DataStoreAppearancePreferences(PreferenceDataStoreFactory.create(scope = scope) { file })
    }

    private fun newFile() = File(folder.root, "settings.preferences_pb")

    @Test
    fun `a new install follows the phone at normal size and contrast`() = runBlocking {
        assertEquals(Appearance(), store(newFile()).appearance.first())
    }

    @Test
    fun `the choices survive a restart`() = runBlocking {
        val file = newFile()
        store(file).apply {
            setThemeMode(ThemeMode.DARK)
            setTextSize(TextSize.EXTRA_LARGE)
            setHighContrast(true)
        }
        scopes.removeAt(0).coroutineContext.job.cancelAndJoin()

        assertEquals(
            Appearance(themeMode = ThemeMode.DARK, textSize = TextSize.EXTRA_LARGE, highContrast = true),
            store(file).appearance.first(),
        )
    }
}
