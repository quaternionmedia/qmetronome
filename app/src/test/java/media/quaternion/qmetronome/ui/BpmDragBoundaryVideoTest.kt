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

/** The animated-GIF counterpart to [BpmDragBoundaryScreenshotTest] - see [BpmDragVideoTest]'s
 * kdoc for why this is a separate test/file from the screenshot one and why it's wrapped in
 * [TouchIndicatorOverlay]. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = FULLSCREEN_QUALIFIERS)
class BpmDragBoundaryVideoTest {

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
    fun `record dragging left across the BPM-to-BPH boundary, then back`() {
        MetronomeEngine.setExtendedBpmRangeEnabled(true)
        MetronomeEngine.setBpm(3f)
        MetronomeEngine.markBpmHintShown()
        composeTestRule.setThemedContent {
            TouchIndicatorOverlay { MainScreen(onActivateToy = {}) }
        }

        val bpmNumber = composeTestRule.onNodeWithTag("bpm_number")
        composeTestRule.onScreenshotRoot().recordRoboVideo(
            composeRule = composeTestRule,
            filePath = videoPath("bpm-drag-scrub-boundary"),
            videoOptions = RoboVideoOptions(fps = 10),
        ) {
            // Fewer, larger-offset moves than the screenshot test's own drag distances - enough
            // frames to illustrate the motion without ballooning memory (each captured frame is a
            // full 1080x2400 bitmap; too many of them risks the JVM test heap's own OutOfMemoryError).
            dragWithIndicator(bpmNumber, steps = 8, stepOffset = Offset(-60f, 0f))
            delay(300)
            dragWithIndicator(bpmNumber, steps = 8, stepOffset = Offset(110f, 0f))
            delay(300)
        }
    }
}
