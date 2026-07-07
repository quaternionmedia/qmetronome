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

/**
 * The animated-GIF counterpart to [HoldButtonScreenshotTest]'s momentary-staging topic - see
 * [BpmDragVideoTest]'s kdoc for why this is a separate test/file from the screenshot one and why
 * it's wrapped in [TouchIndicatorOverlay].
 *
 * [MetronomeEngine.beginHold]/[MetronomeEngine.endHold] are called directly rather than through a
 * real press-and-hold, for the exact reason `HoldButtonScreenshotTest`'s own momentary test does
 * the same (see its kdoc): HOLD's `LaunchedEffect(isPressed)` races its own 600ms promotion delay
 * the instant any test synchronization point runs, which `recordRoboVideo`'s own `delay()` calls
 * are. [TouchIndicator] is still shown fixed at the button's position for the whole staged window
 * though, since the *visual* result (color change, "staged" labels) is driven by
 * [MetronomeEngine.holdMode] regardless of how it got there - a direct call renders identically
 * to a real held press, just without racing that timer.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = FULLSCREEN_QUALIFIERS)
class HoldMomentaryVideoTest {

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
    fun `record holding HOLD to stage a tempo change, releasing to commit it`() {
        MetronomeEngine.setBpm(120f)
        MetronomeEngine.markBpmHintShown()
        composeTestRule.setThemedContent {
            TouchIndicatorOverlay { MainScreen(onActivateToy = {}) }
        }

        val holdButton = composeTestRule.onNodeWithTag("hold_button")
        composeTestRule.onScreenshotRoot().recordRoboVideo(
            composeRule = composeTestRule,
            filePath = videoPath("hold-momentary-staging"),
            videoOptions = RoboVideoOptions(fps = 10),
        ) {
            TouchIndicator.at(holdButton.fetchSemanticsNode().boundsInRoot.center)
            MetronomeEngine.beginHold()
            delay(200)
            MetronomeEngine.setBpm(140f)
            delay(300)
            MetronomeEngine.endHold()
            TouchIndicator.clear()
            delay(300)
        }
    }
}
