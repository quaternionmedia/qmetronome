package media.quaternion.qmetronome.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import media.quaternion.qmetronome.engine.MetronomeEngine
import media.quaternion.qmetronome.engine.MidiActionType
import media.quaternion.qmetronome.engine.MidiBeatAction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings' "Beat Overrides" section - stepping to a specific beat, giving it its own MIDI
 * action that wins over its type's configured default (see
 * [MetronomeEngine.resolveMidiActionForBeat]), and the manual Trigger button for one-shot
 * verification without starting playback. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = FULLSCREEN_QUALIFIERS)
class SettingsBeatOverridesScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        MetronomeEngine.resetForTesting()
        MetronomeEngine.attach(RuntimeEnvironment.getApplication())
        MetronomeEngine.setBeatsPerBar(4)
    }

    @After
    fun tearDown() {
        MetronomeEngine.resetForTesting()
    }

    @Test
    fun `stepping to a beat and picking Note gives it its own override, independent of beat 0`() {
        composeTestRule.setThemedContent {
            SettingsSheet(onDismiss = {}, onActivateToy = {})
        }

        // See SettingsClockFeelScreenshotTest's own kdoc for why the header is queried unmerged
        // and .invokeOnClick() (not .performClick()) is used throughout this section.
        composeTestRule.onNodeWithTag("section_header_Beat_Overrides", useUnmergedTree = true).invokeOnClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("beat_override_next_button").performScrollTo()

        // Starts on beat 1 (index 0); step to beat 2 (index 1).
        composeTestRule.onNodeWithTag("beat_override_next_button").invokeOnClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Beat 2 of 4").assertExists()

        composeTestRule.onScreenshotRoot().captureRoboImage(screenshotPath("beat-overrides"))

        composeTestRule.onNodeWithText("Note").invokeOnClick()
        composeTestRule.waitForIdle()

        assertEquals(MidiActionType.NOTE, MetronomeEngine.timeSignature.value.midiOverrideAt(1)?.type)
        assertNull(MetronomeEngine.timeSignature.value.midiOverrideAt(0))
    }

    @Test
    fun `clearing an override removes it and the clear button disappears`() {
        MetronomeEngine.setMidiOverride(1, MidiBeatAction(type = MidiActionType.NOTE))
        composeTestRule.setThemedContent {
            SettingsSheet(onDismiss = {}, onActivateToy = {})
        }
        composeTestRule.onNodeWithTag("section_header_Beat_Overrides", useUnmergedTree = true).invokeOnClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("beat_override_next_button").performScrollTo()
        composeTestRule.onNodeWithTag("beat_override_next_button").invokeOnClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("beat_override_clear_button").assertExists()

        composeTestRule.onNodeWithTag("beat_override_clear_button").invokeOnClick()
        composeTestRule.waitForIdle()

        assertNull(MetronomeEngine.timeSignature.value.midiOverrideAt(1))
        composeTestRule.onNodeWithTag("beat_override_clear_button").assertDoesNotExist()
    }

    @Test
    fun `the Trigger button fires without crashing, on whatever beat the engine is currently at`() {
        MetronomeEngine.setMidiOverride(0, MidiBeatAction(type = MidiActionType.CC))
        composeTestRule.setThemedContent {
            SettingsSheet(onDismiss = {}, onActivateToy = {})
        }
        composeTestRule.onNodeWithTag("section_header_Beat_Overrides", useUnmergedTree = true).invokeOnClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("midi_trigger_button").performScrollTo()

        composeTestRule.onNodeWithTag("midi_trigger_button").invokeOnClick()
        composeTestRule.waitForIdle()
        // No assertion beyond "didn't crash" - MidiActionSender.fire() is a no-op with no
        // registered destination (see MidiActionSenderTest for its own send-path coverage);
        // this test's job is confirming the button is wired to a live call, not re-testing send.
    }

    @Test
    fun `an unmarked beat shows as following its resolved type's own default, not blank`() {
        composeTestRule.setThemedContent {
            SettingsSheet(onDismiss = {}, onActivateToy = {})
        }
        composeTestRule.onNodeWithTag("section_header_Beat_Overrides", useUnmergedTree = true).invokeOnClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("beat_override_index_label").performScrollTo()

        // Beat 1 (index 0) is always ClickSound.BAR - see MetronomeEngine.beatTypeFor.
        composeTestRule.onNodeWithText("Following Bar's own default").assertExists()
    }
}
