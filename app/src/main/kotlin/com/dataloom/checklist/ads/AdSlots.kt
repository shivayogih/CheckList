package com.dataloom.checklist.ads

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The app's [AdsGate], provided by MainActivity. Null (the default, used by previews and screen tests)
 * means no ads, exactly like a build with ads switched off.
 */
val LocalAdsGate = staticCompositionLocalOf<AdsGate?> { null }

/** Test tags of the ad slots, so tests can prove a build without ads has none. */
object AdTags {
    const val BANNER = "ad_banner"
    const val NATIVE_ROW = "ad_native_row"
}

/** True when ads may show here and now: allowed by the gate and the keyboard is closed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun rememberAdsVisible(): Boolean {
    val canShow = LocalAdsGate.current?.canShow?.collectAsStateWithLifecycle()?.value ?: false
    return canShow && !WindowInsets.isImeVisible
}

/** Home's bottom banner. Emits nothing at all, so no space, unless ads may show. */
@Composable
fun BannerAdSlot(modifier: Modifier = Modifier) {
    val gate = LocalAdsGate.current
    if (gate == null || !rememberAdsVisible()) return
    Box(modifier.fillMaxWidth().navigationBarsPadding().testTag(AdTags.BANNER)) {
        gate.platform.Banner(Modifier.fillMaxWidth())
    }
}

/** A native ad between catalogue rows. Emits nothing unless ads may show. */
@Composable
fun NativeAdSlot(modifier: Modifier = Modifier) {
    val gate = LocalAdsGate.current
    if (gate == null || !rememberAdsVisible()) return
    Box(modifier.fillMaxWidth().testTag(AdTags.NATIVE_ROW)) {
        gate.platform.NativeRow(Modifier.fillMaxWidth())
    }
}

/**
 * Returns the call to make after a PDF export. A share opens the Sharesheet first, so the ad waits until
 * the user is back in the app ([afterShare] true); a save is already back. With no ads it does nothing.
 */
@Composable
fun rememberPdfExportAd(): (afterShare: Boolean) -> Unit {
    val gate = LocalAdsGate.current
    val activity = LocalActivity.current
    val lifecycle by rememberUpdatedState(LocalLifecycleOwner.current.lifecycle)
    val scope = rememberCoroutineScope()
    return remember(gate, activity) {
        { afterShare: Boolean ->
            if (gate != null && activity != null && gate.canShow.value) {
                scope.launch {
                    if (afterShare) {
                        // No pause soon means the Sharesheet never opened: skip the ad rather than show it later.
                        val left = withTimeoutOrNull(LEAVE_TIMEOUT_MILLIS) {
                            lifecycle.currentStateFlow.first { !it.isAtLeast(Lifecycle.State.RESUMED) }
                        }
                        if (left == null) return@launch
                        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
                    }
                    gate.onPdfExported(activity)
                }
            }
        }
    }
}

private const val LEAVE_TIMEOUT_MILLIS = 3_000L
