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

/** The animated-GIF counterpart to [PhraseQueueScreenshotTest]'s phrase-building topic - see
 * [BarQueueManagementVideoTest]'s kdoc for why this is a separate test/file from the screenshot
 * one and why it's wrapped in [TouchIndicatorOverlay]. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = FULLSCREEN_QUALIFIERS)
class PhraseQueueManagementVideoTest {

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
    fun `record inviting the phrase strip, adding phrases, tapping to jump, long-pressing to remove`() {
        MetronomeEngine.markBpmHintShown()
        composeTestRule.setThemedContent {
            TouchIndicatorOverlay { MainScreen(onActivateToy = {}) }
        }

        val entryButton = composeTestRule.onNodeWithTag("phrase_add_entry_button")
        composeTestRule.onScreenshotRoot().recordRoboVideo(
            composeRule = composeTestRule,
            filePath = videoPath("phrase-queue-management"),
            videoOptions = RoboVideoOptions(fps = 10),
        ) {
            tapWithIndicator(entryButton)
            delay(300)

            val addButton = composeTestRule.onNodeWithTag("phrase_add_button")
            tapWithIndicator(addButton)
            delay(300)

            // A single longClick() (not separate down()/delay()/up() calls) - same reasoning as
            // BarQueueManagementVideoTest's own matching step: the dots are freshly (re)composed
            // the instant a phrase is jumped to, and splitting the gesture risked racing that.
            val lastRow = composeTestRule.onNodeWithTag("phrase_dot_2")
            TouchIndicator.at(lastRow.fetchSemanticsNode().boundsInRoot.center)
            lastRow.performTouchInput { longClick(center) }
            delay(300)
            TouchIndicator.clear()

            val firstRow = composeTestRule.onNodeWithTag("phrase_dot_0")
            tapWithIndicator(firstRow)
            delay(300)
        }
    }
}
