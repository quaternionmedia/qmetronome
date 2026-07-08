package media.quaternion.qmetronome.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoboVideoOptions
import com.github.takahirom.roborazzi.recordRoboVideo
import media.quaternion.qmetronome.engine.ClockTimingMode
import media.quaternion.qmetronome.engine.MetronomeEngine
import media.quaternion.qmetronome.midi.MidiClockSender
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The animated-GIF counterpart to [SettingsClockFeelScreenshotTest] - see [BpmDragVideoTest]'s
 * kdoc for why this is a separate test/file from the screenshot one and why it's wrapped in
 * [TouchIndicatorOverlay]. Opens from [MainScreen] with Settings closed, tapping the gear icon
 * first, rather than composing [SettingsSheet] pre-opened - this is the one Settings-category
 * video reused as a top-level doc's hero (see `readme/using-qmetronome/staying-in-sync-with-other-
 * gear.md`), so it shows the real, complete path in: default running app, then into Settings,
 * not a mid-Settings starting point a reader would never actually land on. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = FULLSCREEN_QUALIFIERS)
class SettingsClockFeelVideoTest {

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
    fun `record expanding Clock, then toggling Mechanical and Organic`() {
        MidiClockSender.setTimingMode(ClockTimingMode.MECHANICAL)
        MetronomeEngine.markBpmHintShown()
        composeTestRule.setThemedContent {
            TouchIndicatorOverlay { MainScreen(onActivateToy = {}) }
        }

        val settingsButton = composeTestRule.onNodeWithContentDescription("Settings")
        composeTestRule.onScreenshotRoot().recordRoboVideo(
            composeRule = composeTestRule,
            filePath = videoPath("settings-clock-feel"),
            videoOptions = RoboVideoOptions(fps = 10),
        ) {
            tapWithIndicator(settingsButton)
            delay(300)

            // Fetched fresh here, not before the block starts - SettingsSheet doesn't exist in
            // the tree until the tap above opens it (same pattern BarQueueManagementVideoTest
            // uses for nodes that only appear partway through a recording).
            val header = composeTestRule.onNodeWithTag("section_header_Clock", useUnmergedTree = true)
            invokeClickWithIndicator(header)
            delay(300)

            val organic = composeTestRule.onNodeWithTag("clock_feel_ORGANIC")
            invokeClickWithIndicator(organic)
            delay(300)

            val mechanical = composeTestRule.onNodeWithTag("clock_feel_MECHANICAL")
            invokeClickWithIndicator(mechanical)
            delay(300)
        }
    }
}
