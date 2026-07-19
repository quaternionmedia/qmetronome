package media.quaternion.qmetronome.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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

/** The animated-GIF counterpart to [PhraseQueueScreenshotTest]'s mode-cycling topic - see
 * [BarQueueModeCyclingVideoTest]'s kdoc for why this is a separate test/file from the screenshot
 * one and why it's wrapped in [TouchIndicatorOverlay]. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = FULLSCREEN_QUALIFIERS)
class PhraseQueueModeCyclingVideoTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        MetronomeEngine.resetForTesting()
        MetronomeEngine.attach(RuntimeEnvironment.getApplication())
        MetronomeEngine.resetPhrasesToDefault()
        TouchIndicator.clear()
    }

    @After
    fun tearDown() {
        MetronomeEngine.resetForTesting()
        TouchIndicator.clear()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun `record tapping the phrase mode icon through Loop, Once, Manual`() {
        MetronomeEngine.markBpmHintShown()
        composeTestRule.setThemedContent {
            TouchIndicatorOverlay { MainScreen(onActivateToy = {}) }
        }
        // A second phrase must exist first - the phrase-management strip (and its mode icon) doesn't
        // exist at all with just one. Not part of the recorded video itself, same as how
        // BarQueueModeCyclingVideoTest's setUp already has a queue ready before recording starts.
        composeTestRule.onNodeWithTag("phrase_add_entry_button").performClick()
        composeTestRule.waitForIdle()

        val modeButton = composeTestRule.onNodeWithTag("phrase_mode_button")
        composeTestRule.onScreenshotRoot().recordRoboVideo(
            composeRule = composeTestRule,
            filePath = videoPath("phrase-queue-mode-cycling"),
            videoOptions = RoboVideoOptions(fps = 10),
        ) {
            repeat(3) {
                tapWithIndicator(modeButton)
                delay(300)
            }
        }
    }
}
