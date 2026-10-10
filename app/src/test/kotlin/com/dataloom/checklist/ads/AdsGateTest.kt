package com.dataloom.checklist.ads

import android.app.Activity
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AdsGateTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activity: Activity by lazy { Robolectric.buildActivity(Activity::class.java).setup().get() }

    @After
    fun close() = scope.cancel()

    private fun settingsFile() = File(folder.root, "ai_settings.preferences_pb")

    private fun gate(platform: AdPlatform, pastGrace: Boolean = true) = AdsGate(
        platform = platform,
        frequency = AdFrequencyStore(PreferenceDataStoreFactory.create(scope = scope) { settingsFile() }),
        installAge = { pastGrace },
    )

    @Test
    fun `a build with ads off never shows any ad`() = runBlocking {
        val gate = gate(AdPlatform.None)
        gate.start(activity)
        repeat(6) { gate.onPdfExported(activity) }
        assertFalse(gate.canShow.value)
    }

    @Test
    fun `nothing starts, not even the consent form, in the first days after install`() = runBlocking {
        val platform = FakeAdPlatform()
        val gate = gate(platform, pastGrace = false)
        gate.start(activity)
        repeat(6) { gate.onPdfExported(activity) }
        assertEquals(0, platform.starts)
        assertEquals(0, platform.interstitials)
        assertFalse(gate.canShow.value)
    }

    @Test
    fun `without consent no ad is requested`() = runBlocking {
        val platform = FakeAdPlatform(consent = false)
        val gate = gate(platform)
        gate.start(activity)
        repeat(3) { gate.onPdfExported(activity) }
        assertFalse(gate.canShow.value)
        assertEquals(0, platform.interstitials)
    }

    @Test
    fun `with ads on, the platform starts once and every third PDF export shows an interstitial`() = runBlocking {
        val platform = FakeAdPlatform()
        val gate = gate(platform)
        gate.start(activity)
        gate.start(activity)
        assertEquals(1, platform.starts)
        assertTrue(gate.canShow.value)

        repeat(7) { gate.onPdfExported(activity) }
        assertEquals(2, platform.interstitials)
    }
}
