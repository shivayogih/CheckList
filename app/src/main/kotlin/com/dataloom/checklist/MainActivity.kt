package com.dataloom.checklist

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.dataloom.checklist.navigation.CheckListNavHost
import com.dataloom.checklist.presentation.theme.CheckListTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single activity hosting the Compose UI.
 *
 * It extends AppCompatActivity (not ComponentActivity) because AppCompat applies the per-app
 * language on Android 8-12 and recreates the activity when the user changes it. It is a Hilt entry
 * point so screens can obtain injected ViewModels.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CheckListTheme {
                CheckListNavHost()
            }
        }
    }
}
