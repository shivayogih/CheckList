package com.dataloom.checklist.accessibility

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.dataloom.checklist.R
import com.dataloom.checklist.domain.model.CategoryId
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.Quantity
import com.dataloom.checklist.domain.model.SectionId
import com.dataloom.checklist.domain.model.UnitCode
import com.dataloom.checklist.domain.repository.CatalogRepository
import com.dataloom.checklist.domain.repository.ChecklistRepository
import com.dataloom.checklist.domain.usecase.AddCustomItemUseCase
import com.dataloom.checklist.domain.usecase.CreateChecklistUseCase
import com.dataloom.checklist.domain.usecase.CustomItemOutcome
import com.dataloom.checklist.domain.usecase.DomainResult
import com.dataloom.checklist.domain.usecase.SetItemCompletedUseCase
import com.dataloom.checklist.navigation.AddCategoriesRoute
import com.dataloom.checklist.navigation.AddItemsRoute
import com.dataloom.checklist.navigation.CheckListNavHost
import com.dataloom.checklist.navigation.ChecklistDetailRoute
import com.dataloom.checklist.navigation.CreateChecklistRoute
import com.dataloom.checklist.navigation.ItemEditorRoute
import com.dataloom.checklist.navigation.LanguageRoute
import com.dataloom.checklist.navigation.ProfileRoute
import com.dataloom.checklist.navigation.RemindersRoute
import com.dataloom.checklist.navigation.SettingsRoute
import com.dataloom.checklist.onboarding.StartDestination
import com.dataloom.checklist.presentation.theme.CheckListTheme
import com.dataloom.checklist.reminder.Reminder
import com.dataloom.checklist.reminder.ReminderStore
import com.dataloom.checklist.testing.HiltComponentActivity
import com.dataloom.checklist.testing.ui.assertAccessible
import com.dataloom.checklist.testing.ui.awaitNode
import com.dataloom.checklist.testing.ui.awaitText
import com.dataloom.checklist.testing.ui.string
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The accessibility audit of every screen (CL-174), at 100% and 200% font scale: labels, 48dp
 * touch targets, a heading per screen, state descriptions on checkbox rows, and no clipped or
 * cut-off text (see [assertAccessible]). Native graphics give real text measurement.
 *
 * Screens are reached directly through the real NavHost with real data in Room, so a new screen
 * only needs one more test here.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(ParameterizedRobolectricTestRunner::class)
class ScreenAccessibilityTest(private val fontScale: Float) {

    private val hiltRule = HiltAndroidRule(this)
    private val composeRule = createAndroidComposeRule<HiltComponentActivity>()

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(hiltRule).around(composeRule)

    @Inject lateinit var catalog: CatalogRepository

    @Inject lateinit var checklists: ChecklistRepository

    @Inject lateinit var createChecklist: CreateChecklistUseCase

    @Inject lateinit var addCustomItem: AddCustomItemUseCase

    @Inject lateinit var setItemCompleted: SetItemCompletedUseCase

    @Inject lateinit var reminders: ReminderStore

    private lateinit var navController: NavHostController

    private val screen get() = "font scale $fontScale"

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun `home on first use`() {
        launchApp()
        composeRule.awaitText(string(R.string.home_empty_title))
        composeRule.assertAccessible("Home, first use, $screen")
    }

    @Test
    fun `home with checklists`() {
        seedChecklist()
        launchApp()
        composeRule.awaitText(TITLE)
        composeRule.assertAccessible("Home, $screen")
    }

    @Test
    fun `create checklist`() {
        launchApp()
        navigate(CreateChecklistRoute)
        composeRule.awaitNode(hasText(GROCERIES) and isToggleable())
        composeRule.assertAccessible("Create checklist, $screen")
    }

    @Test
    fun `checklist detail with done and open items`() {
        val (checklist, _) = seedChecklist()
        launchApp()
        navigate(ChecklistDetailRoute(checklist.value))
        composeRule.awaitNode(hasText("Rice") and isToggleable())
        composeRule.assertAccessible("Checklist detail, $screen")
    }

    @Test
    fun `add categories`() {
        val (checklist, _) = seedChecklist()
        launchApp()
        navigate(AddCategoriesRoute(checklist.value))
        composeRule.awaitNode(hasText(VEGETABLES) and isToggleable())
        composeRule.assertAccessible("Add categories, $screen")
    }

    @Test
    fun `add items with a selected suggestion`() {
        val (checklist, section) = seedChecklist()
        launchApp()
        navigate(AddItemsRoute(checklist.value, section.value))
        composeRule.awaitNode(hasSetTextAction() and hasContentDescription(string(R.string.add_items_search_label)))
            .performTextInput("Sugar")
        composeRule.awaitNode(hasText("Sugar") and isToggleable()).performClick()
        composeRule.awaitText(string(R.string.item_quantity_optional))
        composeRule.assertAccessible("Add items, $screen")
    }

    @Test
    fun `new custom item`() {
        val (checklist, section) = seedChecklist()
        launchApp()
        navigate(ItemEditorRoute(checklist.value, section.value, initialName = "Banana chips"))
        composeRule.awaitText(string(R.string.item_new_title, GROCERIES))
        composeRule.assertAccessible("New item, $screen")
    }

    @Test
    fun `reminders with one reminder`() {
        val (checklist, _) = seedChecklist()
        runBlocking { reminders.upsert(Reminder("reminder-1", checklist.value, REMINDER_TIME)) }
        launchApp()
        navigate(RemindersRoute)
        composeRule.awaitNode(hasContentDescription(string(R.string.reminder_delete_named, TITLE)))
        composeRule.assertAccessible("Reminders, $screen")
    }

    @Test
    fun settings() {
        launchApp()
        navigate(SettingsRoute)
        composeRule.awaitText(string(R.string.settings_export_all))
        composeRule.assertAccessible("Settings, $screen")
    }

    @Test
    fun language() {
        launchApp()
        navigate(LanguageRoute)
        composeRule.awaitText(string(R.string.settings_language_system))
        composeRule.assertAccessible("Language, $screen")
    }

    @Test
    fun profile() {
        launchApp()
        navigate(ProfileRoute)
        composeRule.awaitText(string(R.string.profile_intro))
        composeRule.assertAccessible("Profile, $screen")
    }

    @Test
    @Config(qualifiers = "kn")
    fun `checklist detail in Kannada`() {
        val (checklist, _) = seedChecklist(language = "kn")
        launchApp()
        navigate(ChecklistDetailRoute(checklist.value))
        composeRule.awaitNode(hasText("Rice") and isToggleable())
        composeRule.assertAccessible("Checklist detail in Kannada, $screen")
    }

    // ----------------------------------------------------------------------------------------

    private fun launchApp() {
        composeRule.setContent {
            WithFontScale(fontScale) {
                CheckListTheme {
                    navController = rememberNavController()
                    CheckListNavHost(StartDestination.HOME, navController)
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun navigate(route: Any) {
        composeRule.runOnIdle { navController.navigate(route) }
    }

    /** A checklist "Goa Trip" with Groceries: Rice (5 kg, done) and Soap (open). */
    private fun seedChecklist(language: String = "en"): Pair<ChecklistId, SectionId> = runBlocking {
        val groceries: CategoryId = catalog.observeCategories(language).first().first { it.iconKey == "🛒" }.id
        val created = createChecklist(TITLE, "Things for the beach", listOf(groceries)) as DomainResult.Success
        val checklist = created.value.id
        val section = checklists.observeChecklist(checklist, language).first()!!.sections.single().id
        val riceResult = addCustomItem(checklist, section, "Rice", language, Quantity.parse("5"), UnitCode("KG"))
        val rice = (riceResult as DomainResult.Success).value as CustomItemOutcome.Added
        addCustomItem(checklist, section, "Soap", language)
        setItemCompleted(rice.itemId, completed = true)
        checklist to section
    }

    companion object {
        const val TITLE = "Goa Trip"
        const val GROCERIES = "Groceries & Staples"
        const val VEGETABLES = "Vegetables"

        /** 2100-01-01, far enough ahead to stay a future reminder. */
        const val REMINDER_TIME = 4_102_444_800_000L

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "fontScale={0}")
        fun scales(): List<Array<Any>> = listOf(arrayOf(1f), arrayOf(2f))
    }
}

@OptIn(ExperimentalTestApi::class)
@Composable
private fun WithFontScale(fontScale: Float, content: @Composable () -> Unit) {
    DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale), content)
}
