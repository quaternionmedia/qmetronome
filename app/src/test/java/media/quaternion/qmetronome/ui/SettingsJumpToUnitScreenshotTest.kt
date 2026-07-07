package media.quaternion.qmetronome.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import media.quaternion.qmetronome.engine.MetronomeEngine
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

/** Settings' "Jump to unit" chip row, under the "Tempo & Bars" section - jumps the live tempo
 * straight into a unit's own range rather than dragging/typing to get there. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = FULLSCREEN_QUALIFIERS)
class SettingsJumpToUnitScreenshotTest {

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
    fun `tapping a unit chip jumps tempo into that unit's own range`() {
        MetronomeEngine.setBpm(120f)
        composeTestRule.setThemedContent {
            SettingsSheet(onDismiss = {}, onActivateToy = {})
        }

        composeTestRule.onNodeWithTag("section_header_Tempo_and_Bars").performTouchInput { click(Offset(1f, 1f)) }
        composeTestRule.waitForIdle()

        composeTestRule.onScreenshotRoot().captureRoboImage(screenshotPath("settings-jump-to-unit"))

        // .invokeOnClick(), not .performClick() - see its kdoc in ComposeTestSupport.kt.
        composeTestRule.onNodeWithTag("jump_to_unit_BPH").invokeOnClick()
        composeTestRule.waitForIdle()
        assertTrue(MetronomeEngine.extendedBpmRangeEnabled.value)
        assertEquals("BPH", bpmDisplayUnit(MetronomeEngine.state.value.bpm))

        composeTestRule.onNodeWithTag("jump_to_unit_BPS").invokeOnClick()
        composeTestRule.waitForIdle()
        assertEquals("BPS", bpmDisplayUnit(MetronomeEngine.state.value.bpm))

        composeTestRule.onNodeWithTag("jump_to_unit_BPM").invokeOnClick()
        composeTestRule.waitForIdle()
        assertEquals("BPM", bpmDisplayUnit(MetronomeEngine.state.value.bpm))
    }
}
