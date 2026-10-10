package com.dataloom.checklist.ads

import android.app.Activity
import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.dataloom.checklist.BuildConfig
import java.io.File
import java.util.Properties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The build flag (CL-370): with ads off there is no ad slot at all, not even an empty one; with ads on
 * the slots appear once the gate allows them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AdSlotsTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @After
    fun close() = scope.cancel()

    private fun settingsFile() = File(folder.root, "ai_settings.preferences_pb")

    private fun gate(platform: AdPlatform): AdsGate {
        val gate = AdsGate(
            platform = platform,
            frequency = AdFrequencyStore(PreferenceDataStoreFactory.create(scope = scope) { settingsFile() }),
            installAge = { true },
        )
        gate.start(Robolectric.buildActivity(Activity::class.java).setup().get())
        return gate
    }

    private fun showSlots(gate: AdsGate?) {
        compose.setContent {
            CompositionLocalProvider(LocalAdsGate provides gate) {
                Column {
                    BannerAdSlot()
                    NativeAdSlot()
                }
            }
        }
    }

    @Test
    fun `a build with ads off has no ad slots`() {
        showSlots(gate(AdPlatform.None))
        compose.onNodeWithTag(AdTags.BANNER).assertDoesNotExist()
        compose.onNodeWithTag(AdTags.NATIVE_ROW).assertDoesNotExist()
    }

    @Test
    fun `screens without the app's gate, such as previews and tests, have no ad slots`() {
        showSlots(null)
        compose.onNodeWithTag(AdTags.BANNER).assertDoesNotExist()
        compose.onNodeWithTag(AdTags.NATIVE_ROW).assertDoesNotExist()
    }

    @Test
    fun `a build with ads on shows the banner and native slots once allowed`() {
        showSlots(gate(FakeAdPlatform()))
        compose.onNodeWithTag(AdTags.BANNER).assertExists()
        compose.onNodeWithTag(AdTags.NATIVE_ROW).assertExists()
    }

    @Test
    fun `the compiled ad platform matches the build flag`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        assertEquals(BuildConfig.ADS_ENABLED, createAdPlatform(context).isAvailable)
    }

    @Test
    fun `production and staging are ad-free unless switched on`() {
        // Gradle runs unit tests with the module directory (app/) as the working directory.
        val properties = Properties().apply { File("../gradle.properties").inputStream().use(::load) }
        assertEquals("false", properties.getProperty("checklist.ads.production"))
        assertEquals("false", properties.getProperty("checklist.ads.staging"))
    }
}
