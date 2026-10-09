package com.dataloom.checklist.keyboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.presentation.common.bringIntoViewWhenFocused
import com.dataloom.checklist.presentation.common.keyboardAwareScreen
import com.dataloom.checklist.presentation.common.rememberDismissKeyboardActions
import com.dataloom.checklist.presentation.common.scrollableForm
import com.dataloom.checklist.presentation.theme.CheckListTheme
import com.dataloom.checklist.testing.HiltComponentActivity
import com.dataloom.checklist.testing.ui.contentBottom
import com.dataloom.checklist.testing.ui.simulateKeyboard
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The keyboard helpers in presentation/common/KeyboardInsets.kt, on a small form shaped like the
 * app's screens: Scaffold, a bottom action bar, a scrolling column of fields.
 *
 * The keyboard is simulated by delivering IME window insets (see ImeSimulation.kt). This proves the
 * layout reacts to the inset; it does not prove real keyboard behaviour (animation, window panning,
 * keyboard apps), which needs an emulator. Robolectric's screen is 320 x 470 dp at 1 px per dp.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class KeyboardInsetsTest {

    private val hiltRule = HiltAndroidRule(this)
    private val composeRule = createAndroidComposeRule<HiltComponentActivity>()

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(hiltRule).around(composeRule)

    private val density get() = composeRule.activity.resources.displayMetrics.density
    private val keyboardDp get() = KEYBOARD_PX / density

    @Test
    fun `control - without the helper the simulated keyboard covers the bottom bar`() {
        composeRule.setContent { Form(helper = false) }
        composeRule.simulateKeyboard(composeRule.activity, KEYBOARD_PX)

        val barBottom = composeRule.onNodeWithTag(BAR).getUnclippedBoundsInRoot().bottom
        // If this fails the simulation is not reaching Compose, and the tests below prove nothing.
        assertEquals(composeRule.contentBottom().value, barBottom.value, 1f)
    }

    @Test
    fun `bottom bar lifts above the keyboard and drops back when it closes`() {
        composeRule.setContent { Form(helper = true) }

        composeRule.simulateKeyboard(composeRule.activity, KEYBOARD_PX)
        val lifted = composeRule.onNodeWithTag(BAR).getUnclippedBoundsInRoot().bottom
        assertTrue(
            "bar bottom $lifted must be above the keyboard",
            lifted.value <= composeRule.contentBottom().value - keyboardDp + 1f,
        )

        composeRule.simulateKeyboard(composeRule.activity, 0)
        val closed = composeRule.onNodeWithTag(BAR).getUnclippedBoundsInRoot().bottom
        assertEquals(composeRule.contentBottom().value, closed.value, 1f)
    }

    @Test
    fun `a long form scrolls and its last field can be reached above the keyboard`() {
        composeRule.setContent { Form(helper = true) }
        composeRule.simulateKeyboard(composeRule.activity, KEYBOARD_PX)

        composeRule.onNodeWithTag(FORM).assert(hasScrollAction())
        val last = composeRule.onNodeWithTag("field-${FIELD_COUNT - 1}")
        last.performScrollTo().assertIsDisplayed()
        val lastBottom = last.getUnclippedBoundsInRoot().bottom
        assertTrue(
            "last field bottom $lastBottom must be above the keyboard",
            lastBottom.value <= composeRule.contentBottom().value - keyboardDp + 1f,
        )
    }

    @Test
    fun `a field that gains focus off screen is scrolled into view`() {
        val focus = FocusRequester()
        composeRule.setContent {
            Scaffold(modifier = Modifier.keyboardAwareScreen()) { padding ->
                Column(Modifier.padding(padding).scrollableForm().testTag(FORM)) {
                    Box(Modifier.fillMaxWidth().height(1200.dp))
                    OutlinedTextField(
                        value = "",
                        onValueChange = {},
                        modifier = Modifier
                            .testTag(FAR_FIELD)
                            .focusRequester(focus)
                            .bringIntoViewWhenFocused(),
                    )
                }
            }
        }
        composeRule.simulateKeyboard(composeRule.activity, KEYBOARD_PX)

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitUntil(WAIT_MILLIS) {
            runCatching { composeRule.onNodeWithTag(FAR_FIELD).assertIsDisplayed() }.isSuccess
        }
    }

    @Test
    fun `tapping empty space clears focus`() {
        composeRule.setContent { Form(helper = true) }
        composeRule.onNodeWithTag("field-1").performClick().assertIsFocused()

        composeRule.onNodeWithTag(EMPTY_AREA).performClick()

        composeRule.onNodeWithTag("field-1").assertIsNotFocused()
    }

    @Test
    fun `the search key closes the keyboard and clears focus`() {
        composeRule.setContent { Form(helper = true) }
        composeRule.onNodeWithTag("field-1").performClick().assertIsFocused()

        composeRule.onNodeWithTag("field-1").performImeAction()

        composeRule.onNodeWithTag("field-1").assertIsNotFocused()
    }

    // ----------------------------------------------------------------------------------------

    /** A form like the app's: bottom action bar, empty space, then a scrolling column of [FIELD_COUNT] fields. */
    @Composable
    private fun Form(helper: Boolean) {
        CheckListTheme {
            Scaffold(
                modifier = if (helper) Modifier.keyboardAwareScreen() else Modifier,
                bottomBar = { Button(onClick = {}, modifier = Modifier.fillMaxWidth().testTag(BAR)) { Text("Save") } },
            ) { padding ->
                val dismiss = rememberDismissKeyboardActions()
                val column = Modifier.fillMaxSize().padding(padding)
                Column(modifier = (if (helper) column.scrollableForm() else column).testTag(FORM)) {
                    Box(Modifier.fillMaxWidth().height(48.dp).testTag(EMPTY_AREA))
                    repeat(FIELD_COUNT) { index ->
                        var text by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = dismiss,
                            modifier = Modifier.fillMaxWidth().testTag("field-$index"),
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val KEYBOARD_PX = 200
        const val FIELD_COUNT = 8
        const val BAR = "bar"
        const val FORM = "form"
        const val FAR_FIELD = "far-field"
        const val EMPTY_AREA = "empty-area"
        const val WAIT_MILLIS = 5_000L
    }
}
