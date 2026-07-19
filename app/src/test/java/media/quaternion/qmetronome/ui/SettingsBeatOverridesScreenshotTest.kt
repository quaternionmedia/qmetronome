package media.quaternion.qmetronome.ui

import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
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

/** Settings' "Beat Overrides" section - browsing to a specific phrase/bar/beat (via the same
 * phrase/bar dot pickers the main screen's own queues use) and giving that beat its own MIDI
 * action that wins over its type's configured default (see
 * [MetronomeEngine.resolveMidiActionForBeat]). The manual Trigger button now lives on the main
 * screen instead (see `MainScreenTriggerButtonScreenshotTest` and `MainScreen.kt`'s own
 * `TriggerButton` kdoc for why). */
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
        MetronomeEngine.setMidiOverride(0, 0, 1, MidiBeatAction(type = MidiActionType.NOTE))
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

    @Test
    fun `with only one phrase and one bar, no dot pickers render - just the beat stepper`() {
        composeTestRule.setThemedContent {
            SettingsSheet(onDismiss = {}, onActivateToy = {})
        }
        composeTestRule.onNodeWithTag("section_header_Beat_Overrides", useUnmergedTree = true).invokeOnClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("beat_override_index_label").performScrollTo()

        composeTestRule.onNodeWithTag("phrase_dot_0").assertDoesNotExist()
        composeTestRule.onNodeWithTag("queue_bar_0").assertDoesNotExist()
    }

    @Test
    fun `picking a non-active phrase and bar sets the override there, not on the active bar`() {
        MetronomeEngine.addBarToQueue() // phrase 0 now has 2 bars, bar 1 active
        MetronomeEngine.addPhrase() // phrase 1, active - phrase 0 (2 bars) is now the non-active one
        composeTestRule.setThemedContent {
            SettingsSheet(onDismiss = {}, onActivateToy = {})
        }
        composeTestRule.onNodeWithTag("section_header_Beat_Overrides", useUnmergedTree = true).invokeOnClick()
        composeTestRule.waitForIdle()

        // Browse to phrase 0 (not the active phrase 1), then its second bar (index 1). The dots
        // themselves are raw pointerInput/detectTapGestures targets, not semantics-based
        // clickables, so performTouchInput{click()} is needed here (not .invokeOnClick()) - the
        // same way BarQueueScreenshotTest/PhraseQueueScreenshotTest tap dots.
        composeTestRule.onNodeWithTag("phrase_dot_0").performScrollTo()
        composeTestRule.onNodeWithTag("phrase_dot_0").performTouchInput { click(center) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("queue_bar_1").performScrollTo()
        composeTestRule.onNodeWithTag("queue_bar_1").performTouchInput { click(center) }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Note").invokeOnClick()
        composeTestRule.waitForIdle()

        // Landed on phrase 0's bar 1, beat 0 (the default beat) - not phrase 1's active bar.
        assertEquals(MidiActionType.NOTE, MetronomeEngine.phrases.value[0].bars[1].midiOverrideAt(0)?.type)
        assertNull(MetronomeEngine.timeSignature.value.midiOverrideAt(0))
        assertNull(MetronomeEngine.phrases.value[1].bars[0].midiOverrideAt(0))
    }
}
