package media.quaternion.qmetronome.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import media.quaternion.qmetronome.engine.MetronomeEngine
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Proof-of-concept for a tests-as-source-of-truth pipeline: one real production composable
 * (`BpmControls`), driven through an actual gesture (not a direct `MetronomeEngine.setBpm()`
 * call), with both a genuine behavioral assertion and a Roborazzi screenshot capture in the same
 * test. Once this passes and produces a real image under `docs/images/generated/screenshots/`,
 * the rest of the planned UI test suite follows the same shape.
 *
 * Also the first automated coverage of the BPM drag-direction question a manual bug report
 * couldn't resolve a few rounds back - see `MainScreenFormattingTest`'s `steppedBpm` tests for
 * the pure-math half of that same investigation; this is the actual-gesture half.
 *
 * Renders the real, full [MainScreen] (not just [BpmControls] in isolation) at
 * [FULLSCREEN_QUALIFIERS] - every topic's screenshot shows the actual app the way it'd look on a
 * real device, not a cropped close-up of just the one control under test.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = FULLSCREEN_QUALIFIERS)
class BpmDragScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        MetronomeEngine.resetForTesting()
        MetronomeEngine.attach(RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() {
        MetronomeEngine.resetForTesting()
    }

    @Test
    fun `dragging the BPM number right increases tempo, left decreases it`() {
        MetronomeEngine.setBpm(120f)
        MetronomeEngine.markBpmHintShown() // suppress the one-time hint for a clean capture
        composeTestRule.setThemedContent {
            MainScreen(onActivateToy = {})
        }

        composeTestRule.onScreenshotRoot().captureRoboImage(screenshotPath("bpm-drag-scrub"))

        // Hand-rolled drag (down, N small incremental moves, up) rather than the swipeRight()/
        // swipeLeft() convenience helpers - this composable stacks two pointerInput blocks (tap +
        // drag) on the same node, and explicit small steps give detectHorizontalDragGestures a
        // clean, unambiguous sequence of drag deltas to recognize, matching how a real finger
        // actually generates many small moves rather than one large jump.
        val bpmNumber = composeTestRule.onNodeWithTag("bpm_number")
        bpmNumber.performTouchInput {
            down(center)
            repeat(10) {
                moveBy(Offset(10f, 0f))
                advanceEventTime(16)
            }
            up()
        }
        composeTestRule.waitForIdle()
        val afterRight = MetronomeEngine.state.value.bpm
        assertTrue("expected a rightward drag to increase bpm from 120, got $afterRight", afterRight > 120f)

        bpmNumber.performTouchInput {
            down(center)
            repeat(10) {
                moveBy(Offset(-10f, 0f))
                advanceEventTime(16)
            }
            up()
        }
        composeTestRule.waitForIdle()
        val afterLeft = MetronomeEngine.state.value.bpm
        assertTrue("expected a leftward drag to decrease bpm from $afterRight, got $afterLeft", afterLeft < afterRight)
    }
}
