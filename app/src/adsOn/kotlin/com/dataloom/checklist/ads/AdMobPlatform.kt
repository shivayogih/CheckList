package com.dataloom.checklist.ads

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dataloom.checklist.BuildConfig
import com.dataloom.checklist.R
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Google AdMob with the UMP consent form (CL-370). Compiled only into flavors with ads switched on.
 * Ads are requested only after consent allows it; until an ad has loaded, every slot draws nothing.
 */
internal class AdMobPlatform(private val appContext: Context) : AdPlatform {

    override val isAvailable = BuildConfig.ADS_ENABLED

    private val initialized = AtomicBoolean(false)
    private var interstitial: InterstitialAd? = null
    private var loadingInterstitial = false

    override fun start(activity: Activity, onReady: (Boolean) -> Unit) {
        val consent = UserMessagingPlatform.getConsentInformation(activity)
        val afterConsent = { onConsent(consent.canRequestAds(), onReady) }
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            { UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { afterConsent() } },
            { afterConsent() },
        )
        // Consent given in an earlier session lets ads start without waiting for the update above.
        if (consent.canRequestAds()) afterConsent()
    }

    private fun onConsent(canRequestAds: Boolean, onReady: (Boolean) -> Unit) {
        if (canRequestAds && initialized.compareAndSet(false, true)) {
            MobileAds.initialize(appContext)
            preloadInterstitial()
        }
        onReady(canRequestAds)
    }

    @Composable
    override fun Banner(modifier: Modifier) {
        val context = LocalContext.current
        BoxWithConstraints(modifier.fillMaxWidth()) {
            val widthDp = maxWidth.value.toInt()
            val size = remember(widthDp) { AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, widthDp) }
            val adView = remember(size) {
                AdView(context).apply {
                    setAdSize(size)
                    adUnitId = BuildConfig.AD_UNIT_BANNER
                    loadAd(AdRequest.Builder().build())
                }
            }
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            DisposableEffect(adView, lifecycle) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> adView.resume()
                        Lifecycle.Event.ON_PAUSE -> adView.pause()
                        else -> Unit
                    }
                }
                lifecycle.addObserver(observer)
                onDispose {
                    lifecycle.removeObserver(observer)
                    adView.destroy()
                }
            }
            AndroidView(factory = { adView }, modifier = Modifier.fillMaxWidth().height(size.height.dp))
        }
    }

    @Composable
    override fun NativeRow(modifier: Modifier) {
        val context = LocalContext.current
        var ad by remember { mutableStateOf<NativeAd?>(null) }
        DisposableEffect(context) {
            var disposed = false
            AdLoader.Builder(context, BuildConfig.AD_UNIT_NATIVE)
                .forNativeAd { loaded -> if (disposed) loaded.destroy() else ad = loaded }
                .withNativeAdOptions(
                    NativeAdOptions.Builder().setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT).build(),
                )
                .build()
                .loadAd(AdRequest.Builder().build())
            onDispose {
                disposed = true
                ad?.destroy()
                ad = null
            }
        }
        val loaded = ad ?: return
        val colors = MaterialTheme.colorScheme
        val style = NativeRowStyle(
            text = colors.onSurface.toArgb(),
            secondaryText = colors.onSurfaceVariant.toArgb(),
            accent = colors.primary.toArgb(),
            onAccent = colors.onPrimary.toArgb(),
            label = stringResource(R.string.ad_label),
        )
        AndroidView(
            factory = { NativeRowView(it).root },
            update = { (it.tag as NativeRowView).bind(loaded, style) },
            modifier = modifier.fillMaxWidth(),
        )
    }

    override fun showInterstitial(activity: Activity) {
        val ad = interstitial ?: return preloadInterstitial()
        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = preloadInterstitial()

            override fun onAdFailedToShowFullScreenContent(error: AdError) = preloadInterstitial()
        }
        ad.show(activity)
    }

    private fun preloadInterstitial() {
        if (!initialized.get() || interstitial != null || loadingInterstitial) return
        loadingInterstitial = true
        InterstitialAd.load(
            appContext,
            BuildConfig.AD_UNIT_INTERSTITIAL,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                    loadingInterstitial = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loadingInterstitial = false
                }
            },
        )
    }
}
