package com.dataloom.checklist.presentation.photos

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.presentation.checklist.detail.ItemRow
import com.dataloom.checklist.testing.screenshot.Sample
import com.dataloom.checklist.testing.screenshot.ScreenshotVariant
import com.dataloom.checklist.testing.screenshot.assertNoTextOverflow
import com.dataloom.checklist.testing.screenshot.assertTouchTargetsAtLeast48
import com.dataloom.checklist.testing.screenshot.captureScene
import com.dataloom.checklist.testing.screenshot.setScreenshotContent
import com.dataloom.checklist.testing.ui.PhotoFixtures
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the photo features (CL-216) with the shared harness: the item row, the form section and the
 * viewer in every variant (light, dark, 200 % font, the seven languages), with the layout assertions (no
 * clipped text, no tap target under 48 dp). Goldens are recorded by the "Record screenshots" CI step.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A tall window on purpose, as in ComponentScreenshotTest: overflow below the edge of a phone window must not hide.
@Config(sdk = [34], qualifiers = "w412dp-h4000dp-hdpi", application = Application::class)
class ItemPhotosScreenshotTest(private val variant: ScreenshotVariant) {

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = ScreenshotVariant.entries.map { arrayOf(it) }
    }

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val temp = TemporaryFolder()

    private fun shoot(name: String, content: @Composable (Sample) -> Unit) {
        compose.setScreenshotContent(variant, content)
        compose.assertNoTextOverflow()
        compose.assertTouchTargetsAtLeast48()
        compose.captureScene(name, variant)
    }

    @Test
    fun itemRows() {
        val one = PhotoFixtures.photos(temp.newFolder(), 1)
        val three = PhotoFixtures.photos(temp.newFolder(), 3)
        shoot("photos_item_row") { s ->
            Column {
                ItemRow(PhotoFixtures.item(emptyList(), s.t("Salt", "ಉಪ್ಪು", "உப்பு")), {}, {}, {})
                HorizontalDivider()
                ItemRow(PhotoFixtures.item(one, s.t("Rice", "ಅಕ್ಕಿ", "அரிசி")), {}, {}, {})
                HorizontalDivider()
                ItemRow(PhotoFixtures.item(three, s.t("Sugar", "ಸಕ್ಕರೆ", "சர்க்கரை")), {}, {}, {})
            }
        }
    }

    @Test
    fun formSection() {
        val photos = PhotoFixtures.formPhotos(temp.newFolder(), 2)
        shoot("photos_form") {
            Column(Modifier.padding(16.dp)) {
                PhotosFormSection(
                    photos, processing = 1, message = null, onAdd = {}, onRemove = {}, onMove = { _, _ -> },
                )
            }
        }
    }

    @Test
    fun viewer() {
        val photos = PhotoFixtures.photos(temp.newFolder(), 3)
        shoot("photos_viewer") { s ->
            Column(Modifier.height(720.dp)) {
                PhotoViewerContent(
                    s.t("Rice", "ಅಕ್ಕಿ", "அரிசி"), photos, index = 0, onIndexChange = {}, onCaption = { _, _ -> },
                    onDelete = {}, onClose = {},
                )
            }
        }
    }
}
