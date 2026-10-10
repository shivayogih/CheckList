package com.dataloom.checklist.journeys

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ApplicationProvider
import com.dataloom.checklist.MainActivity
import com.dataloom.checklist.R
import com.dataloom.checklist.di.OnboardingModule
import com.dataloom.checklist.domain.localization.LanguagePreference
import com.dataloom.checklist.domain.localization.SupportedLanguages
import com.dataloom.checklist.localization.AppLocales
import com.dataloom.checklist.onboarding.OnboardingStore
import com.dataloom.checklist.testing.FakeOnboardingStore
import com.dataloom.checklist.testing.ui.awaitNode
import com.dataloom.checklist.testing.ui.awaitText
import com.dataloom.checklist.testing.ui.string
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Journey J4: change the app language to Kannada in Settings and see the UI in Kannada (CL-171).
 *
 * Runs on SDK 32 on purpose: there AppCompat itself stores the per-app language and applies it to
 * the recreated activity (Android 13+ hands this to the platform LocaleManager, which Robolectric
 * does not apply to resources). The flow under test (our picker -> AppLocales -> AppCompat ->
 * recreated MainActivity) is the same on every version.
 */
@HiltAndroidTest
@UninstallModules(OnboardingModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class LanguageJourneyTest {

    // A returning user: the app opens on Home, not in the first-run flow (CL-250).
    @BindValue
    @JvmField
    val onboardingStore: OnboardingStore = FakeOnboardingStore(initial = true)

    private val hiltRule = HiltAndroidRule(this)
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(hiltRule).around(composeRule)

    @After
    fun resetLanguage() {
        // AppCompat keeps the choice in a static field; never leak Kannada into other tests.
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }

    @Test
    fun `choosing Kannada in Settings shows the app in Kannada`() {
        // Settings is an icon button on Home, found by its content description.
        composeRule.awaitNode(hasContentDescription(string(R.string.settings_title)) and hasClickAction())
            .performClick()
        composeRule.awaitNode(hasText(string(R.string.settings_language)) and hasClickAction()).performClick()
        composeRule.awaitNode(hasText(kannadaLanguage.nativeName) and hasClickAction()).performClick()
        composeRule.waitForIdle()

        assertEquals(LanguagePreference.Specific(kannadaLanguage), AppLocales.current())

        // AppCompat recreates the activity; recreating again here is harmless and makes the test
        // independent of when Robolectric runs that recreation.
        composeRule.activityRule.scenario.recreate()

        // The language screen and, after going back, Settings are now in Kannada.
        composeRule.awaitText(kannada(R.string.language_title))
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.awaitText(kannada(R.string.settings_title))
        composeRule.awaitText(kannada(R.string.settings_language))
    }

    private val kannadaLanguage = SupportedLanguages.fromTag("kn")!!

    private fun kannada(id: Int): String {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag("kn")) }
        return context.createConfigurationContext(config).getString(id)
    }
}
