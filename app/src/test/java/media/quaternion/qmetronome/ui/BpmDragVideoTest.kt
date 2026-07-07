package media.quaternion.qmetronome.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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

/**
 * The animated-GIF counterpart to [BpmDragScreenshotTest] - a single static frame loses the
 * "continuously scrubs, doesn't jump" quality of this gesture, which is exactly the property a
 * manual bug report once disputed (see [BpmDragScreenshotTest]'s own kdoc). Deliberately a
 * separate test/file from the screenshot one - `recordRoboVideo`'s own frame-by-frame clock
 * control (see [RoboVideoRecorderScope.delay]) doesn't compose with a plain `captureRoboImage`
 * call in the same test. Wrapped in [TouchIndicatorOverlay] so the recording shows *where* the
 * simulated finger is, not just the resulting number change.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = FULLSCREEN_QUALIFIERS)
class BpmDragVideoTest {

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
    fun `record dragging the BPM number right then left`() {
        MetronomeEngine.setBpm(120f)
        MetronomeEngine.markBpmHintShown()
        composeTestRule.setThemedContent {
            TouchIndicatorOverlay { MainScreen(onActivateToy = {}) }
        }

        val bpmNumber = composeTestRule.onNodeWithTag("bpm_number")
        composeTestRule.onScreenshotRoot().recordRoboVideo(
            composeRule = composeTestRule,
            filePath = videoPath("bpm-drag-scrub"),
            videoOptions = RoboVideoOptions(fps = 10),
        ) {
            dragWithIndicator(bpmNumber, steps = 8, stepOffset = Offset(30f, 0f))
            delay(200)
            dragWithIndicator(bpmNumber, steps = 8, stepOffset = Offset(-30f, 0f))
            delay(200)
        }
    }
}
