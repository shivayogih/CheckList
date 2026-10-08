package com.dataloom.checklist.presentation.components

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.testing.screenshot.assertNoTextOverflow
import com.dataloom.checklist.testing.screenshot.assertTouchTargetsAtLeast48
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the shared components say to TalkBack and how they react to taps, and a check that the layout
 * assertions themselves catch a violation (an assertion that never fails proves nothing).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h917dp-hdpi", application = Application::class)
class ComponentSemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `item row is one item that reads the full sentence and toggles on tap`() {
        var checked by mutableStateOf(false)
        var menuTaps = 0
        compose.setContent {
            PreviewSurface {
                ItemRow(
                    name = "Rice",
                    checked = checked,
                    onCheckedChange = { checked = it },
                    unitText = "5 KG",
                    completedLabel = "Completed",
                    accessibilityLabel = "Rice, 5 kilograms, not completed",
                    menu = { AppIconButton(androidx.compose.ui.res.painterResource(com.dataloom.checklist.R.drawable.ic_more_vert), "More options for Rice", { menuTaps++ }) },
                )
            }
        }
        compose.onNodeWithContentDescription("Rice, 5 kilograms, not completed").performClick()
        assertTrue(checked)
        // The menu is its own item and must not tick the row.
        compose.onNodeWithContentDescription("More options for Rice").performClick()
        assertTrue("menu tap must not toggle the item", checked)
        assertEquals(1, menuTaps)
    }

    @Test
    fun `ticked item row shows the completed label`() {
        compose.setContent {
            PreviewSurface {
                ItemRow(name = "Sugar", checked = true, onCheckedChange = {}, unitText = "2 KG", completedLabel = "Completed")
            }
        }
        compose.onNodeWithText("Completed", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `quantity stepper buttons are named and react`() {
        var value by mutableStateOf("5")
        compose.setContent {
            PreviewSurface {
                QuantityStepper(
                    value = value,
                    onValueChange = { value = it },
                    onDecrement = { value = (value.toInt() - 1).toString() },
                    onIncrement = { value = (value.toInt() + 1).toString() },
                    valueDescription = "Quantity",
                    decrementDescription = "Less",
                    incrementDescription = "More",
                )
            }
        }
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithContentDescription("Less").performClick()
        assertEquals("6", value)
        compose.assertTouchTargetsAtLeast48()
    }

    @Test
    fun `selectable row that is disabled does not toggle`() {
        var toggles = 0
        compose.setContent {
            PreviewSurface {
                SelectableRow(
                    label = "Rava",
                    checked = false,
                    onCheckedChange = { toggles++ },
                    enabled = false,
                    stateDescription = "Already added",
                )
            }
        }
        compose.onNodeWithText("Rava").assertIsNotEnabled()
        compose.onNodeWithText("Rava").performClick()
        assertEquals(0, toggles)
    }

    @Test
    fun `switch row is a switch that toggles as a whole row`() {
        var on by mutableStateOf(false)
        compose.setContent {
            PreviewSurface {
                SettingsSwitchRow(title = "High contrast", checked = on, onCheckedChange = { on = it })
            }
        }
        compose.onNodeWithText("High contrast").assertIsOff()
        compose.onNodeWithText("High contrast").performClick()
        compose.onNodeWithText("High contrast").assertIsOn()
        assertTrue(on)
    }

    @Test
    fun `segmented choice marks the chosen segment as selected`() {
        var index by mutableStateOf(0)
        compose.setContent {
            PreviewSurface { SegmentedChoice(listOf("Active", "Archived"), index, { index = it }) }
        }
        compose.onNodeWithText("Active").assertIsSelected()
        compose.onNodeWithText("Archived").performClick()
        compose.onNodeWithText("Archived").assertIsSelected()
        assertEquals(1, index)
    }

    @Test
    fun `icon button has a description and a 48 dp target`() {
        var taps = 0
        compose.setContent {
            PreviewSurface {
                AppIconButton(androidx.compose.ui.res.painterResource(com.dataloom.checklist.R.drawable.ic_share), "Share", { taps++ })
            }
        }
        compose.onNode(hasContentDescription("Share")).assertIsEnabled().performClick()
        assertEquals(1, taps)
        compose.assertTouchTargetsAtLeast48()
    }

    @Test
    fun `touch target assertion fails for a 30 dp clickable`() {
        compose.setContent { Text("tiny", Modifier.size(30.dp).clickable { }) }
        assertThrows(IllegalStateException::class.java) { compose.assertTouchTargetsAtLeast48() }
    }

    @Test
    fun `overflow assertion fails for text that is cut`() {
        compose.setContent {
            Column {
                Text(
                    "A long label that cannot fit in one line of this narrow box",
                    modifier = Modifier.width(80.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        assertThrows(IllegalStateException::class.java) { compose.assertNoTextOverflow() }
    }

    @Test
    fun `wrapping text passes the overflow assertion`() {
        compose.setContent { Column { Text("A long label that wraps onto several lines", Modifier.width(80.dp)) } }
        compose.assertNoTextOverflow()
    }
}
