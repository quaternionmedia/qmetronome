package media.quaternion.qmetronome.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import media.quaternion.qmetronome.engine.MetronomeEngine
import media.quaternion.qmetronome.engine.MidiActionType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings' "Phrase Actions" section - stepping to a specific phrase and giving it its own MIDI
 * action, fired once via [MetronomeEngine.goToPhrase] whenever that phrase is entered. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = FULLSCREEN_QUALIFIERS)
class SettingsPhraseActionsScreenshotTest {

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
    fun `stepping to a phrase and picking CC gives it its own action`() {
        MetronomeEngine.addPhrase() // phrase 1
        composeTestRule.setThemedContent {
            SettingsSheet(onDismiss = {}, onActivateToy = {})
        }

        // See SettingsClockFeelScreenshotTest's own kdoc for why the header is queried unmerged
        // and .invokeOnClick() (not .performClick()) is used throughout this section.
        composeTestRule.onNodeWithTag("section_header_Phrase_Actions", useUnmergedTree = true).invokeOnClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("phrase_action_next_button").performScrollTo()

        composeTestRule.onNodeWithTag("phrase_action_next_button").invokeOnClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Phrase 2 of 2").assertExists()

        composeTestRule.onScreenshotRoot().captureRoboImage(screenshotPath("phrase-actions"))

        composeTestRule.onNodeWithText("CC").invokeOnClick()
        composeTestRule.waitForIdle()

        assertEquals(MidiActionType.CC, MetronomeEngine.phrases.value[1].action.type)
        assertEquals(MidiActionType.NONE, MetronomeEngine.phrases.value[0].action.type)
    }

    @Test
    fun `the phrase stepper is scoped to however many phrases currently exist`() {
        composeTestRule.setThemedContent {
            SettingsSheet(onDismiss = {}, onActivateToy = {})
        }
        composeTestRule.onNodeWithTag("section_header_Phrase_Actions", useUnmergedTree = true).invokeOnClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("phrase_action_index_label").performScrollTo()

        // With only the single default phrase, there's nowhere further to step to.
        composeTestRule.onNodeWithText("Phrase 1 of 1").assertExists()
    }
}
