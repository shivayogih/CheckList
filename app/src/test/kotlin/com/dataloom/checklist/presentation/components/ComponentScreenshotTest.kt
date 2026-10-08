package com.dataloom.checklist.presentation.components

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.R
import com.dataloom.checklist.testing.screenshot.Sample
import com.dataloom.checklist.testing.screenshot.ScreenshotVariant
import com.dataloom.checklist.testing.screenshot.assertNoTextOverflow
import com.dataloom.checklist.testing.screenshot.assertTouchTargetsAtLeast48
import com.dataloom.checklist.testing.screenshot.captureScene
import com.dataloom.checklist.testing.screenshot.setScreenshotContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * One golden per component and variant (light, dark, 200 % font, Kannada, Tamil) for the shared components
 * (CL-261), plus the layout assertions: no clipped text, no tap target under 48 dp.
 *
 * Record or update the goldens with `-Proborazzi.test.record=true`; CI verifies them with
 * `-Proborazzi.test.verify=true`. Without either property the screenshots are skipped and the assertions run.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h917dp-hdpi", application = Application::class)
class ComponentScreenshotTest(private val variant: ScreenshotVariant) {

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = ScreenshotVariant.entries.map { arrayOf(it) }
    }

    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, content: @Composable (Sample) -> Unit) {
        compose.setScreenshotContent(variant, content)
        compose.assertNoTextOverflow()
        compose.assertTouchTargetsAtLeast48()
        compose.captureScene(name, variant)
    }

    @Test
    fun topBar() = shoot("top_bar") { s ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CheckListTopBar(
                title = s.t("My checklists", "ನನ್ನ ಪಟ್ಟಿಗಳು", "என் பட்டியல்கள்"),
                actions = { TopBarIconButton(painterResource(R.drawable.ic_settings), s.t("Settings", "ಸೆಟ್ಟಿಂಗ್‌ಗಳು", "அமைப்புகள்"), {}) },
            )
            CheckListTopBar(
                title = s.t("Add to Groceries", "ದಿನಸಿಗೆ ಸೇರಿಸಿ", "மளிகையில் சேர்"),
                subtitle = s.t("Diwali Shopping", "ದೀಪಾವಳಿ ಶಾಪಿಂಗ್", "தீபாவளி ஷாப்பிங்"),
                navigationIcon = { TopBarCloseButton(s.t("Close", "ಮುಚ್ಚಿ", "மூடு"), {}) },
                actions = { TopBarTextAction(s.t("Save", "ಉಳಿಸಿ", "சேமி"), {}) },
            )
            // A long title must wrap, never be cut (mockup 28).
            CheckListTopBar(
                title = s.t(
                    "Diwali shopping for the whole family and all our guests",
                    "ಇಡೀ ಕುಟುಂಬ ಮತ್ತು ಎಲ್ಲಾ ಅತಿಥಿಗಳಿಗಾಗಿ ದೀಪಾವಳಿ ಶಾಪಿಂಗ್",
                    "முழு குடும்பத்திற்கும் அனைத்து விருந்தினர்களுக்குமான தீபாவளி ஷாப்பிங்",
                ),
                navigationIcon = { TopBarBackButton(s.t("Back", "ಹಿಂದೆ", "பின்செல்"), {}) },
                actions = {
                    TopBarIconButton(painterResource(R.drawable.ic_share), s.t("Share", "ಹಂಚಿಕೊಳ್ಳಿ", "பகிர்"), {})
                },
            )
        }
    }

    @Test
    fun iconButtons() = shoot("icon_buttons") { s ->
        // Icon only, no visible text; the translated description is what TalkBack and the tooltip say.
        Row {
            AppIconButton(painterResource(R.drawable.ic_search), s.t("Search", "ಹುಡುಕಿ", "தேடு"), {})
            AppIconButton(painterResource(R.drawable.ic_filter_list), s.t("Filter", "ಫಿಲ್ಟರ್", "வடிகட்டு"), {})
            AppIconButton(painterResource(R.drawable.ic_sort), s.t("Sort", "ವಿಂಗಡಿಸಿ", "வரிசைப்படுத்து"), {})
            AppIconButton(painterResource(R.drawable.ic_share), s.t("Share", "ಹಂಚಿಕೊಳ್ಳಿ", "பகிர்"), {})
            AppIconButton(painterResource(R.drawable.ic_more_vert), s.t("More", "ಇನ್ನಷ್ಟು", "மேலும்"), {})
        }
    }

    @Test
    fun searchField() = shoot("search_field") { s ->
        val placeholder = s.t("Search checklists", "ಪಟ್ಟಿಗಳನ್ನು ಹುಡುಕಿ", "பட்டியல்களைத் தேடு")
        val clear = s.t("Clear search", "ಹುಡುಕಾಟ ತೆರವುಗೊಳಿಸಿ", "தேடலை அழி")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SearchField("", {}, placeholder, clear)
            SearchField(s.t("ri", "ಅಕ್ಕಿ", "அரிசி"), {}, placeholder, clear)
        }
    }

    @Test
    fun checklistCard() = shoot("checklist_card") { s ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChecklistCard(
                title = s.t("Diwali Shopping", "ದೀಪಾವಳಿ ಶಾಪಿಂಗ್", "தீபாவளி ஷாப்பிங்"),
                progress = 0.25f,
                doneText = s.t("3 of 12 done", "12 ರಲ್ಲಿ 3 ಮುಗಿದಿದೆ", "12 இல் 3 முடிந்தது"),
                dateText = s.t("Updated today", "ಇಂದು ನವೀಕರಿಸಲಾಗಿದೆ", "இன்று புதுப்பிக்கப்பட்டது"),
                emojis = listOf("🛒", "🥕", "🎁", "🔔"),
                onClick = {},
                menu = { cardMenu(s) },
            )
            ChecklistCard(
                title = s.t("Exam Day", "ಪರೀಕ್ಷೆಯ ದಿನ", "தேர்வு நாள்"),
                description = s.t("10th standard board exam", "10ನೇ ತರಗತಿ ಬೋರ್ಡ್ ಪರೀಕ್ಷೆ", "10ஆம் வகுப்பு வாரியத் தேர்வு"),
                progress = 1f,
                doneText = s.t("All 5 done", "ಎಲ್ಲಾ 5 ಮುಗಿದಿದೆ", "5 உம் முடிந்தது"),
                complete = true,
                dateText = s.t("Updated 5 days ago", "5 ದಿನಗಳ ಹಿಂದೆ", "5 நாட்களுக்கு முன்"),
                emojis = listOf("📝", "✏️"),
                onClick = {},
                menu = { cardMenu(s) },
            )
        }
    }

    @Composable
    private fun cardMenu(s: Sample) {
        OverflowMenu(
            s.t("More options", "ಇನ್ನಷ್ಟು ಆಯ್ಕೆಗಳು", "மேலும் விருப்பங்கள்"),
            listOf(MenuAction(s.t("Delete", "ಅಳಿಸಿ", "நீக்கு")) {}),
        )
    }

    @Test
    fun progressCard() = shoot("progress_card") { s ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ProgressCard(
                countText = s.t("5 of 12 done", "12 ರಲ್ಲಿ 5 ಮುಗಿದಿದೆ", "12 இல் 5 முடிந்தது"),
                subText = s.t("7 items left", "7 ವಸ್ತುಗಳು ಬಾಕಿ", "7 பொருட்கள் மீதம்"),
                percentText = "42%",
                progress = 5f / 12f,
                hideCompleted = false,
                onHideCompletedChange = {},
                hideCompletedLabel = s.t("Hide completed items", "ಮುಗಿದ ವಸ್ತುಗಳನ್ನು ಮರೆಮಾಡಿ", "முடிந்த பொருட்களை மறை"),
            )
            ProgressCard(
                countText = s.t("0 of 0 done", "0 ರಲ್ಲಿ 0 ಮುಗಿದಿದೆ", "0 இல் 0 முடிந்தது"),
                subText = s.t(
                    "Add items to start ticking them off.",
                    "ವಸ್ತುಗಳನ್ನು ಸೇರಿಸಿ ಮತ್ತು ಟಿಕ್ ಮಾಡಲು ಪ್ರಾರಂಭಿಸಿ.",
                    "பொருட்களைச் சேர்த்து டிக் செய்யத் தொடங்குங்கள்.",
                ),
                progress = 0f,
            )
        }
    }

    @Test
    fun categoryHeader() = shoot("category_header") { s ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryHeader(
                emoji = "🛒",
                name = s.t("Groceries", "ದಿನಸಿ", "மளிகைப் பொருட்கள்"),
                progressText = s.t("2 of 4 done", "4 ರಲ್ಲಿ 2 ಮುಗಿದಿದೆ", "4 இல் 2 முடிந்தது"),
                moveUpLabel = s.t("Move up", "ಮೇಲಕ್ಕೆ ಸರಿಸಿ", "மேலே நகர்த்து"),
                moveDownLabel = s.t("Move down", "ಕೆಳಕ್ಕೆ ಸರಿಸಿ", "கீழே நகர்த்து"),
                onMoveDown = {},
                menu = { cardMenu(s) },
            )
            CategoryHeader(
                emoji = "🎁",
                name = s.t("Gifts and sweets for relatives", "ಸಂಬಂಧಿಕರಿಗೆ ಉಡುಗೊರೆ ಮತ್ತು ಸಿಹಿ", "உறவினர்களுக்கான பரிசுகளும் இனிப்புகளும்"),
                progressText = s.t("0 of 3 done", "3 ರಲ್ಲಿ 0 ಮುಗಿದಿದೆ", "3 இல் 0 முடிந்தது"),
                moveUpLabel = s.t("Move up", "ಮೇಲಕ್ಕೆ ಸರಿಸಿ", "மேலே நகர்த்து"),
                moveDownLabel = s.t("Move down", "ಕೆಳಕ್ಕೆ ಸರಿಸಿ", "கீழே நகர்த்து"),
                onMoveUp = {},
                menu = { cardMenu(s) },
            )
        }
    }

    @Test
    fun itemRow() = shoot("item_row") { s ->
        val completed = s.t("Completed", "ಪೂರ್ಣಗೊಂಡಿದೆ", "முடிந்தது")
        Column {
            ItemRow(
                name = s.t("Rice", "ಅಕ್ಕಿ", "அரிசி"),
                checked = false,
                onCheckedChange = {},
                unitText = s.t("5 KG", "5 ಕೆಜಿ", "5 கிலோ"),
                completedLabel = completed,
                menu = { cardMenu(s) },
            )
            ItemRow(
                name = s.t("Sugar", "ಸಕ್ಕರೆ", "சர்க்கரை"),
                checked = true,
                onCheckedChange = {},
                unitText = s.t("2 KG", "2 ಕೆಜಿ", "2 கிலோ"),
                completedLabel = completed,
                menu = { cardMenu(s) },
            )
            ItemRow(
                name = s.t("Dry fruits", "ಒಣ ಹಣ್ಣುಗಳು", "உலர் பழங்கள்"),
                checked = false,
                onCheckedChange = {},
                unitText = s.t("2 KG", "2 ಕೆಜಿ", "2 கிலோ"),
                note = s.t("For guests", "ಅತಿಥಿಗಳಿಗಾಗಿ", "விருந்தினர்களுக்கு"),
                completedLabel = completed,
                // The slot the item-photos track fills; a plain box stands in for the thumbnail.
                trailingThumbnail = {
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    )
                },
                menu = { cardMenu(s) },
            )
        }
    }

    @Test
    fun dashedAddButton() = shoot("dashed_add_button") { s ->
        DashedAddButton(s.t("Add item to Groceries", "ದಿನಸಿಗೆ ವಸ್ತು ಸೇರಿಸಿ", "மளிகையில் பொருளைச் சேர்"), onClick = {})
    }

    @Test
    fun selectableRow() = shoot("selectable_row") { s ->
        Column {
            SelectableRow(s.t("Groceries", "ದಿನಸಿ", "மளிகைப் பொருட்கள்"), checked = true, onCheckedChange = {}, lead = "🛒")
            SelectableRow(
                s.t("Rice", "ಅಕ್ಕಿ", "அரிசி"),
                checked = true,
                onCheckedChange = {},
                unitText = s.t("KG", "ಕೆಜಿ", "கிலோ"),
            )
            SelectableRow(
                s.t("Dry fruits", "ಒಣ ಹಣ್ಣುಗಳು", "உலர் பழங்கள்"),
                checked = false,
                onCheckedChange = {},
                supporting = s.t("Your item", "ನಿಮ್ಮ ವಸ್ತು", "உங்கள் பொருள்"),
                unitText = s.t("KG", "ಕೆಜಿ", "கிலோ"),
            )
            SelectableRow(
                s.t("Rava (semolina)", "ರವೆ", "ரவை"),
                checked = false,
                onCheckedChange = {},
                supporting = s.t("Already added", "ಈಗಾಗಲೇ ಸೇರಿಸಲಾಗಿದೆ", "ஏற்கனவே சேர்க்கப்பட்டது"),
                unitText = s.t("KG", "ಕೆಜಿ", "கிலோ"),
                enabled = false,
            )
        }
    }

    @Test
    fun settingsRows() = shoot("settings_rows") { s ->
        Column {
            SectionHeader(s.t("General", "ಸಾಮಾನ್ಯ", "பொது"))
            SettingsRow(
                s.t("Language", "ಭಾಷೆ", "மொழி"),
                icon = painterResource(R.drawable.ic_translate),
                value = s.t("System default", "ಸಿಸ್ಟಂ ಡೀಫಾಲ್ಟ್", "கணினி இயல்பு"),
                onClick = {},
            )
            SettingsRow(
                s.t("Text size", "ಅಕ್ಷರದ ಗಾತ್ರ", "எழுத்து அளவு"),
                icon = painterResource(R.drawable.ic_format_size),
                value = s.t("Large", "ದೊಡ್ಡದು", "பெரியது"),
                onClick = {},
            )
            SettingsSwitchRow(
                s.t("High contrast", "ಹೆಚ್ಚಿನ ಕಾಂಟ್ರಾಸ್ಟ್", "அதிக மாறுபாடு"),
                checked = false,
                onCheckedChange = {},
                icon = painterResource(R.drawable.ic_contrast),
            )
            SettingsSwitchRow(
                s.t("AI features", "AI ವೈಶಿಷ್ಟ್ಯಗಳು", "AI அம்சங்கள்"),
                checked = true,
                onCheckedChange = {},
                icon = painterResource(R.drawable.ic_auto_awesome),
                subtitle = s.t("Off by default.", "ಡೀಫಾಲ್ಟ್ ಆಗಿ ಆಫ್ ಆಗಿದೆ.", "இயல்பாக அணைக்கப்பட்டுள்ளது."),
            )
            RadioRow(s.t("English", "ಇಂಗ್ಲಿಷ್", "ஆங்கிலம்"), selected = true, onSelect = {}, subtitle = "English")
            RadioRow("ಕನ್ನಡ", selected = false, onSelect = {}, subtitle = "Kannada")
            OptionCard(
                s.t("Backup file (.json)", "ಬ್ಯಾಕಪ್ ಫೈಲ್ (.json)", "காப்புக் கோப்பு (.json)"),
                selected = true,
                onSelect = {},
                subtitle = s.t("Move to another phone", "ಇನ್ನೊಂದು ಫೋನ್‌ಗೆ ಸರಿಸಿ", "மற்றொரு தொலைபேசிக்கு மாற்று"),
                icon = painterResource(R.drawable.ic_data_object),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    @Test
    fun buttons() = shoot("buttons") { s ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(
                s.t("Create checklist", "ಪಟ್ಟಿ ರಚಿಸಿ", "பட்டியலை உருவாக்கு"),
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = painterResource(R.drawable.ic_add),
            )
            OutlinedActionButton(
                s.t("Create with AI instead", "ಬದಲಿಗೆ AI ಬಳಸಿ ರಚಿಸಿ", "அதற்குப் பதிலாக AI மூலம் உருவாக்கு"),
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = painterResource(R.drawable.ic_auto_awesome),
            )
            TextActionButton(s.t("Skip for now", "ಈಗ ಬೇಡ", "இப்போது தவிர்"), onClick = {})
            DangerButton(s.t("Delete", "ಅಳಿಸಿ", "நீக்கு"), onClick = {}, compact = true)
            PrimaryButton(s.t("Create", "ರಚಿಸಿ", "உருவாக்கு"), onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
        }
    }

    @Test
    fun bottomActionBar() = shoot("bottom_action_bar") { s ->
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BottomActionBar {
                PrimaryButton(s.t("Create", "ರಚಿಸಿ", "உருவாக்கு"), onClick = {}, modifier = Modifier.fillMaxWidth())
                OutlinedActionButton(
                    s.t("Create with AI instead", "ಬದಲಿಗೆ AI ಬಳಸಿ ರಚಿಸಿ", "அதற்குப் பதிலாக AI மூலம் உருவாக்கு"),
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            BottomActionBarRow {
                OutlinedActionButton(s.t("Cancel", "ರದ್ದುಮಾಡಿ", "ரத்து செய்"), onClick = {}, modifier = Modifier.weight(1f))
                PrimaryButton(s.t("Import", "ಆಮದು ಮಾಡಿ", "இறக்குமதி"), onClick = {}, modifier = Modifier.weight(1f))
            }
        }
    }

    @Test
    fun quantityStepper() = shoot("quantity_stepper") { s ->
        val quantity = s.t("Quantity", "ಪ್ರಮಾಣ", "அளவு")
        val less = s.t("Less", "ಕಡಿಮೆ", "குறை")
        val more = s.t("More", "ಹೆಚ್ಚು", "கூட்டு")
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            QuantityStepper("5", {}, {}, {}, quantity, less, more, modifier = Modifier.fillMaxWidth())
            QuantityStepper("2", {}, {}, {}, quantity, less, more, compact = true, modifier = Modifier.fillMaxWidth())
            QuantityStepper(
                "1.5",
                {},
                {},
                {},
                quantity,
                less,
                more,
                compact = true,
                isError = true,
                errorText = s.t("Pieces must be a whole number", "ತುಣುಕುಗಳು ಪೂರ್ಣ ಸಂಖ್ಯೆಯಾಗಿರಬೇಕು", "எண்ணிக்கை முழு எண்ணாக இருக்க வேண்டும்"),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    @Test
    fun chips() = shoot("chips") { s ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChipFlow {
                SelectableChip(s.t("Shopping", "ಶಾಪಿಂಗ್", "ஷாப்பிங்"), selected = true, onClick = {}, leadingEmoji = "🛒")
                SelectableChip(s.t("Travel", "ಪ್ರಯಾಣ", "பயணம்"), selected = false, onClick = {}, leadingEmoji = "✈️")
                SelectableChip(s.t("Exam", "ಪರೀಕ್ಷೆ", "தேர்வு"), selected = false, onClick = {})
                SelectableChip(
                    s.t("Hospital visit", "ಆಸ್ಪತ್ರೆ ಭೇಟಿ", "மருத்துவமனை வருகை"),
                    selected = false,
                    onClick = {},
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UnitChip(s.t("5 KG", "5 ಕೆಜಿ", "5 கிலோ"))
                UnitChip(s.t("2 KG", "2 ಕೆಜಿ", "2 கிலோ"), done = true)
            }
            SegmentedChoice(
                listOf(s.t("Active", "ಸಕ್ರಿಯ", "செயலில்"), s.t("Archived", "ಆರ್ಕೈವ್ ಮಾಡಲಾಗಿದೆ", "காப்பகம்")),
                selectedIndex = 0,
                onSelect = {},
            )
        }
    }

    @Test
    fun emptyState() = shoot("empty_state") { s ->
        EmptyState(
            emoji = "📝",
            title = s.t("No checklists yet", "ಇನ್ನೂ ಯಾವುದೇ ಪಟ್ಟಿಗಳಿಲ್ಲ", "இன்னும் பட்டியல்கள் இல்லை"),
            body = s.t(
                "Create your first checklist for shopping, travel, exams or anything else.",
                "ಶಾಪಿಂಗ್, ಪ್ರಯಾಣ, ಪರೀಕ್ಷೆ ಅಥವಾ ಬೇರೆ ಯಾವುದಕ್ಕೂ ನಿಮ್ಮ ಮೊದಲ ಪಟ್ಟಿಯನ್ನು ರಚಿಸಿ.",
                "ஷாப்பிங், பயணம், தேர்வு அல்லது வேறு எதற்கும் உங்கள் முதல் பட்டியலை உருவாக்குங்கள்.",
            ),
        ) {
            PrimaryButton(s.t("Create checklist", "ಪಟ್ಟಿ ರಚಿಸಿ", "பட்டியலை உருவாக்கு"), onClick = {}, modifier = Modifier.fillMaxWidth())
            OutlinedActionButton(s.t("Create with AI", "AI ಬಳಸಿ ರಚಿಸಿ", "AI மூலம் உருவாக்கு"), onClick = {}, modifier = Modifier.fillMaxWidth())
        }
    }

    @Test
    fun banner() = shoot("banner") { s ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Banner(
                s.t(
                    "Saved only on this phone, locked with encryption.",
                    "ಈ ಫೋನ್‌ನಲ್ಲಿ ಮಾತ್ರ ಉಳಿಸಲಾಗಿದೆ, ಎನ್‌ಕ್ರಿಪ್ಶನ್‌ನಿಂದ ಲಾಕ್ ಆಗಿದೆ.",
                    "இந்த தொலைபேசியில் மட்டும் சேமிக்கப்படும், குறியாக்கத்துடன் பூட்டப்பட்டுள்ளது.",
                ),
                painterResource(R.drawable.ic_lock),
            )
            Banner(
                s.t(
                    "Your profile could not be read and was reset.",
                    "ನಿಮ್ಮ ಪ್ರೊಫೈಲ್ ಓದಲಾಗಲಿಲ್ಲ ಮತ್ತು ಮರುಹೊಂದಿಸಲಾಗಿದೆ.",
                    "உங்கள் சுயவிவரத்தைப் படிக்க முடியவில்லை, மீட்டமைக்கப்பட்டது.",
                ),
                painterResource(R.drawable.ic_warning),
                variant = BannerVariant.Error,
            )
        }
    }

    @Test
    fun dialog() = shoot("dialog") { s ->
        ConfirmDialogContent(
            title = s.t("Delete 'Goa Trip'?", "'ಗೋವಾ ಪ್ರವಾಸ' ಅಳಿಸಬೇಕೆ?", "'கோவா பயணம்' நீக்கவா?"),
            message = s.t(
                "This removes the checklist and all its 24 items. You can't undo this.",
                "ಇದು ಪಟ್ಟಿಯನ್ನು ಮತ್ತು ಅದರ ಎಲ್ಲಾ 24 ವಸ್ತುಗಳನ್ನು ತೆಗೆದುಹಾಕುತ್ತದೆ. ಇದನ್ನು ಹಿಂತಿರುಗಿಸಲು ಸಾಧ್ಯವಿಲ್ಲ.",
                "இது பட்டியலையும் அதன் 24 பொருட்களையும் நீக்கும். இதை மீட்க முடியாது.",
            ),
            confirmLabel = s.t("Delete", "ಅಳಿಸಿ", "நீக்கு"),
            onConfirm = {},
            onDismiss = {},
            destructive = true,
            cancelLabel = s.t("Cancel", "ರದ್ದುಮಾಡಿ", "ரத்து செய்"),
        )
    }

    @Test
    fun sheet() = shoot("sheet") { s ->
        Column {
            SheetHandle(Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally))
            SheetBody(s.t("Share as PDF", "PDF ಆಗಿ ಹಂಚಿಕೊಳ್ಳಿ", "PDF ஆகப் பகிர்")) {
                SettingsSwitchRow(
                    s.t("Include completed items", "ಪೂರ್ಣಗೊಂಡ ವಸ್ತುಗಳನ್ನು ಸೇರಿಸಿ", "முடிந்த பொருட்களைச் சேர்"),
                    checked = true,
                    onCheckedChange = {},
                    icon = painterResource(R.drawable.ic_task_alt),
                )
                PrimaryButton(
                    s.t("Share", "ಹಂಚಿಕೊಳ್ಳಿ", "பகிர்"),
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = painterResource(R.drawable.ic_share),
                )
            }
        }
    }

    @Test
    fun snackbar() = shoot("snackbar") { s ->
        AppSnackbar(
            FakeSnackbarData(
                s.t("'Tomato' deleted", "'ಟೊಮೆಟೊ' ಅಳಿಸಲಾಗಿದೆ", "'தக்காளி' நீக்கப்பட்டது"),
                s.t("Undo", "ರದ್ದುಗೊಳಿಸಿ", "செயல்தவிர்"),
            ),
        )
    }

    @Test
    fun aiLabel() = shoot("ai_label") { s ->
        AiLabel(s.t("Suggested by AI", "AI ಸೂಚಿಸಿದೆ", "AI பரிந்துரைத்தது"))
    }

    @Test
    fun formFields() = shoot("form_fields") { s ->
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FormField(
                label = s.t("Checklist name *", "ಪಟ್ಟಿಯ ಹೆಸರು *", "பட்டியலின் பெயர் *"),
                value = s.t("Diwali Shopping", "ದೀಪಾವಳಿ ಶಾಪಿಂಗ್", "தீபாவளி ஷாப்பிங்"),
                onValueChange = {},
                helperText = s.t("For example: Diwali Shopping", "ಉದಾಹರಣೆ: ದೀಪಾವಳಿ ಶಾಪಿಂಗ್", "எ.கா: தீபாவளி ஷாப்பிங்"),
            )
            FormField(
                label = s.t("Checklist name *", "ಪಟ್ಟಿಯ ಹೆಸರು *", "பட்டியலின் பெயர் *"),
                value = "",
                onValueChange = {},
                errorText = s.t("Please enter a name", "ದಯವಿಟ್ಟು ಹೆಸರನ್ನು ನಮೂದಿಸಿ", "பெயரை உள்ளிடவும்"),
            )
            FormField(
                label = s.t("Description (optional)", "ವಿವರಣೆ (ಐಚ್ಛಿಕ)", "விளக்கம் (விருப்பம்)"),
                value = "",
                onValueChange = {},
                placeholder = s.t("Who is it for?", "ಇದು ಯಾರಿಗಾಗಿ?", "இது யாருக்காக?"),
                multiLine = true,
            )
        }
    }

    @Test
    fun profileFields() = shoot("profile_fields") { s ->
        ProfileFields(
            values = ProfileFieldValues(name = "Kamala Hiremath", phone = "+91 98450 12345", email = "kamala@example"),
            onValuesChange = {},
            labels = ProfileFieldLabels(
                name = s.t("Name", "ಹೆಸರು", "பெயர்"),
                phone = s.t("Phone", "ಫೋನ್", "தொலைபேசி"),
                email = s.t("Email", "ಇಮೇಲ್", "மின்னஞ்சல்"),
            ),
            errors = ProfileFieldErrors(
                email = s.t(
                    "Enter a valid email like name@example.com",
                    "name@example.com ನಂತಹ ಮಾನ್ಯ ಇಮೇಲ್ ನಮೂದಿಸಿ",
                    "name@example.com போன்ற சரியான மின்னஞ்சலை உள்ளிடவும்",
                ),
            ),
        )
    }

    @Test
    fun toggles() = shoot("toggles") { _ ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppCheckbox(checked = false, onCheckedChange = {})
            AppCheckbox(checked = true, onCheckedChange = {})
            AppSwitch(checked = false, onCheckedChange = {})
            AppSwitch(checked = true, onCheckedChange = {})
            AppRadio(selected = false, onClick = {})
            AppRadio(selected = true, onClick = {})
        }
    }

    @Test
    fun progressBar() = shoot("progress_bar") { _ ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppProgressBar(progress = 0f)
            AppProgressBar(progress = 0.42f)
            AppProgressBar(progress = 1f, height = 12.dp)
            Text("42%", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
