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

/** The animated-GIF counterpart to [PreviewGestureScreenshotTest]'s swipe topic - see
 * [BpmDragVideoTest]'s kdoc for why this is a separate test/file from the screenshot one and why
 * it's wrapped in [TouchIndicatorOverlay]. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = FULLSCREEN_QUALIFIERS)
class PreviewSwipeVideoTest {

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
    fun `record swiping the preview left, then right`() {
        MetronomeEngine.markBpmHintShown()
        composeTestRule.setThemedContent {
            TouchIndicatorOverlay { MainScreen(onActivateToy = {}) }
        }

        val preview = composeTestRule.onNodeWithTag("matrix_preview")
        composeTestRule.onScreenshotRoot().recordRoboVideo(
            composeRule = composeTestRule,
            filePath = videoPath("preview-swipe-visualizer"),
            videoOptions = RoboVideoOptions(fps = 10),
        ) {
            dragWithIndicator(preview, steps = 8, stepOffset = Offset(-30f, 0f))
            delay(300)
            dragWithIndicator(preview, steps = 8, stepOffset = Offset(30f, 0f))
            delay(300)
        }
    }
}
