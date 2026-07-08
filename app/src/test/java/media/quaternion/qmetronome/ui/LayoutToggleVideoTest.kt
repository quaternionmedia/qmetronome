package media.quaternion.qmetronome.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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

/** The animated-GIF counterpart to [LayoutToggleScreenshotTest] - showing the Material `Switch`'s
 * own thumb-slide animation is exactly the kind of motion a static before/after pair can't convey.
 * See [BpmDragVideoTest]'s kdoc for why these are separate tests/files from the screenshot ones
 * and why they're wrapped in [TouchIndicatorOverlay]. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = FULLSCREEN_QUALIFIERS)
class LayoutToggleVideoTest {

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
    fun `record toggling compact landscape layout`() {
        MetronomeEngine.setCompactLandscape(false)
        composeTestRule.setThemedContent {
            TouchIndicatorOverlay { SettingsSheet(onDismiss = {}, onActivateToy = {}) }
        }

        val chevron = composeTestRule.onNodeWithTag("section_header_Layout", useUnmergedTree = true)
        val toggle = composeTestRule.onNodeWithTag("compact_landscape_switch")
        composeTestRule.onScreenshotRoot().recordRoboVideo(
            composeRule = composeTestRule,
            filePath = videoPath("compact-landscape-layout"),
            videoOptions = RoboVideoOptions(fps = 10),
        ) {
            invokeClickWithIndicator(chevron)
            delay(200)
            toggle.performScrollTo()
            delay(200)
            invokeClickWithIndicator(toggle, holdMs = 400) // longer hold: lets the thumb-slide settle on camera
        }
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun `record toggling symbol-only controls`() {
        MetronomeEngine.setSymbolicControlsEnabled(false)
        composeTestRule.setThemedContent {
            TouchIndicatorOverlay { SettingsSheet(onDismiss = {}, onActivateToy = {}) }
        }

        val chevron = composeTestRule.onNodeWithTag("section_header_Layout", useUnmergedTree = true)
        val toggle = composeTestRule.onNodeWithTag("symbol_only_controls_switch")
        composeTestRule.onScreenshotRoot().recordRoboVideo(
            composeRule = composeTestRule,
            filePath = videoPath("symbol-only-controls"),
            videoOptions = RoboVideoOptions(fps = 10),
        ) {
            invokeClickWithIndicator(chevron)
            delay(200)
            toggle.performScrollTo()
            delay(200)
            invokeClickWithIndicator(toggle, holdMs = 400)
        }
    }
}
