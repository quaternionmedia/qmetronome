package media.quaternion.qmetronome.ui

import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoboVideoOptions
import com.github.takahirom.roborazzi.recordRoboVideo
import media.quaternion.qmetronome.ui.theme.QMetronomeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The animated-GIF counterpart to [BpmUnitEntryDialogScreenshotTest] - showing the field's value
 * actually swap (120 -> 30) when switching units is exactly the kind of moment a static
 * before/after pair loses. No [TouchIndicatorOverlay] here (unlike every other `*VideoTest`) - see
 * [BpmUnitEntryDialogScreenshotTest]'s own kdoc for why this dialog is rendered in isolation
 * rather than via a real gesture on the full [MainScreen]: the same reasoning means an overlay
 * drawn in this test's outer composition wouldn't appear in the dialog's own separate window
 * anyway, so it's skipped rather than built for no visible effect.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class BpmUnitEntryDialogVideoTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun `record switching units in the BPM entry dialog`() {
        composeTestRule.setContent {
            QMetronomeTheme {
                BpmUnitEntryDialog(
                    initialBpm = 120f,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNode(isDialog()).recordRoboVideo(
            composeRule = composeTestRule,
            filePath = videoPath("bpm-unit-entry-dialog"),
            videoOptions = RoboVideoOptions(fps = 10),
        ) {
            delay(200)
            composeTestRule.onNodeWithText("BPH").performClick()
            delay(300)
            composeTestRule.onNodeWithText("Set").performClick()
            delay(200)
        }
    }
}
