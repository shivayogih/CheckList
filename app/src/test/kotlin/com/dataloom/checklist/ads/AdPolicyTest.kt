package com.dataloom.checklist.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdPolicyTest {

    private val day = 24L * 60 * 60 * 1000
    private val installed = 1_700_000_000_000L

    @Test
    fun `no ads in the first three days after install`() {
        assertFalse(AdPolicy.isPastGracePeriod(installed, installed))
        assertFalse(AdPolicy.isPastGracePeriod(installed, installed + 3 * day - 1))
        assertTrue(AdPolicy.isPastGracePeriod(installed, installed + 3 * day))
    }

    @Test
    fun `an unknown install time or a clock set back counts as a fresh install`() {
        assertFalse(AdPolicy.isPastGracePeriod(0, installed))
        assertFalse(AdPolicy.isPastGracePeriod(installed, installed - 10 * day))
    }

    @Test
    fun `a native row follows every fifteenth row but never the last one`() {
        val after = (0 until 40).filter { AdPolicy.hasNativeAdAfter(it, 40) }
        assertEquals(listOf(14, 29), after)
        assertFalse("Not after the last row", AdPolicy.hasNativeAdAfter(14, 15))
        assertTrue(AdPolicy.hasNativeAdAfter(14, 16))
        assertEquals(emptyList<Int>(), (0 until 14).filter { AdPolicy.hasNativeAdAfter(it, 14) })
    }

    @Test
    fun `an interstitial follows every third PDF export only`() {
        var count = 0
        val shown = (1..9).map {
            val decision = AdPolicy.afterPdfExport(count)
            count = decision.exportsSinceAd
            decision.showInterstitial
        }
        assertEquals(listOf(false, false, true, false, false, true, false, false, true), shown)
    }

    @Test
    fun `a corrupt stored count never shows an ad early`() {
        assertEquals(ExportDecision(showInterstitial = false, exportsSinceAd = 1), AdPolicy.afterPdfExport(-5))
        assertEquals(ExportDecision(showInterstitial = true, exportsSinceAd = 0), AdPolicy.afterPdfExport(99))
    }
}
