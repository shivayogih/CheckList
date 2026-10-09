package com.dataloom.checklist.presentation.common

import android.view.WindowManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.delay

/*
 * The one place that decides how screens behave when the soft keyboard opens (CL-300..304, see
 * docs/ui-keyboard-and-insets.md). Screens use these helpers instead of ad hoc imePadding() calls.
 *
 * Why this is needed: MainActivity is edge-to-edge and the app targets Android 15+, where
 * `windowSoftInputMode="adjustResize"` no longer resizes the window. The keyboard is then just an
 * inset, and nothing moves out of its way unless the layout consumes that inset.
 */

/** How long the keyboard animation takes to settle; a focused field is re-revealed after it. */
private const val IME_SETTLE_MILLIS = 300L

/**
 * Put this on the `Scaffold` of every screen, not on its content:
 * `Scaffold(modifier = Modifier.keyboardAwareScreen())`.
 *
 * The whole Scaffold shrinks to the area above the keyboard, so the content, the bottom bar and the
 * snackbar all sit above it, and the Scaffold's own navigation-bar padding is dropped while the
 * keyboard is open (imePadding consumes the inset the nav bar is part of, so nothing is padded twice).
 * Do not also add imePadding() to the bottom bar or the content.
 */
fun Modifier.keyboardAwareScreen(): Modifier = imePadding()

/**
 * For the content of a `ModalBottomSheet` (it lives in its own window, outside the Scaffold): clear of the
 * navigation bar and lifted above the keyboard, nothing padded twice. Use on the sheet's root column.
 */
fun Modifier.keyboardAwareSheet(): Modifier = navigationBarsPadding().imePadding()

/**
 * A column that scrolls and gives up the keyboard when the user taps outside a field or drags the
 * content (see [dismissKeyboardOnOutsideInteraction]). Use as
 * `Column(Modifier.fillMaxSize().padding(padding).scrollableForm())`; apply it before any inner padding
 * so the padding scrolls with the content.
 */
@Composable
fun Modifier.scrollableForm(): Modifier =
    dismissKeyboardOnOutsideInteraction().verticalScroll(rememberScrollState())

/**
 * Hides the keyboard when the user scrolls the content with a finger, and clears focus (which also
 * hides the keyboard) when they tap on empty space. Taps on fields, buttons and other clickables are
 * unaffected: they consume the tap first. Works for Column+verticalScroll and for LazyColumn when
 * placed on the container's modifier, before the scrolling.
 */
@Composable
fun Modifier.dismissKeyboardOnOutsideInteraction(): Modifier {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // Programmatic scrolls (bringIntoView when a field gains focus or the keyboard opens) also reach
    // the nested-scroll chain as UserInput, so the source alone cannot tell them from a finger drag.
    // Hiding on those closed the keyboard the moment it opened (CL-340). Only a scroll made while a
    // finger is on the screen hides it.
    val touch = remember { TouchTracker() }
    val hideOnScroll = remember(keyboard, touch) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (touch.pressed && source == NestedScrollSource.UserInput && available.y != 0f) keyboard?.hide()
                return Offset.Zero
            }
        }
    }
    return pointerInput(touch) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                touch.pressed = event.changes.any { it.pressed }
            }
        }
    }.nestedScroll(hideOnScroll).pointerInput(focusManager) {
        detectTapGestures { focusManager.clearFocus() }
    }
}

/** Whether a finger is on the content; read by the nested-scroll connection, so not Compose state. */
private class TouchTracker {
    var pressed = false
}

/**
 * Keeps a focused field, together with its error text and counter, visible. Apply to the text field
 * (the supporting text is part of it) or to a column that holds a field and its helper text.
 *
 * The field's own cursor tracking already reveals the caret; this additionally reveals the whole
 * block when the field gains focus and again once the keyboard has finished opening, because the
 * viewport shrinks while the keyboard animates and the first request can be overtaken by it.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun Modifier.bringIntoViewWhenFocused(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(focused, imeVisible) {
        if (focused) {
            requester.bringIntoView()
            if (imeVisible) {
                delay(IME_SETTLE_MILLIS)
                requester.bringIntoView()
            }
        }
    }
    return bringIntoViewRequester(requester).onFocusChanged { focused = it.hasFocus }
}

/**
 * Keyboard actions for the last field of a form, and for search fields: the Done, Search and Go keys
 * close the keyboard and clear focus. (Next already moves focus to the next field by default, and
 * Compose's default for Done hides the keyboard; Search and Go do nothing unless told to.)
 */
@Composable
fun rememberDismissKeyboardActions(): KeyboardActions {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return remember(focusManager, keyboard) {
        val dismiss: () -> Unit = {
            keyboard?.hide()
            focusManager.clearFocus()
        }
        KeyboardActions(onDone = { dismiss() }, onSearch = { dismiss() }, onGo = { dismiss() })
    }
}

/**
 * Call inside a dialog's content: makes the dialog window resize when the keyboard opens, so the
 * dialog re-centres in the space above it and its (scrollable) content stays reachable. Without
 * it a dialog relies on the platform default, which pans or does nothing depending on the Android
 * version. Safe to call where there is no dialog window (it does nothing).
 */
@Composable
fun ResizeDialogForKeyboard() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        @Suppress("DEPRECATION")
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
}
