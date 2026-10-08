package com.dataloom.checklist.testing

import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * An empty Hilt entry point for Compose UI tests (CL-171): tests call `setContent` on it with any
 * screen or the whole NavHost, and `hiltViewModel()` works inside. It lives in the debug source set
 * because Robolectric starts only activities declared in the merged manifest; it is not exported
 * and nothing in the app starts it. Release builds do not contain it.
 */
@AndroidEntryPoint
class HiltComponentActivity : ComponentActivity()
