package media.quaternion.qmetronome.ui

import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import media.quaternion.qmetronome.engine.MetronomeEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [MetronomeEngine.tapTempo] reads real wall-clock time ([System.nanoTime]), not Compose's virtual
 * test clock, so this test uses a real (short) [Thread.sleep] between taps rather than advancing
 * `composeTestRule.mainClock` - the only way to give it a real, predictable interval to derive a
 * bpm from.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = FULLSCREEN_QUALIFIERS)
class TapTempoScreenshotTest {

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
    fun `first tap is a no-op, second tap derives bpm from the interval between taps`() {
        MetronomeEngine.setBpm(200f) // clearly different from the ~120 bpm the tapped interval below derives
        MetronomeEngine.markBpmHintShown()
        composeTestRule.setThemedContent {
            MainScreen(onActivateToy = {})
        }

        composeTestRule.onScreenshotRoot().captureRoboImage(screenshotPath("tap-tempo"))

        val bpmNumber = composeTestRule.onNodeWithTag("bpm_number")
        bpmNumber.performTouchInput { click(center) }
        composeTestRule.waitForIdle()
        assertEquals(
            "a lone first tap only starts timing - it shouldn't touch bpm yet",
            200f,
            MetronomeEngine.state.value.bpm,
            0.001f,
        )

        Thread.sleep(500)

        bpmNumber.performTouchInput { click(center) }
        composeTestRule.waitForIdle()
        assertEquals(
            "a ~500ms gap between two taps should derive roughly 120 bpm",
            120f,
            MetronomeEngine.state.value.bpm,
            5f,
        )
    }
}
