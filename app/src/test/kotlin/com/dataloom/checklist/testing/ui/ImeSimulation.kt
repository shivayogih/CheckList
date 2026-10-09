package com.dataloom.checklist.testing.ui

import android.app.Activity
import android.view.ViewGroup
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/*
 * Simulated soft keyboard for Compose tests on Robolectric (CL-300..304).
 *
 * Robolectric has no keyboard. What it can do is deliver window insets to the Compose view, so the
 * test sets "the keyboard is [heightPx] tall" and Compose lays out exactly as it would when the real
 * IME reports that inset. That proves the layout reacts to the inset (nothing is padded twice, bars
 * lift, content scrolls). It cannot prove how a real keyboard animates, how the system decides to
 * pan or resize a dialog window, or what a particular keyboard app does: those need an emulator.
 */

/** Tells the Compose content hosted by [activity] that the keyboard is now [heightPx] tall (0 hides it). */
fun ComposeTestRule.simulateKeyboard(activity: Activity, heightPx: Int) {
    runOnUiThread {
        val host = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, heightPx))
            .setVisible(WindowInsetsCompat.Type.ime(), heightPx > 0)
            .build()
        ViewCompat.dispatchApplyWindowInsets(host, insets)
    }
    waitForIdle()
}

/** The bottom edge of the Compose content, in dp: where the screen ends and the keyboard would start. */
fun ComposeTestRule.contentBottom(): Dp = onRoot().getUnclippedBoundsInRoot().bottom
