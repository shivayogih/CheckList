package com.dataloom.checklist.testing.ui

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.TimeUnit
import org.robolectric.shadows.ShadowLooper

/*
 * Shared helpers for Compose UI tests on Robolectric (CL-171). The photos track and later screens
 * reuse them; keep them free of screen-specific knowledge.
 *
 * Why the waits advance the main looper: in Robolectric's paused-looper mode the clock only moves
 * when a test moves it. ViewModels debounce searches and Room delivers results from its own
 * threads, so a test waits by polling with real time while also letting simulated time pass.
 */

private const val STEP_MS = 16L
private const val DEFAULT_TIMEOUT_MS = 10_000L

/** Lets [millis] of simulated time pass on the main looper (debounces, delayed posts). */
fun advanceMainLooper(millis: Long) {
    ShadowLooper.idleMainLooper(millis, TimeUnit.MILLISECONDS)
}

/** Waits until a node matching [matcher] exists, then returns the first one. */
fun ComposeTestRule.awaitNode(
    matcher: SemanticsMatcher,
    useUnmergedTree: Boolean = false,
    timeoutMillis: Long = DEFAULT_TIMEOUT_MS,
): SemanticsNodeInteraction {
    waitUntil(timeoutMillis) {
        advanceMainLooper(STEP_MS)
        onAllNodes(matcher, useUnmergedTree).fetchSemanticsNodes().isNotEmpty()
    }
    return onAllNodes(matcher, useUnmergedTree).onFirst()
}

/** Waits until a node with exactly this text (or content description) exists. */
fun ComposeTestRule.awaitText(text: String, substring: Boolean = false): SemanticsNodeInteraction =
    awaitNode(hasText(text, substring = substring))

/** Waits until no node matches [matcher]. */
fun ComposeTestRule.awaitGone(matcher: SemanticsMatcher, timeoutMillis: Long = DEFAULT_TIMEOUT_MS) {
    waitUntil(timeoutMillis) {
        advanceMainLooper(STEP_MS)
        onAllNodes(matcher).fetchSemanticsNodes().isEmpty()
    }
}

/** Resolves a string the way the app does, so tests do not hard-code English copy. */
fun string(@StringRes id: Int, vararg args: Any): String =
    ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String =
    ApplicationProvider.getApplicationContext<Context>().resources.getQuantityString(id, count, *args)
