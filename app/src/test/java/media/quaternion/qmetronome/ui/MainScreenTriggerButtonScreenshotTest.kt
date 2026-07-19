package media.quaternion.qmetronome.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import media.quaternion.qmetronome.engine.MetronomeEngine
import media.quaternion.qmetronome.engine.MidiActionType
import media.quaternion.qmetronome.engine.MidiBeatAction
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

/** The main screen's manual MIDI Trigger action - not its own button (see `TransportRow`'s own
 * kdoc in `MainScreen.kt` for why a 4th always-visible button next to TAP/PLAY/HOLD broke that
 * row's symmetry), but a gesture layered onto the existing TAP button: while HOLD is latched and
 * MIDI Actions is on, tapping TAP fires whatever's configured for the engine's live current beat
 * instead of tap-tempo's usual "commit the staged tempo, start playing, unlatch". Replaces the
 * old Settings -> Beat Overrides "Trigger" button - see `SettingsBeatOverridesScreenshotTest`'s
 * own kdoc for why that one was removed first. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = FULLSCREEN_QUALIFIERS)
class MainScreenTriggerButtonScreenshotTest {

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
    fun `TAP keeps doing tap-tempo when not latched, even with MIDI Actions on`() {
        MidiActionSender.setEnabled(true)
        composeTestRule.setThemedContent {
            MainScreen(onActivateToy = {})
        }

        composeTestRule.onNodeWithText("TAP").assertExists()
        composeTestRule.onNodeWithContentDescription("Trigger the current beat's MIDI action").assertDoesNotExist()
    }

    @Test
    fun `TAP switches to Trigger once latched, with MIDI Actions on`() {
        MidiActionSender.setEnabled(true)
        MetronomeEngine.setMidiOverride(0, 0, 0, MidiBeatAction(type = MidiActionType.CC))
        composeTestRule.setThemedContent {
            MainScreen(onActivateToy = {})
        }
        // Driven directly rather than through a real long-press/double-tap gesture - see
        // HoldButtonScreenshotTest for why simulating that timing is its own, separate concern;
        // this test's own focus is TAP's reaction once Latched, however that state is reached.
        MetronomeEngine.toggleLatch()
        composeTestRule.waitForIdle()
        assertEquals(MetronomeEngine.HoldMode.Latched, MetronomeEngine.holdMode.value)

        composeTestRule.onNodeWithContentDescription("Trigger the current beat's MIDI action").assertExists()
        composeTestRule.onScreenshotRoot().captureRoboImage(screenshotPath("trigger-button"))

        composeTestRule.onNodeWithTag("tap_trigger_button").performClick()
        composeTestRule.waitForIdle()

        // Tap-tempo's own latched behavior (commit + start playing + unlatch) must NOT have run -
        // that's the clearest proof the tap was consumed as a Trigger fire instead. No direct
        // assertion on the MIDI send itself - MidiActionSender.fire() is a no-op with no
        // registered destination (see MidiActionSenderTest for its own send-path coverage); this
        // test's job is confirming the tap is wired to Trigger, not re-testing send.
        assertEquals(MetronomeEngine.HoldMode.Latched, MetronomeEngine.holdMode.value)
        assertEquals(false, MetronomeEngine.state.value.isPlaying)
    }

    @Test
    fun `TAP keeps its normal latched commit-and-play behavior when MIDI Actions is off`() {
        composeTestRule.setThemedContent {
            MainScreen(onActivateToy = {})
        }
        MetronomeEngine.toggleLatch()
        composeTestRule.waitForIdle()
        assertEquals(MetronomeEngine.HoldMode.Latched, MetronomeEngine.holdMode.value)

        composeTestRule.onNodeWithText("TAP").assertExists()
        composeTestRule.onNodeWithTag("tap_trigger_button").performClick()
        composeTestRule.waitForIdle()
        // A lone first tap only starts timing (see TapTempoScreenshotTest) - it doesn't derive a
        // bpm or touch hold state on its own, so this only confirms tap-tempo's own path ran
        // (nothing crashed reading MidiActionSender internals it shouldn't have touched), not a
        // full commit - the existing MetronomeEngineTest coverage owns that end-to-end assertion.
        assertEquals(MetronomeEngine.HoldMode.Latched, MetronomeEngine.holdMode.value)
    }
}
