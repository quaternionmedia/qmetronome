package media.quaternion.qmetronome.ui

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

/** The animated-GIF counterpart to [TapTempoScreenshotTest] - see [BpmDragVideoTest]'s kdoc for
 * why this is a separate test/file from the screenshot one and why it's wrapped in
 * [TouchIndicatorOverlay]. Uses a real (short) [Thread.sleep] between the two taps, same as the
 * screenshot test - [MetronomeEngine.tapTempo] reads real wall-clock time, not the paused compose
 * clock [RoboVideoRecorderScope.delay] advances, so only a genuine sleep gives it a predictable
 * interval to derive a bpm from. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = FULLSCREEN_QUALIFIERS)
class TapTempoVideoTest {

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
    fun `record tapping out a tempo`() {
        MetronomeEngine.setBpm(200f)
        MetronomeEngine.markBpmHintShown()
        composeTestRule.setThemedContent {
            TouchIndicatorOverlay { MainScreen(onActivateToy = {}) }
        }

        val bpmNumber = composeTestRule.onNodeWithTag("bpm_number")
        composeTestRule.onScreenshotRoot().recordRoboVideo(
            composeRule = composeTestRule,
            filePath = videoPath("tap-tempo"),
            videoOptions = RoboVideoOptions(fps = 10),
        ) {
            tapWithIndicator(bpmNumber)
            delay(100)
            Thread.sleep(500) // real time - see this test's own kdoc
            tapWithIndicator(bpmNumber)
            delay(300)
        }
    }
}
