package media.quaternion.qmetronome.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import media.quaternion.qmetronome.engine.MetronomeEngine
import media.quaternion.qmetronome.visualizers.VisualizerRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Glyph Matrix preview's three gesture affordances, each their own topic/screenshot since
 * each represents a different moment. See [PreviewBox]'s own kdoc for why long-press/double-tap
 * share one detector and the drag lives on the inner [MatrixPreview] separately. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = FULLSCREEN_QUALIFIERS)
class PreviewGestureScreenshotTest {

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

    private fun setPreviewContent() {
        composeTestRule.setThemedContent {
            MainScreen(onActivateToy = {})
        }
    }

    @Test
    fun `swiping left cycles to the next visualizer, swiping right cycles back`() {
        val starting = MetronomeEngine.visualizer.value
        setPreviewContent()

        composeTestRule.onScreenshotRoot().captureRoboImage(screenshotPath("preview-swipe-visualizer"))

        val preview = composeTestRule.onNodeWithTag("matrix_preview")
        // 3x the pixel offsets a 1x-density window would need - FULLSCREEN_QUALIFIERS renders at
        // xxhdpi (3.0 scale), and the swipe threshold (56dp) scales with density the same way.
        preview.performTouchInput {
            down(center)
            repeat(10) {
                moveBy(Offset(-30f, 0f))
                advanceEventTime(16)
            }
            up()
        }
        composeTestRule.waitForIdle()
        assertEquals(VisualizerRegistry.next(starting).id, MetronomeEngine.visualizer.value.id)

        preview.performTouchInput {
            down(center)
            repeat(10) {
                moveBy(Offset(30f, 0f))
                advanceEventTime(16)
            }
            up()
        }
        composeTestRule.waitForIdle()
        assertEquals(starting.id, MetronomeEngine.visualizer.value.id)
    }

    @Test
    fun `double-tapping the preview toggles play or stop`() {
        setPreviewContent()
        assertTrue("expected a fresh engine to start stopped", !MetronomeEngine.state.value.isPlaying)

        composeTestRule.onScreenshotRoot().captureRoboImage(screenshotPath("preview-double-tap-play"))

        composeTestRule.onNodeWithTag("matrix_preview").performTouchInput { doubleClick(center) }
        composeTestRule.waitForIdle()
        assertTrue("expected double-tap to start playback", MetronomeEngine.state.value.isPlaying)
    }

    @Test
    fun `long-pressing the preview opens settings`() {
        setPreviewContent()

        composeTestRule.onScreenshotRoot().captureRoboImage(screenshotPath("preview-long-press-settings"))

        composeTestRule.onNodeWithTag("matrix_preview").performTouchInput { longClick(center) }
        composeTestRule.waitForIdle()
        // The real end-to-end effect (Settings actually opens), not a substitute callback flag -
        // MainScreen owns showSettings itself now that this test renders the whole screen rather
        // than a bare PreviewBox with its own onShowSettings parameter.
        composeTestRule.onNodeWithText("Settings").assertExists()
    }
}
