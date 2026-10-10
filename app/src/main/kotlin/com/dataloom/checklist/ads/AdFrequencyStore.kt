package com.dataloom.checklist.ads

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Ad frequency caps kept on the phone (CL-370), in the app's settings DataStore. Nothing leaves the device. */
@Singleton
class AdFrequencyStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    /** Counts a PDF export; true when this one should be followed by an interstitial. */
    suspend fun recordPdfExport(): Boolean {
        var show = false
        try {
            dataStore.edit { prefs ->
                val decision = AdPolicy.afterPdfExport(prefs[EXPORTS_SINCE_AD] ?: 0)
                prefs[EXPORTS_SINCE_AD] = decision.exportsSinceAd
                show = decision.showInterstitial
            }
        } catch (_: IOException) {
            // An unreadable file never earns an ad.
            show = false
        }
        return show
    }

    private companion object {
        val EXPORTS_SINCE_AD = intPreferencesKey("ads_pdf_exports_since_interstitial")
    }
}
