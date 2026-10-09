package com.dataloom.checklist.keyboard

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.dataloom.checklist.R
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.SectionId
import com.dataloom.checklist.domain.repository.CatalogRepository
import com.dataloom.checklist.domain.repository.ChecklistRepository
import com.dataloom.checklist.domain.usecase.CreateChecklistUseCase
import com.dataloom.checklist.domain.usecase.DomainResult
import com.dataloom.checklist.navigation.AddCategoriesRoute
import com.dataloom.checklist.navigation.AddItemsRoute
import com.dataloom.checklist.navigation.CheckListNavHost
import com.dataloom.checklist.navigation.ChecklistDetailRoute
import com.dataloom.checklist.navigation.CreateChecklistRoute
import com.dataloom.checklist.navigation.ItemEditorRoute
import com.dataloom.checklist.navigation.ProfileRoute
import com.dataloom.checklist.onboarding.StartDestination
import com.dataloom.checklist.presentation.theme.CheckListTheme
import com.dataloom.checklist.testing.HiltComponentActivity
import com.dataloom.checklist.testing.ui.awaitNode
import com.dataloom.checklist.testing.ui.awaitText
import com.dataloom.checklist.testing.ui.contentBottom
import com.dataloom.checklist.testing.ui.plural
import com.dataloom.checklist.testing.ui.simulateKeyboard
import com.dataloom.checklist.testing.ui.string
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Every screen with a text field, rendered through the real NavHost with a simulated keyboard
 * (CL-300..304): its primary action sits above the keyboard, the form can scroll, and the field the
 * user types in is reachable. The audit table is in docs/ui-keyboard-and-insets.md.
 *
 * Robolectric delivers IME insets to Compose; it has no real keyboard, so animation, system window
 * panning and keyboard apps are not covered (an emulator check is listed in the doc).
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class KeyboardScreensTest {

    private val hiltRule = HiltAndroidRule(this)
    private val composeRule = createAndroidComposeRule<HiltComponentActivity>()

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(hiltRule).around(composeRule)

    @Inject lateinit var catalog: CatalogRepository

    @Inject lateinit var checklists: ChecklistRepository

    @Inject lateinit var createChecklist: CreateChecklistUseCase

    private lateinit var navController: NavHostController

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun `create checklist keeps Create above the keyboard and the form scrolls`() {
        launchApp()
        navigate(CreateChecklistRoute)
        val title = composeRule.awaitNode(hasSetTextAction() and hasText(string(R.string.create_title_label)))
        title.performClick()
        composeRule.simulateKeyboard(composeRule.activity, KEYBOARD_PX)

        assertAboveKeyboard(button(string(R.string.action_create)))
        assertAboveKeyboard(hasSetTextAction() and hasText(string(R.string.create_title_label)))
        assertScrollable()
    }

    @Test
    fun `new item keeps Add item above the keyboard and every field reachable`() {
        val (checklist, section) = seedChecklist()
        launchApp()
        navigate(ItemEditorRoute(checklist.value, section.value, initialName = "Banana chips"))
        composeRule.awaitText(string(R.string.item_new_title, GROCERIES))
        composeRule.simulateKeyboard(composeRule.activity, KEYBOARD_PX)

        assertAboveKeyboard(button(string(R.string.detail_add_item)))
        assertScrollable()
        // The last field of the form is reachable by scrolling and ends above the keyboard.
        assertAboveKeyboard(hasSetTextAction() and hasText(string(R.string.item_notes_label)), scrollFirst = true)
    }

    @Test
    fun `add items keeps the action above the keyboard and the search field visible`() {
        val (checklist, section) = seedChecklist()
        launchApp()
        navigate(AddItemsRoute(checklist.value, section.value))
        composeRule.awaitNode(hasSetTextAction() and hasText(string(R.string.add_items_search_label))).performClick()
        composeRule.simulateKeyboard(composeRule.activity, KEYBOARD_PX)

        assertAboveKeyboard(button(plural(R.plurals.add_selected_items, 0, "0")))
        assertAboveKeyboard(hasSetTextAction() and hasText(string(R.string.add_items_search_label)))
        assertScrollable()
    }

    @Test
    fun `add categories keeps the action above the keyboard and the list scrolls`() {
        val (checklist, _) = seedChecklist()
        launchApp()
        navigate(AddCategoriesRoute(checklist.value))
        composeRule.awaitText(string(R.string.category_create))
        composeRule.simulateKeyboard(composeRule.activity, KEYBOARD_PX)

        assertAboveKeyboard(button(plural(R.plurals.add_selected_categories, 0, "0")))
        assertScrollable()
    }

    @Test
    fun `profile keeps Save reachable above the keyboard`() {
        launchApp()
        navigate(ProfileRoute)
        composeRule.awaitText(string(R.string.profile_intro))
        composeRule.awaitNode(hasSetTextAction() and hasText(string(R.string.profile_name))).performClick()
        composeRule.simulateKeyboard(composeRule.activity, KEYBOARD_PX)

        assertScrollable()
        assertAboveKeyboard(button(string(R.string.action_save)), scrollFirst = true)
    }

    @Test
    fun `checklist detail list scrolls with the keyboard open`() {
        val (checklist, _) = seedChecklist()
        launchApp()
        navigate(ChecklistDetailRoute(checklist.value))
        composeRule.awaitText(string(R.string.detail_section_empty))
        composeRule.simulateKeyboard(composeRule.activity, KEYBOARD_PX)

        assertScrollable()
    }

    @Test
    fun `home list scrolls and the search field is above the keyboard`() {
        seedChecklist()
        launchApp()
        val search = hasSetTextAction() and hasText(string(R.string.home_search_label))
        composeRule.awaitNode(search).performClick()
        composeRule.simulateKeyboard(composeRule.activity, KEYBOARD_PX)

        assertAboveKeyboard(search)
        assertScrollable()
    }

    // ----------------------------------------------------------------------------------------

    private fun button(text: String): SemanticsMatcher = hasText(text) and hasClickAction()

    /** Fails unless the first node matching [matcher] ends above the keyboard (scrolling to it first if asked). */
    private fun assertAboveKeyboard(matcher: SemanticsMatcher, scrollFirst: Boolean = false) {
        val node = composeRule.awaitNode(matcher)
        if (scrollFirst) node.performScrollTo()
        val bottom = composeRule.onAllNodes(matcher).onFirst().getUnclippedBoundsInRoot().bottom.value
        val density = composeRule.activity.resources.displayMetrics.density
        val limit = composeRule.contentBottom().value - KEYBOARD_PX / density
        assertTrue("node ends at $bottom dp but the keyboard starts at $limit dp", bottom <= limit + TOLERANCE_DP)
    }

    private fun assertScrollable() {
        val scrollables = composeRule.onAllNodes(hasScrollAction()).fetchSemanticsNodes()
        assertTrue("the screen has no scrollable container", scrollables.isNotEmpty())
    }

    private fun launchApp() {
        composeRule.setContent {
            CheckListTheme {
                navController = rememberNavController()
                CheckListNavHost(StartDestination.HOME, navController)
            }
        }
        composeRule.waitForIdle()
    }

    private fun navigate(route: Any) {
        composeRule.runOnIdle { navController.navigate(route) }
    }

    /** A checklist "Goa Trip" with the Groceries category and no items. */
    private fun seedChecklist(): Pair<ChecklistId, SectionId> = runBlocking {
        val groceries = catalog.observeCategories("en").first().first { it.iconKey == "🛒" }.id
        val created = createChecklist("Goa Trip", "Things for the beach", listOf(groceries)) as DomainResult.Success
        val checklist = created.value.id
        checklist to checklists.observeChecklist(checklist, "en").first()!!.sections.single().id
    }

    private companion object {
        const val KEYBOARD_PX = 200
        const val TOLERANCE_DP = 1f
        const val GROCERIES = "Groceries"
    }
}
