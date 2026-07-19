package media.quaternion.qmetronome.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import media.quaternion.qmetronome.engine.MetronomeEngine
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings' "Layout" section - two independent toggles, each its own topic/screenshot. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = FULLSCREEN_QUALIFIERS)
class LayoutToggleScreenshotTest {

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

    // .invokeOnClick() (not .performClick()) throughout - see its kdoc in ComposeTestSupport.kt.
    // The header row is queried unmerged specifically: its merged node also carries the "Layout"
    // section's own summary Switch (compact landscape), and resolving that merged node's single
    // OnClick risks picking the wrong one of the two actions living on it.
    private fun expandSection(headerTag: String) {
        composeTestRule.onNodeWithTag(headerTag, useUnmergedTree = true).invokeOnClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun `toggling compact landscape layout`() {
        MetronomeEngine.setCompactLandscape(false)
        composeTestRule.setThemedContent { SettingsSheet(onDismiss = {}, onActivateToy = {}) }
        expandSection("section_header_Layout")
        // "Layout" sits well down Settings' scrollable list - without scrolling first, the
        // capture below would just show whatever's at the (unrelated) top of the list instead.
        // Scrolled to the *next* switch down (not this section's own) so the compact-landscape
        // switch itself lands comfortably inside the frame instead of right at its bottom edge.
        composeTestRule.onNodeWithTag("symbol_only_controls_switch").performScrollTo()

        composeTestRule.onScreenshotRoot().captureRoboImage(screenshotPath("compact-landscape-layout"))

        composeTestRule.onNodeWithTag("compact_landscape_switch").invokeOnClick()
        composeTestRule.waitForIdle()
        assertTrue(MetronomeEngine.compactLandscape.value)
    }

    @Test
    fun `toggling symbol-only controls`() {
        MetronomeEngine.setSymbolicControlsEnabled(false)
        composeTestRule.setThemedContent { SettingsSheet(onDismiss = {}, onActivateToy = {}) }
        expandSection("section_header_Layout")
        composeTestRule.onNodeWithTag("symbol_only_controls_switch").performScrollTo()

        composeTestRule.onScreenshotRoot().captureRoboImage(screenshotPath("symbol-only-controls"))

        composeTestRule.onNodeWithTag("symbol_only_controls_switch").invokeOnClick()
        composeTestRule.waitForIdle()
        assertTrue(MetronomeEngine.symbolicControlsEnabled.value)
    }

    @Test
    fun `toggling unit symbols`() {
        MetronomeEngine.setUnitSymbolsEnabled(true)
        composeTestRule.setThemedContent { SettingsSheet(onDismiss = {}, onActivateToy = {}) }
        expandSection("section_header_Layout")
        composeTestRule.onNodeWithTag("unit_symbols_switch").performScrollTo()

        composeTestRule.onScreenshotRoot().captureRoboImage(screenshotPath("unit-symbols"))

        composeTestRule.onNodeWithTag("unit_symbols_switch").invokeOnClick()
        composeTestRule.waitForIdle()
        assertFalse(MetronomeEngine.unitSymbolsEnabled.value)
    }
}
