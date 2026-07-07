package media.quaternion.qmetronome.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoboVideoOptions
import com.github.takahirom.roborazzi.recordRoboVideo
import media.quaternion.qmetronome.engine.MetronomeEngine
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The animated-GIF counterpart to [SettingsJumpToUnitScreenshotTest] - see [BpmDragVideoTest]'s
 * kdoc for why this is a separate test/file from the screenshot one and why it's wrapped in
 * [TouchIndicatorOverlay]. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = FULLSCREEN_QUALIFIERS)
class SettingsJumpToUnitVideoTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        MetronomeEngine.resetForTesting()
        MetronomeEngine.attach(RuntimeEnvironment.getApplication())
        TouchIndicator.clear()
    }

    @After
    fun tearDown() {
        MetronomeEngine.resetForTesting()
        TouchIndicator.clear()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun `record expanding Tempo and Bars, then tapping unit chips`() {
        MetronomeEngine.setBpm(120f)
        composeTestRule.setThemedContent {
            TouchIndicatorOverlay { SettingsSheet(onDismiss = {}, onActivateToy = {}) }
        }

        val header = composeTestRule.onNodeWithTag("section_header_Tempo_and_Bars")
        composeTestRule.onScreenshotRoot().recordRoboVideo(
            composeRule = composeTestRule,
            filePath = videoPath("settings-jump-to-unit"),
            videoOptions = RoboVideoOptions(fps = 10),
        ) {
            // Same real-touch expand click SettingsJumpToUnitScreenshotTest uses (this section's
            // own summary is plain text, no competing action to worry about).
            TouchIndicator.at(header.fetchSemanticsNode().boundsInRoot.topLeft)
            header.performTouchInput { click(Offset(1f, 1f)) }
            delay(200)
            TouchIndicator.clear()

            val bph = composeTestRule.onNodeWithTag("jump_to_unit_BPH")
            invokeClickWithIndicator(bph)
            delay(300)

            val bps = composeTestRule.onNodeWithTag("jump_to_unit_BPS")
            invokeClickWithIndicator(bps)
            delay(300)

            val bpm = composeTestRule.onNodeWithTag("jump_to_unit_BPM")
            invokeClickWithIndicator(bpm)
            delay(300)
        }
    }
}
