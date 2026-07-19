package media.quaternion.qmetronome.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import media.quaternion.qmetronome.engine.ClickSound
import media.quaternion.qmetronome.engine.MetronomeEngine
import media.quaternion.qmetronome.engine.MidiActionType
import media.quaternion.qmetronome.midi.MidiActionSender
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings' "MIDI Actions" section - a master switch plus per-beat-type Note/CC configuration,
 * sent over the same virtual/USB connections "Send clock" already reaches. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = FULLSCREEN_QUALIFIERS)
class SettingsMidiActionsScreenshotTest {

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
    fun `enabling MIDI Actions and picking Note for a beat type configures it`() {
        composeTestRule.setThemedContent {
            SettingsSheet(onDismiss = {}, onActivateToy = {})
        }

        // See SettingsClockFeelScreenshotTest's own kdoc for why the header is queried unmerged
        // and .invokeOnClick() (not .performClick()) is used throughout this section.
        composeTestRule.onNodeWithTag("section_header_MIDI_Actions", useUnmergedTree = true).invokeOnClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("midi_actions_switch").performScrollTo()
        composeTestRule.onNodeWithTag("midi_actions_switch").invokeOnClick()
        composeTestRule.waitForIdle()
        assertEquals(true, MidiActionSender.enabled.value)

        composeTestRule.onScreenshotRoot().captureRoboImage(screenshotPath("midi-actions"))

        // The Bar tab is selected by default - picking "Note" configures ClickSound.BAR's action.
        composeTestRule.onNodeWithText("Note").invokeOnClick()
        composeTestRule.waitForIdle()

        assertEquals(MidiActionType.NOTE, MidiActionSender.actions.value.getValue(ClickSound.BAR).type)
    }
}
