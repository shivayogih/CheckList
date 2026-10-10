package com.dataloom.checklist.ads

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The ad SDK behind one interface (CL-370). Builds with ads switched off get [None] (src/adsOff): no
 * SDK, nothing loaded, and every slot draws nothing and takes no space. Builds with ads on get AdMob
 * (src/adsOn). Screens never call this directly; they use the slots in AdSlots.kt, which apply
 * [AdsGate]'s rules first.
 */
interface AdPlatform {
    val isAvailable: Boolean

    /**
     * Asks for consent where the law needs it (Google UMP form), then starts the SDK. [onReady] gets
     * whether ads may be requested; it can be called more than once.
     */
    fun start(activity: Activity, onReady: (Boolean) -> Unit)

    /** An adaptive banner, as wide as [modifier] allows. Draws nothing until an ad has loaded. */
    @Composable
    fun Banner(modifier: Modifier)

    /** A native ad laid out like a list row. Draws nothing until an ad has loaded. */
    @Composable
    fun NativeRow(modifier: Modifier)

    /** Shows the preloaded full-screen ad, if one is ready; otherwise only starts loading one. */
    fun showInterstitial(activity: Activity)

    object None : AdPlatform {
        override val isAvailable = false

        override fun start(activity: Activity, onReady: (Boolean) -> Unit) = onReady(false)

        @Composable
        override fun Banner(modifier: Modifier) = Unit

        @Composable
        override fun NativeRow(modifier: Modifier) = Unit

        override fun showInterstitial(activity: Activity) = Unit
    }
}
