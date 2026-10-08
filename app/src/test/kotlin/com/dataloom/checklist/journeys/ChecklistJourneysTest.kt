package com.dataloom.checklist.journeys

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.dataloom.checklist.MainActivity
import com.dataloom.checklist.R
import com.dataloom.checklist.testing.ui.awaitGone
import com.dataloom.checklist.testing.ui.awaitNode
import com.dataloom.checklist.testing.ui.awaitText
import com.dataloom.checklist.testing.ui.plural
import com.dataloom.checklist.testing.ui.string
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Journeys J1 and J2 end to end on the JVM (CL-171): the real activity, navigation, ViewModels, use
 * cases and Room database (a fresh file per test under Robolectric), with the bundled seed catalog.
 * Only the Android framework is simulated. Copy comes from string resources, never hard-coded.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class ChecklistJourneysTest {

    private val hiltRule = HiltAndroidRule(this)
    private val composeRule = createAndroidComposeRule<MainActivity>()

    // Hilt's test component must exist before the activity starts and injects itself.
    @get:Rule
    val rules: TestRule = RuleChain.outerRule(hiltRule).around(composeRule)

    @Test
    fun `create a checklist with a category from the first-use screen`() {
        createChecklist("Goa Trip")

        composeRule.awaitText(string(R.string.detail_add_item_to, GROCERIES))
        composeRule.awaitNode(hasText("Goa Trip") and isHeading())
    }

    @Test
    fun `add a catalog item and a custom item, tick one and see progress`() {
        createChecklist("Weekly shopping")

        addCatalogItem("Rice")
        addCustomItem("Banana chips")

        composeRule.awaitText(progress(completed = 0, total = 2))
        val rice = composeRule.awaitNode(itemRow("Rice")).assertIsOff()
            .assert(hasStateDescription(string(R.string.state_not_completed)))

        rice.performClick()

        composeRule.awaitText(progress(completed = 1, total = 2))
        composeRule.awaitNode(itemRow("Rice")).assertIsOn()
            .assert(hasStateDescription(string(R.string.state_completed)))
        composeRule.awaitNode(itemRow("Banana chips")).assertIsOff()
    }

    @Test
    fun `edit an item's quantity and unit`() {
        createChecklist("Monthly groceries")
        addCatalogItem("Rice")

        openItemMenu("Rice")
        composeRule.awaitNode(hasText(string(R.string.action_edit)) and hasClickAction()).performClick()

        composeRule.awaitText(string(R.string.item_edit_title))
        composeRule.awaitNode(hasSetTextAction() and hasText(string(R.string.item_quantity_optional)))
            .performTextReplacement("2.5")
        composeRule.awaitNode(hasText(string(R.string.unit_button, string(R.string.unit_none))) and hasClickAction())
            .performClick()
        composeRule.awaitText(string(R.string.unit_picker_title))
        composeRule.awaitNode(hasText(string(R.string.unit_kg)) and isSelectable()).performClick()
        composeRule.awaitNode(hasText(string(R.string.action_save)) and hasClickAction()).performClick()

        composeRule.awaitText(string(R.string.item_quantity_with_unit, "2.5", string(R.string.unit_kg)))
        composeRule.awaitNode(hasText(string(R.string.detail_add_item_to, GROCERIES)))
    }

    @Test
    fun `delete an item and bring it back with Undo`() {
        createChecklist("Trip")
        addCustomItem("Sunscreen")

        openItemMenu("Sunscreen")
        composeRule.awaitNode(hasText(string(R.string.action_delete)) and hasClickAction()).performClick()

        composeRule.awaitGone(itemRow("Sunscreen"))
        composeRule.awaitText(string(R.string.detail_item_deleted, "Sunscreen"))
        composeRule.awaitNode(hasText(string(R.string.action_undo)) and hasClickAction()).performClick()

        composeRule.awaitNode(itemRow("Sunscreen")).assertIsOff()
        composeRule.awaitText(progress(completed = 0, total = 1))
    }

    // --- Steps shared by the journeys -------------------------------------------------------

    /** J1: Home (first use) -> Create checklist -> name + Groceries -> Create -> detail screen. */
    private fun createChecklist(title: String) {
        composeRule.awaitNode(hasText(string(R.string.home_create_checklist)) and hasClickAction()).performClick()
        composeRule.awaitNode(hasSetTextAction() and hasText(string(R.string.create_title_label))).performTextInput(title)
        composeRule.awaitNode(hasText(GROCERIES) and isToggleable()).performClick()
        composeRule.awaitNode(hasText(string(R.string.action_create)) and hasClickAction()).performClick()
        composeRule.awaitNode(hasText(title) and isHeading())
    }

    /** J2: "Add item to Groceries" -> search -> tick the suggestion -> "Add 1 item". */
    private fun addCatalogItem(name: String) {
        composeRule.awaitNode(hasText(string(R.string.detail_add_item_to, GROCERIES)) and hasClickAction()).performClick()
        composeRule.awaitNode(hasSetTextAction() and hasText(string(R.string.add_items_search_label))).performTextInput(name)
        composeRule.awaitNode(hasText(name) and isToggleable()).performClick()
        composeRule.awaitNode(hasText(plural(R.plurals.add_selected_items, 1, "1")) and hasClickAction()).performClick()
        composeRule.awaitNode(itemRow(name))
    }

    /** J2: search for a name the catalog does not have -> "Create new item" -> Add item. */
    private fun addCustomItem(name: String) {
        composeRule.awaitNode(hasText(string(R.string.detail_add_item_to, GROCERIES)) and hasClickAction()).performClick()
        composeRule.awaitNode(hasSetTextAction() and hasText(string(R.string.add_items_search_label))).performTextInput(name)
        composeRule.awaitNode(hasText(string(R.string.add_items_create_named, name)) and hasClickAction()).performClick()
        composeRule.awaitText(string(R.string.item_new_title, GROCERIES))
        composeRule.awaitNode(hasText(string(R.string.detail_add_item)) and hasClickAction()).performClick()
        composeRule.awaitNode(itemRow(name))
    }

    private fun openItemMenu(name: String) {
        composeRule.awaitNode(hasContentDescription(string(R.string.item_more_options, name))).performClick()
    }

    private fun progress(completed: Int, total: Int) =
        plural(R.plurals.progress_items_done, completed, completed.toString(), total.toString())

    private fun itemRow(name: String) = hasText(name) and isToggleable()

    private fun isHeading() = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    private fun hasStateDescription(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    private companion object {
        /** The seeded "groceries" category in English (data/src/main/assets/seed/i18n/en.json). */
        const val GROCERIES = "Groceries"
    }
}
