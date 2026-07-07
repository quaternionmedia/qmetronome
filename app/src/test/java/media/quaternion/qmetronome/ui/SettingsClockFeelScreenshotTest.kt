package media.quaternion.qmetronome.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import media.quaternion.qmetronome.engine.ClockTimingMode
import media.quaternion.qmetronome.engine.MetronomeEngine
import media.quaternion.qmetronome.midi.MidiClockSender
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings' "Outgoing clock feel" chip row, under the "Clock" section - Mechanical actively
 * corrects the outgoing MIDI clock; Organic lets a followed clock's own natural variance through
 * unfiltered. Only affects clock sent to other apps/gear, not this app's own click/flash. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = FULLSCREEN_QUALIFIERS)
class SettingsClockFeelScreenshotTest {

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
    fun `tapping Mechanical or Organic chooses the outgoing clock's timing mode`() {
        MidiClockSender.setTimingMode(ClockTimingMode.MECHANICAL)
        composeTestRule.setThemedContent {
            SettingsSheet(onDismiss = {}, onActivateToy = {})
        }

        // .invokeOnClick() (not .performClick()) throughout - see its kdoc in
        // ComposeTestSupport.kt. The header row is also queried unmerged specifically: its merged
        // node otherwise also carries the "Clock" section's own summary Switch (send-clock
        // enabled), and resolving that merged node's single OnClick risks picking the wrong one
        // of the two actions living on it.
        composeTestRule.onNodeWithTag("section_header_Clock", useUnmergedTree = true).invokeOnClick()
        composeTestRule.waitForIdle()
        // "Clock" is the last section in Settings' scrollable list - without scrolling to it
        // first, the capture below would just show whatever's at the top of the list instead.
        composeTestRule.onNodeWithTag("clock_feel_ORGANIC").performScrollTo()

        composeTestRule.onScreenshotRoot().captureRoboImage(screenshotPath("settings-clock-feel"))

        composeTestRule.onNodeWithTag("clock_feel_ORGANIC").invokeOnClick()
        composeTestRule.waitForIdle()
        assertEquals(ClockTimingMode.ORGANIC, MidiClockSender.timingMode.value)

        composeTestRule.onNodeWithTag("clock_feel_MECHANICAL").invokeOnClick()
        composeTestRule.waitForIdle()
        assertEquals(ClockTimingMode.MECHANICAL, MidiClockSender.timingMode.value)
    }
}
