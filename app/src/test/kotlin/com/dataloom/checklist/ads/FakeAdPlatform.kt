package com.dataloom.checklist.ads

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** An ad platform that is "on", grants consent at once and counts what it was asked to do. */
class FakeAdPlatform(private val consent: Boolean = true) : AdPlatform {
    override val isAvailable = true
    var starts = 0
    var interstitials = 0

    override fun start(activity: Activity, onReady: (Boolean) -> Unit) {
        starts++
        onReady(consent)
    }

    @Composable
    override fun Banner(modifier: Modifier) = Box(modifier.height(50.dp))

    @Composable
    override fun NativeRow(modifier: Modifier) = Box(modifier.height(56.dp))

    override fun showInterstitial(activity: Activity) {
        interstitials++
    }
}
