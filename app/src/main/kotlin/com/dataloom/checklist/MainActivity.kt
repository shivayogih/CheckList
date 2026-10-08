package com.dataloom.checklist

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dataloom.checklist.localization.AppCompatLanguageProvider
import com.dataloom.checklist.navigation.CheckListNavHost
import com.dataloom.checklist.onboarding.StartViewModel
import com.dataloom.checklist.presentation.theme.CheckListTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

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

    private val startViewModel: StartViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A language change recreates the activity; ViewModels on the back stack then see the new language.
        languageProvider.refresh()
        setContent {
            CheckListTheme {
                // Only the themed background shows for the few milliseconds the first-run flag takes.
                val start by startViewModel.start.collectAsStateWithLifecycle()
                start?.let { CheckListNavHost(it) }
            }
        }
    }
}
