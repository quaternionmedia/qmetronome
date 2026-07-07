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

/** The animated-GIF counterpart to [PreviewGestureScreenshotTest]'s double-tap topic - see
 * [BpmDragVideoTest]'s kdoc for why this is a separate test/file from the screenshot one and why
 * it's wrapped in [TouchIndicatorOverlay]. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = FULLSCREEN_QUALIFIERS)
class PreviewDoubleTapVideoTest {

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
    fun `record double-tapping the preview to start playback`() {
        MetronomeEngine.markBpmHintShown()
        composeTestRule.setThemedContent {
            TouchIndicatorOverlay { MainScreen(onActivateToy = {}) }
        }

        val preview = composeTestRule.onNodeWithTag("matrix_preview")
        composeTestRule.onScreenshotRoot().recordRoboVideo(
            composeRule = composeTestRule,
            filePath = videoPath("preview-double-tap-play"),
            // settleTimeoutMillis = 0: once the double-tap actually starts playback, the engine's
            // own real-time beat-scheduling coroutines (not gated by this recorder's paused
            // compose clock) keep firing for as long as recording continues - the default 3s
            // "settle" window after this block ends left them running long enough to exhaust the
            // JVM test heap capturing that continuous real-time animation. Cutting it to 0 stops
            // recording the instant this block returns instead.
            videoOptions = RoboVideoOptions(fps = 10, settleTimeoutMillis = 0),
        ) {
            delay(200)
            doubleTapWithIndicator(preview)
            delay(200)
        }
    }
}
