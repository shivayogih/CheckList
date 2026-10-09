package com.dataloom.checklist.presentation.photos

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.dataloom.checklist.R
import com.dataloom.checklist.presentation.checklist.detail.ChecklistDetailAction
import com.dataloom.checklist.presentation.checklist.detail.ItemRow
import com.dataloom.checklist.presentation.theme.CheckListTheme
import com.dataloom.checklist.testing.ui.PhotoFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Compose UI tests of the photo parts: the item row, the form section and the viewer (CL-212, CL-216). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ItemPhotosUiTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val temp = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun viewPhotos(count: Int) =
        context.resources.getQuantityString(R.plurals.photos_view_cd, count, count, "Rice")

    // ---- Item row ----

    private fun showRow(photos: Int, onAction: (ChecklistDetailAction) -> Unit = {}, onView: () -> Unit = {}) {
        val item = PhotoFixtures.item(PhotoFixtures.photos(temp.newFolder(), photos))
        compose.setContent {
            CheckListTheme { ItemRow(item, onAction, onEdit = {}, onViewPhotos = onView) }
        }
    }

    @Test
    fun `an item without photos shows no photo slot and no view action`() {
        showRow(photos = 0)
        compose.onNodeWithContentDescription(viewPhotos(1)).assertDoesNotExist()
        compose.onNodeWithText("Rice").assertExists()
        val actions = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
            .fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
        assertTrue(actions.none { it == context.getString(R.string.photo_view_action) })
    }

    @Test
    fun `an item with one photo shows the thumbnail without a count badge`() {
        showRow(photos = 1)
        compose.onNodeWithContentDescription(viewPhotos(1)).assertExists()
        compose.onNodeWithText("+1", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `an item with three photos shows the first and a plus 2 badge, and tapping opens the viewer`() {
        var opened = 0
        showRow(photos = 3, onView = { opened++ })
        compose.onNodeWithText("+2", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription(viewPhotos(3)).performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `tapping the row still toggles completion and TalkBack gets a view photos action`() {
        val actions = ArrayList<ChecklistDetailAction>()
        showRow(photos = 2, onAction = { actions += it })
        compose.onNodeWithText("Rice").performClick()
        assertEquals(1, actions.size)
        assertTrue(actions.single() is ChecklistDetailAction.ToggleItem)

        val labels = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
            .fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
        assertTrue(labels.contains(context.getString(R.string.photo_view_action)))
    }

    // ---- Form section ----

    private fun showForm(
        photos: Int,
        processing: Int = 0,
        onRemove: (String) -> Unit = {},
        onMove: (String, Int) -> Unit = { _, _ -> },
        onAdd: () -> Unit = {},
    ) {
        val items = PhotoFixtures.formPhotos(temp.newFolder(), photos)
        compose.setContent {
            CheckListTheme {
                PhotosFormSection(
                    items, processing, message = null, onAdd = onAdd, onRemove = onRemove, onMove = onMove,
                )
            }
        }
    }

    @Test
    fun `the form counts the photos and offers Add photo while there is room`() {
        var added = 0
        showForm(photos = 2, onAdd = { added++ })
        compose.onNodeWithText(context.getString(R.string.photos_count, 2, 3)).assertExists()
        compose.onNodeWithText(context.getString(R.string.photos_add)).performClick()
        assertEquals(1, added)
    }

    @Test
    fun `a full form has no Add photo tile`() {
        showForm(photos = 3)
        compose.onNodeWithText(context.getString(R.string.photos_count, 3, 3)).assertExists()
        compose.onNodeWithText(context.getString(R.string.photos_add)).assertDoesNotExist()
    }

    @Test
    fun `every photo has visible remove and move buttons and the ends cannot move outwards`() {
        val removed = ArrayList<String>()
        val moves = ArrayList<Pair<String, Int>>()
        showForm(photos = 3, onRemove = { removed += it }, onMove = { key, delta -> moves += key to delta })

        compose.onNodeWithContentDescription(context.getString(R.string.photos_move_left_cd, 1)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(context.getString(R.string.photos_move_right_cd, 3)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(context.getString(R.string.photos_move_right_cd, 1)).assertIsEnabled()

        compose.onNodeWithContentDescription(context.getString(R.string.photos_move_right_cd, 1)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.photos_move_left_cd, 3)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.photos_remove_cd, 2)).performClick()

        assertEquals(listOf("photo-1" to 1, "photo-3" to -1), moves)
        assertEquals(listOf("photo-2"), removed)
    }

    @Test
    fun `a photo being processed shows a progress tile and still counts as taking a slot`() {
        showForm(photos = 1, processing = 2)
        compose.onNodeWithContentDescription(context.getString(R.string.photos_processing)).assertExists()
        compose.onNodeWithText(context.getString(R.string.photos_add)).assertDoesNotExist()
    }

    // ---- Viewer ----

    private fun showViewer(
        count: Int = 2,
        onDelete: (PhotoUi) -> Unit = {},
        onCaption: (PhotoUi, String) -> Unit = { _, _ -> },
    ) {
        val photos = PhotoFixtures.photos(temp.newFolder(), count)
        compose.setContent {
            CheckListTheme {
                PhotoViewerDialog(
                    "Rice", photos, startIndex = 0, onCaption = onCaption, onDelete = onDelete, onClose = {},
                )
            }
        }
    }

    @Test
    fun `the viewer steps between photos with visible previous and next buttons`() {
        showViewer(count = 2)
        compose.onNodeWithText(context.getString(R.string.photo_viewer_position, 1, 2)).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.photo_viewer_previous)).assertIsNotEnabled()

        compose.onNodeWithContentDescription(context.getString(R.string.photo_viewer_next)).performClick()

        compose.onNodeWithText(context.getString(R.string.photo_viewer_position, 2, 2)).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.photo_viewer_next)).assertIsNotEnabled()
    }

    @Test
    fun `deleting a photo asks first and deletes only after Delete`() {
        val deleted = ArrayList<PhotoUi>()
        showViewer(onDelete = { deleted += it })

        compose.onNodeWithText(context.getString(R.string.photo_viewer_delete)).performClick()
        compose.onNodeWithText(context.getString(R.string.photo_delete_title)).assertExists()
        assertTrue("Nothing is deleted before the answer", deleted.isEmpty())

        compose.onNodeWithText(context.getString(R.string.action_cancel)).performClick()
        compose.onNodeWithText(context.getString(R.string.photo_delete_title)).assertDoesNotExist()
        assertTrue("Cancel keeps the photo", deleted.isEmpty())

        compose.onNodeWithText(context.getString(R.string.photo_viewer_delete)).performClick()
        compose.onNodeWithText(context.getString(R.string.action_delete)).performClick()
        assertEquals(listOf("photo-1"), deleted.map { it.id.value })
    }

    @Test
    fun `the viewer shows the caption of the photo`() {
        showViewer(count = 1)
        compose.onNodeWithText("Front of the shop").assertExists()
    }
}
