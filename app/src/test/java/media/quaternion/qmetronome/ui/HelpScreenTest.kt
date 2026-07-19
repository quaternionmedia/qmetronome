package media.quaternion.qmetronome.ui

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import media.quaternion.qmetronome.engine.MetronomeEngine
import media.quaternion.qmetronome.midi.MidiActionSender
import media.quaternion.qmetronome.tutorial.TutorialTopics
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A smoke test, not a per-gesture one (those are covered by the individual composables'
 * `*ScreenshotTest`s already, and [HelpScreen] just embeds them) - confirms every
 * [TutorialTopics] category actually renders (title + at least its first topic's own title) and
 * that closing works, so a future category with no matching `when` branch or a topic silently
 * dropped from the screen would fail here. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = FULLSCREEN_QUALIFIERS)
class HelpScreenTest {

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
    fun `every category and topic renders, close dismisses`() {
        var dismissed = false
        composeTestRule.setThemedContent {
            HelpScreen(onDismiss = { dismissed = true })
        }

        composeTestRule.onNodeWithText("Help").assertExists()
        TutorialTopics.all.groupBy { it.category }.forEach { (category, topics) ->
            composeTestRule.onNodeWithText(category.displayName).assertExists()
            composeTestRule.onNodeWithText(topics.first().title).assertExists()
        }

        composeTestRule.onNodeWithContentDescription("Close help").performClick()
        composeTestRule.waitForIdle()
        assertTrue(dismissed)
    }

    @Test
    fun `the MIDI category embeds live Beat Overrides, Phrase Actions, and Trigger controls, not just text`() {
        composeTestRule.setThemedContent {
            HelpScreen(onDismiss = {})
        }

        // Beat Overrides and Phrase Actions each render their own beat/phrase stepper - present
        // (not just the topic's own title/description text) proves the real composable is
        // embedded here, the same one Settings uses, not a second disconnected copy.
        composeTestRule.onNodeWithTag("beat_override_index_label").performScrollTo()
        composeTestRule.onNodeWithTag("beat_override_index_label").assertExists()
        composeTestRule.onNodeWithTag("phrase_action_index_label").performScrollTo()
        composeTestRule.onNodeWithTag("phrase_action_index_label").assertExists()

        // Its own testTag (distinct from the production "tap_trigger_button" every other call
        // site shares) - TEMPO's own TempoTransportCluster copy, shown earlier on this same
        // scrollable screen, already puts one plain TAP button on screen, so both a text search
        // and (once latched) a contentDescription search would otherwise match two nodes: this
        // one and TEMPO's, since both read the exact same global hold/MIDI-Actions state and
        // switch to the identical Trigger icon at the same moment. Scoping every assertion to
        // this one uniquely-tagged node sidesteps that rather than fighting it.
        val trigger = composeTestRule.onNodeWithTag("help_midi_tap_trigger_button")
        trigger.performScrollTo()
        trigger.assertExists()

        MidiActionSender.setEnabled(true)
        MetronomeEngine.toggleLatch()
        composeTestRule.waitForIdle()
        trigger.assertContentDescriptionEquals("Trigger the current beat's MIDI action")
    }
}
