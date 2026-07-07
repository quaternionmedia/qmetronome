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

/** The animated-GIF counterpart to [HoldButtonScreenshotTest]'s sticky-latch topic - showing
 * HOLD actually turn red/latched partway through a real held-down press is exactly the kind of
 * timing-dependent moment a single before/after screenshot pair can't convey. See
 * [BpmDragVideoTest]'s kdoc for why this is a separate test/file from the screenshot one and why
 * it's wrapped in [TouchIndicatorOverlay]. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = FULLSCREEN_QUALIFIERS)
class HoldLatchVideoTest {

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
    fun `record holding past the long-press threshold to latch, then a later tap to unlatch`() {
        MetronomeEngine.setBpm(120f)
        MetronomeEngine.markBpmHintShown()
        composeTestRule.setThemedContent {
            TouchIndicatorOverlay { MainScreen(onActivateToy = {}) }
        }

        val holdButton = composeTestRule.onNodeWithTag("hold_button")
        composeTestRule.onScreenshotRoot().recordRoboVideo(
            composeRule = composeTestRule,
            filePath = videoPath("hold-sticky-latch"),
            videoOptions = RoboVideoOptions(fps = 10),
        ) {
            // 800ms past LATCH_LONG_PRESS_MS (600ms) - several frames while held, capturing the
            // moment it actually turns red/latched partway through.
            pressAndHoldWithIndicator(holdButton, holdMs = 800)
            delay(300) // still latched - releasing the promoting press is a no-op
            tapWithIndicator(holdButton) // a separate, later tap unlatches and flushes
            delay(300)
        }
    }
}
