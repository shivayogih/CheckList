package com.dataloom.checklist

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dataloom.checklist.ads.AdsGate
import com.dataloom.checklist.ads.LocalAdsGate
import com.dataloom.checklist.data.local.database.DatabaseHealth
import com.dataloom.checklist.data.local.database.DatabaseState
import com.dataloom.checklist.localization.AppCompatLanguageProvider
import com.dataloom.checklist.navigation.CheckListNavHost
import com.dataloom.checklist.navigation.ChecklistOpenRequest
import com.dataloom.checklist.onboarding.StartViewModel
import com.dataloom.checklist.presentation.common.DatabaseProblemScreen
import com.dataloom.checklist.presentation.components.BrandSplash
import com.dataloom.checklist.presentation.settings.AppearanceViewModel
import com.dataloom.checklist.presentation.theme.CheckListTheme
import com.dataloom.checklist.reminder.ReminderNotifications
import com.dataloom.checklist.settings.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay

/**
 * Single activity hosting the Compose UI.
 *
 * It extends AppCompatActivity (not ComponentActivity) because AppCompat applies the per-app
 * language on Android 8-12 and recreates the activity when the user changes it. It is a Hilt entry
 * point so screens can obtain injected ViewModels.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var languageProvider: AppCompatLanguageProvider

    @Inject
    lateinit var databaseHealth: DatabaseHealth

    @Inject
    lateinit var adsGate: AdsGate

    private val startViewModel: StartViewModel by viewModels()
    private val appearanceViewModel: AppearanceViewModel by viewModels()

    /** A checklist to open, from a tapped reminder notification (CL-350). */
    private var openChecklist by mutableStateOf<ChecklistOpenRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A language change recreates the activity; ViewModels on the back stack then see the new language.
        languageProvider.refresh()
        // After a recreation the same intent is still here; its checklist was already opened.
        if (savedInstanceState == null) openChecklist = intent.checklistOpenRequest()
        // Ads (CL-370): nothing happens in builds with ads off or in the first days after install.
        adsGate.start(this)
        setContent {
            val appearance by appearanceViewModel.appearance.collectAsStateWithLifecycle()
            val dark = when (appearance.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            CheckListTheme(
                darkTheme = dark,
                highContrast = appearance.highContrast,
                textScale = appearance.textSize.scale,
            ) {
                // The brand splash (logo, name, tagline) shows on a cold start only; a recreation (rotation,
                // language change) restores straight into the app.
                var splashDone by rememberSaveable { mutableStateOf(savedInstanceState != null) }
                LaunchedEffect(Unit) {
                    delay(SPLASH_MILLIS)
                    splashDone = true
                }
                val start by startViewModel.start.collectAsStateWithLifecycle()
                val destination = start
                // The database is opened in the background at launch (DatabaseWarmUp). If that open failed,
                // the app shows why instead of screens that would only hit the same error (CL-321).
                val database by databaseHealth.state.collectAsStateWithLifecycle()
                val failed = database as? DatabaseState.Failed
                when {
                    !splashDone -> BrandSplash()
                    failed != null -> DatabaseProblemScreen(failed.failure, onClose = ::finishAffinity)
                    destination == null -> BrandSplash()
                    else -> CompositionLocalProvider(LocalAdsGate provides adsGate) {
                        CheckListNavHost(
                            start = destination,
                            openChecklist = openChecklist,
                            onChecklistOpened = { openChecklist = null },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.checklistOpenRequest()?.let { openChecklist = it }
    }

    private fun Intent.checklistOpenRequest(): ChecklistOpenRequest? =
        getStringExtra(ReminderNotifications.EXTRA_CHECKLIST_ID)?.let(::ChecklistOpenRequest)

    private companion object {
        const val SPLASH_MILLIS = 900L
    }
}
