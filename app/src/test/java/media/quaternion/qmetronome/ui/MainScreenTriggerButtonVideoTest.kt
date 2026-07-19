package media.quaternion.qmetronome.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoboVideoOptions
import com.github.takahirom.roborazzi.recordRoboVideo
import media.quaternion.qmetronome.engine.MetronomeEngine
import media.quaternion.qmetronome.midi.MidiActionSender
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The animated-GIF counterpart to [MainScreenTriggerButtonScreenshotTest] - TAP's icon actually
 * swapping from "TAP" to the lightning bolt partway through latching HOLD is exactly the kind of
 * timing-dependent moment a single before/after screenshot pair can't convey - see
 * [BpmDragVideoTest]'s kdoc for why this is a separate test/file from the screenshot one and why
 * it's wrapped in [TouchIndicatorOverlay]. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = FULLSCREEN_QUALIFIERS)
class MainScreenTriggerButtonVideoTest {

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
    fun `record latching HOLD - TAP swaps to Trigger - then tapping it to fire`() {
        MidiActionSender.setEnabled(true)
        MetronomeEngine.setBeatsPerBar(4)
        MetronomeEngine.markBpmHintShown()
        composeTestRule.setThemedContent {
            TouchIndicatorOverlay { MainScreen(onActivateToy = {}) }
        }

        val holdButton = composeTestRule.onNodeWithTag("hold_button")
        val tapButton = composeTestRule.onNodeWithTag("tap_trigger_button")
        composeTestRule.onScreenshotRoot().recordRoboVideo(
            composeRule = composeTestRule,
            filePath = videoPath("trigger-button"),
            videoOptions = RoboVideoOptions(fps = 10),
        ) {
            // 800ms past HOLD's own 600ms long-press threshold (see HoldButtonScreenshotTest) -
            // several frames while held, capturing TAP's icon actually swapping mid-press once
            // HOLD promotes to Latched.
            pressAndHoldWithIndicator(holdButton, holdMs = 800)
            delay(300) // still latched - releasing the promoting press is a no-op

            // A separate, later tap on TAP itself - now the Trigger icon - fires it without
            // dropping the latch or starting playback.
            tapWithIndicator(tapButton)
            delay(400)
        }
    }
}
