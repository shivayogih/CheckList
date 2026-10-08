package com.dataloom.checklist.testing.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import com.dataloom.checklist.R
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Proves the screenshot pipeline end to end on the CI image: Compose renders under Robolectric's
 * native graphics, the variants switch theme, font scale and language, and Roborazzi writes the PNGs.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotSetupTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `every standard variant writes a non-empty png`() {
        compose.captureAll("setup-smoke") {
            Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium)
        }

        ScreenshotVariant.STANDARD.forEach { variant ->
            val file = File("$SCREENSHOT_DIR/setup-smoke-${variant.name}.png")
            assertTrue("${file.path} was not written", file.isFile && file.length() > 0)
        }
    }
}
