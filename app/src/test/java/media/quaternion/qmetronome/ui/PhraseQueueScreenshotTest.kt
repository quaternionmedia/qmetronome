package media.quaternion.qmetronome.ui

import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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

/** Covers [PhraseQueueControls] - the phrase-management strip that only exists once a second phrase has
 * been added (see [BeatsPerBarControls]'s "+phrase" entry point) - the same two-topics-from-one-
 * composable split [BarQueueScreenshotTest] already uses for its bar-level counterpart. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = FULLSCREEN_QUALIFIERS)
class PhraseQueueScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        MetronomeEngine.resetForTesting()
        MetronomeEngine.attach(RuntimeEnvironment.getApplication())
        MetronomeEngine.resetPhrasesToDefault()
    }

    @After
    fun tearDown() {
        MetronomeEngine.resetForTesting()
    }

    @Test
    fun `the phrase strip is invisible with one phrase, add phrases, tap to jump, long-press to remove`() {
        composeTestRule.setThemedContent { MainScreen(onActivateToy = {}) }

        // Invisible until invoked - none of the phrase-management strip exists with a single phrase.
        composeTestRule.onNodeWithTag("phrase_reset_button").assertDoesNotExist()

        val entryButton = composeTestRule.onNodeWithTag("phrase_add_entry_button")
        entryButton.performClick()
        composeTestRule.waitForIdle()
        assertEquals(2, MetronomeEngine.phrases.value.size)
        // The entry point itself is gone once the strip takes over - no redundant second "add
        // phrase" affordance sitting next to the strip's own.
        composeTestRule.onNodeWithTag("phrase_add_entry_button").assertDoesNotExist()

        val addButton = composeTestRule.onNodeWithTag("phrase_add_button")
        addButton.performClick()
        composeTestRule.waitForIdle()
        assertEquals(3, MetronomeEngine.phrases.value.size)

        composeTestRule.onScreenshotRoot().captureRoboImage(screenshotPath("phrase-queue-management"))

        // Tap the first phrase to jump to it.
        composeTestRule.onNodeWithTag("phrase_dot_0").performTouchInput { click(center) }
        composeTestRule.waitForIdle()
        assertEquals(0, MetronomeEngine.activePhraseIndex.value)

        // Long-press the last phrase to remove it.
        composeTestRule.onNodeWithTag("phrase_dot_2").performTouchInput { longClick(center) }
        composeTestRule.waitForIdle()
        assertEquals(2, MetronomeEngine.phrases.value.size)
    }

    @Test
    fun `tapping the phrase mode icon cycles Loop, Once, Manual`() {
        composeTestRule.setThemedContent { MainScreen(onActivateToy = {}) }
        composeTestRule.onNodeWithTag("phrase_add_entry_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(MetronomeEngine.QueueMode.LOOP, MetronomeEngine.phraseQueueMode.value)

        composeTestRule.onScreenshotRoot().captureRoboImage(screenshotPath("phrase-queue-mode-cycling"))

        val modeButton = composeTestRule.onNodeWithTag("phrase_mode_button")
        modeButton.performClick()
        composeTestRule.waitForIdle()
        assertEquals(MetronomeEngine.QueueMode.ONCE, MetronomeEngine.phraseQueueMode.value)

        modeButton.performClick()
        composeTestRule.waitForIdle()
        assertEquals(MetronomeEngine.QueueMode.MANUAL, MetronomeEngine.phraseQueueMode.value)

        modeButton.performClick()
        composeTestRule.waitForIdle()
        assertEquals(MetronomeEngine.QueueMode.LOOP, MetronomeEngine.phraseQueueMode.value)
    }

    @Test
    fun `reset and remove buttons only appear while HOLD is active`() {
        composeTestRule.setThemedContent { MainScreen(onActivateToy = {}) }
        composeTestRule.onNodeWithTag("phrase_add_entry_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("phrase_reset_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("phrase_remove_button").assertDoesNotExist()

        MetronomeEngine.beginHold()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("phrase_reset_button").assertExists()
        composeTestRule.onNodeWithTag("phrase_remove_button").assertExists()

        MetronomeEngine.endHold()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("phrase_reset_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("phrase_remove_button").assertDoesNotExist()
    }
}
