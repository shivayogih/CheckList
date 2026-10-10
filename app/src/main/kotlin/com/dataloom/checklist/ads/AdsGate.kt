package com.dataloom.checklist.ads

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How long ago the app was installed, for the no-ads grace period. */
fun interface InstallAge {
    fun isPastGracePeriod(): Boolean
}

/** Reads the install time Android keeps for the package. */
class PackageInstallAge @Inject constructor(
    @ApplicationContext private val context: Context,
) : InstallAge {
    override fun isPastGracePeriod(): Boolean {
        val installedAt = try {
            context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        } catch (_: PackageManager.NameNotFoundException) {
            0L
        }
        return AdPolicy.isPastGracePeriod(installedAt, System.currentTimeMillis())
    }
}

/**
 * The one place that decides whether ads show (CL-370). [canShow] stays false when the build has ads
 * off, during the first days after install, and until consent allows ad requests. The SDK, and with
 * it the consent form, starts only once all of that allows it, so onboarding never sees either.
 */
@Singleton
class AdsGate @Inject constructor(
    val platform: AdPlatform,
    private val frequency: AdFrequencyStore,
    private val installAge: InstallAge,
) {
    private val _canShow = MutableStateFlow(false)
    val canShow: StateFlow<Boolean> = _canShow.asStateFlow()

    private var started = false

    /** Called from the activity; does nothing after the first successful start. */
    fun start(activity: Activity) {
        if (started || !platform.isAvailable || !installAge.isPastGracePeriod()) return
        started = true
        platform.start(activity) { ready -> _canShow.value = ready }
    }

    /** After a PDF was shared or saved: every third export is followed by a full-screen ad. */
    suspend fun onPdfExported(activity: Activity) {
        if (!_canShow.value) return
        if (frequency.recordPdfExport()) platform.showInterstitial(activity)
    }
}
