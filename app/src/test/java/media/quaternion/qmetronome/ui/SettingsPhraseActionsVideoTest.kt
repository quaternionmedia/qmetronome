package media.quaternion.qmetronome.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoboVideoOptions
import com.github.takahirom.roborazzi.recordRoboVideo
import media.quaternion.qmetronome.engine.MetronomeEngine
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The animated-GIF counterpart to [SettingsPhraseActionsScreenshotTest] - picking a phrase via
 * its dot is the same "which node did the finger land on" motion [SettingsBeatOverridesVideoTest]
 * captures one level down - see [BpmDragVideoTest]'s kdoc for why this is a separate test/file
 * from the screenshot one and why it's wrapped in [TouchIndicatorOverlay]. Composes
 * [SettingsSheet] directly and scrolls to this section's header *before* recording starts, the
 * same shape [SettingsBeatOverridesVideoTest] uses - see its own kdoc for why `performScrollTo()`
 * can't be called inside the `recordRoboVideo` block itself (observed to hang indefinitely). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = FULLSCREEN_QUALIFIERS)
class SettingsPhraseActionsVideoTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        MetronomeEngine.resetForTesting()
        MetronomeEngine.attach(RuntimeEnvironment.getApplication())
        TouchIndicator.clear()
    }

    @After
    fun tearDown() {
        MetronomeEngine.resetForTesting()
        TouchIndicator.clear()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun `record opening Phrase Actions, picking a phrase via its dot, then assigning it an action`() {
        MetronomeEngine.addPhrase() // phrase 1
        composeTestRule.setThemedContent {
            TouchIndicatorOverlay { SettingsSheet(onDismiss = {}, onActivateToy = {}) }
        }

        val header = composeTestRule.onNodeWithTag("section_header_Phrase_Actions", useUnmergedTree = true)
        header.performScrollTo() // before recording starts - see this file's own kdoc for why

        composeTestRule.onScreenshotRoot().recordRoboVideo(
            composeRule = composeTestRule,
            filePath = videoPath("phrase-actions"),
            videoOptions = RoboVideoOptions(fps = 10),
        ) {
            invokeClickWithIndicator(header)
            delay(300)

            // A raw pointerInput target, not a semantics-clickable, so tapWithIndicator's own
            // down/delay/up shape is used rather than invokeOnClick *or* a bare
            // performTouchInput{click()} - see SettingsBeatOverridesVideoTest's own kdoc for why
            // the latter was observed to hang indefinitely here.
            val phraseDot = composeTestRule.onNodeWithTag("phrase_dot_1")
            tapWithIndicator(phraseDot)
            delay(300)

            val ccChip = composeTestRule.onNodeWithText("CC")
            invokeClickWithIndicator(ccChip)
            delay(300)
        }
    }
}
