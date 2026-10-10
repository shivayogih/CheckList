package com.dataloom.checklist.ads

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The PDF interstitial cap on a real Preferences DataStore: every third export, kept across restarts. */
class AdFrequencyStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun closeStores() = scopes.forEach { it.cancel() }

    private fun store(file: File): AdFrequencyStore {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it }
        return AdFrequencyStore(PreferenceDataStoreFactory.create(scope = scope) { file })
    }

    private fun newFile() = File(folder.root, "ai_settings.preferences_pb")

    @Test
    fun `every third export earns one interstitial`() = runBlocking {
        val store = store(newFile())
        assertEquals(listOf(false, false, true, false, false, true), (1..6).map { store.recordPdfExport() })
    }

    @Test
    fun `the count survives a restart`() = runBlocking {
        val file = newFile()
        store(file).run {
            recordPdfExport()
            recordPdfExport()
        }
        scopes.removeAt(0).coroutineContext.job.cancelAndJoin()

        assertEquals(true, store(file).recordPdfExport())
    }
}
