package com.dataloom.checklist.security

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app is offline (no backend). The assistant and voice input (CL-400) must not change that: the
 * manifests merged into ad-free builds request no INTERNET and no microphone permission, because voice
 * goes through the phone's own recognizer app. The built manifests are checked again in CI by
 * tools/checks/ads_manifest.py; this test catches it before a build.
 */
class OfflinePermissionsTest {

    private val adFreeManifests = listOf(File("src/main/AndroidManifest.xml"), File("src/adsOff/AndroidManifest.xml"))

    @Test
    fun `ad-free manifests request no internet or microphone permission`() {
        val existing = adFreeManifests.filter { it.isFile }
        assertTrue("src/main/AndroidManifest.xml must exist", existing.any { it.path.contains("main") })
        existing.forEach { manifest ->
            val text = manifest.readText()
            assertFalse("$manifest requests INTERNET", "android.permission.INTERNET" in text)
            assertFalse("$manifest requests ACCESS_NETWORK_STATE", "android.permission.ACCESS_NETWORK_STATE" in text)
            assertFalse("$manifest requests RECORD_AUDIO", "android.permission.RECORD_AUDIO" in text)
        }
    }

    @Test
    fun `the speech recognizer is found through a query, not a permission`() {
        val text = File("src/main/AndroidManifest.xml").readText()
        assertTrue("android.speech.action.RECOGNIZE_SPEECH" in text)
    }
}
