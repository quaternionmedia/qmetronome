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

/** The animated-GIF counterpart to [SettingsBeatOverridesScreenshotTest] - browsing to a
 * non-active phrase/bar via the dot pickers before stepping to a beat is exactly the kind of
 * "which node did the finger just land on" motion a single before/after screenshot pair loses -
 * see [BpmDragVideoTest]'s kdoc for why this is a separate test/file from the screenshot one and
 * why it's wrapped in [TouchIndicatorOverlay].
 *
 * Composes [SettingsSheet] directly (not [MainScreen] first), the same shortcut
 * [SettingsJumpToUnitVideoTest] takes - Beat Overrides sits far enough down Settings' own list
 * that reaching it needs a real scroll, and `performScrollTo()` was observed to hang indefinitely
 * when called *inside* a `recordRoboVideo` block (no existing video test in this codebase calls
 * it there - `SettingsClockFeelVideoTest`/`SettingsJumpToUnitVideoTest` both only ever reach
 * sections near the top). The scroll to this section's header happens once, before recording
 * starts; everything the recording actually shows (expanding the section, its own picker/stepper/
 * chips) stays within the one screenful that scroll lands on, so no further scrolling is needed
 * mid-recording. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = FULLSCREEN_QUALIFIERS)
class SettingsBeatOverridesVideoTest {

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
    fun `record opening Beat Overrides, browsing to a phrase and bar, then assigning a beat its own action`() {
        MetronomeEngine.setBeatsPerBar(4)
        MetronomeEngine.addBarToQueue() // phrase 0 now has 2 bars
        MetronomeEngine.addPhrase() // phrase 1, active - phrase 0's 2 bars are the ones to browse to
        composeTestRule.setThemedContent {
            TouchIndicatorOverlay { SettingsSheet(onDismiss = {}, onActivateToy = {}) }
        }

        val header = composeTestRule.onNodeWithTag("section_header_Beat_Overrides", useUnmergedTree = true)
        header.performScrollTo() // before recording starts - see this file's own kdoc for why

        composeTestRule.onScreenshotRoot().recordRoboVideo(
            composeRule = composeTestRule,
            filePath = videoPath("beat-overrides"),
            videoOptions = RoboVideoOptions(fps = 10),
        ) {
            invokeClickWithIndicator(header)
            delay(300)

            // Browse to phrase 0 (not the active phrase 1) via its dot - a raw pointerInput
            // target, not a semantics-clickable, so tapWithIndicator's own down/delay/up shape is
            // used rather than invokeOnClick *or* a bare performTouchInput{click()} - the latter
            // was observed to hang indefinitely here: detectTapGestures' own tap-vs-long-press
            // disambiguation window needs a clock tick from RoboVideoRecorderScope's own delay()
            // to resolve, which a single compound click() never yields back to record.
            val phraseDot = composeTestRule.onNodeWithTag("phrase_dot_0")
            tapWithIndicator(phraseDot)
            delay(300)

            // Then its second bar.
            val barDot = composeTestRule.onNodeWithTag("queue_bar_1")
            tapWithIndicator(barDot)
            delay(300)

            val nextBeatButton = composeTestRule.onNodeWithTag("beat_override_next_button")
            invokeClickWithIndicator(nextBeatButton)
            delay(200)
            invokeClickWithIndicator(nextBeatButton)
            delay(300)

            val noteChip = composeTestRule.onNodeWithText("Note")
            invokeClickWithIndicator(noteChip)
            delay(300)
        }
    }
}
