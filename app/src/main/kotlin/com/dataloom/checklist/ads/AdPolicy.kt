package com.dataloom.checklist.ads

/**
 * When ads may appear (CL-370). Pure rules, so they are unit tested without the ad SDK:
 * - none in the first [GRACE_PERIOD_MILLIS] after install;
 * - one native row after every [NATIVE_AD_EVERY] catalogue rows, never after the last row;
 * - one interstitial per [EXPORTS_PER_INTERSTITIAL] PDF exports, on the last of them.
 */
object AdPolicy {
    const val GRACE_PERIOD_MILLIS = 3L * 24 * 60 * 60 * 1000
    const val NATIVE_AD_EVERY = 15
    const val EXPORTS_PER_INTERSTITIAL = 3

    /** An unknown install time (0) or a clock set before it counts as a fresh install. */
    fun isPastGracePeriod(installedAtMillis: Long, nowMillis: Long): Boolean =
        installedAtMillis > 0 && nowMillis - installedAtMillis >= GRACE_PERIOD_MILLIS

    /** Whether a native ad row follows the row at [index] in a list of [rowCount] rows. */
    fun hasNativeAdAfter(index: Int, rowCount: Int): Boolean =
        index in 0 until rowCount - 1 && (index + 1) % NATIVE_AD_EVERY == 0

    /** Counts one more PDF export after [exportsSinceAd] earlier ones. */
    fun afterPdfExport(exportsSinceAd: Int): ExportDecision {
        val exports = exportsSinceAd.coerceAtLeast(0) + 1
        return if (exports >= EXPORTS_PER_INTERSTITIAL) {
            ExportDecision(showInterstitial = true, exportsSinceAd = 0)
        } else {
            ExportDecision(showInterstitial = false, exportsSinceAd = exports)
        }
    }
}

/** What [AdPolicy.afterPdfExport] decided, and the count to store for next time. */
data class ExportDecision(val showInterstitial: Boolean, val exportsSinceAd: Int)
