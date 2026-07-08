package media.quaternion.qmetronome.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
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

/** The animated-GIF counterpart to [BarQueueScreenshotTest]'s queue-building topic - see
 * [BpmDragVideoTest]'s kdoc for why this is a separate test/file from the screenshot one and why
 * it's wrapped in [TouchIndicatorOverlay]. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = FULLSCREEN_QUALIFIERS)
class BarQueueManagementVideoTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        MetronomeEngine.resetForTesting()
        MetronomeEngine.attach(RuntimeEnvironment.getApplication())
        MetronomeEngine.resetQueueToDefault()
        TouchIndicator.clear()
    }

    @After
    fun tearDown() {
        MetronomeEngine.resetForTesting()
        TouchIndicator.clear()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun `record adding bars, tapping to jump, long-pressing to remove`() {
        MetronomeEngine.markBpmHintShown()
        composeTestRule.setThemedContent {
            TouchIndicatorOverlay { MainScreen(onActivateToy = {}) }
        }

        val addButton = composeTestRule.onNodeWithTag("queue_add_button")
        composeTestRule.onScreenshotRoot().recordRoboVideo(
            composeRule = composeTestRule,
            filePath = videoPath("bar-queue-management"),
            videoOptions = RoboVideoOptions(fps = 10),
        ) {
            tapWithIndicator(addButton)
            delay(200)
            tapWithIndicator(addButton)
            delay(300)

            // A single longClick() (not pressAndHoldWithIndicator's separate down()/delay()/up()
            // calls) - BarQueueDots' bars are freshly (re)composed the instant they're jumped to
            // (each add-tap both appends and activates the new bar), and splitting the gesture
            // into multiple round-trip fetches was observed to occasionally race that, throwing
            // "node no longer in the tree". One atomic gesture call sidesteps it.
            val lastBar = composeTestRule.onNodeWithTag("queue_bar_2")
            TouchIndicator.at(lastBar.fetchSemanticsNode().boundsInRoot.center)
            lastBar.performTouchInput { longClick(center) }
            delay(300)
            TouchIndicator.clear()

            val firstBar = composeTestRule.onNodeWithTag("queue_bar_0")
            tapWithIndicator(firstBar)
            delay(300)
        }
    }
}
