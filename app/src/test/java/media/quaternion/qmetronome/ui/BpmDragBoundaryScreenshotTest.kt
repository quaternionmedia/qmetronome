package media.quaternion.qmetronome.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import media.quaternion.qmetronome.engine.MetronomeEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Covers the tempo scrub crossing the BPM=1 boundary into beats-per-hour and back - the extended
 * end of [BpmDragScreenshotTest]'s ordinary-range coverage, using the same drag gesture but with
 * enough steps and a low enough starting point to actually cross it, in both directions.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = FULLSCREEN_QUALIFIERS)
class BpmDragBoundaryScreenshotTest {

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
    fun `dragging left crosses from BPM into BPH, dragging right crosses back`() {
        MetronomeEngine.setExtendedBpmRangeEnabled(true)
        MetronomeEngine.setBpm(3f)
        MetronomeEngine.markBpmHintShown()
        composeTestRule.setThemedContent {
            MainScreen(onActivateToy = {})
        }

        composeTestRule.onScreenshotRoot().captureRoboImage(screenshotPath("bpm-drag-scrub-boundary"))

        // Offsets are 3x what they'd be at 1x density - FULLSCREEN_QUALIFIERS renders at xxhdpi
        // (3.0 scale), and the drag handler converts its own 6dp-per-step sensitivity using the
        // *same* density, so a fixed pixel distance now covers a third as many steps unless scaled
        // up to match.
        val bpmNumber = composeTestRule.onNodeWithTag("bpm_number")
        bpmNumber.performTouchInput {
            down(center)
            repeat(30) {
                moveBy(Offset(-30f, 0f))
                advanceEventTime(16)
            }
            up()
        }
        composeTestRule.waitForIdle()
        val afterLeft = MetronomeEngine.state.value.bpm
        assertTrue(
            "expected dragging left far enough from 3 BPM to cross under 1 BPM into BPH territory, got $afterLeft",
            afterLeft < MetronomeEngine.MIN_BPM,
        )
        assertEquals("BPH", bpmDisplayUnit(afterLeft))

        // Recovering back across the boundary needs more distance than crossing it did: the
        // multiplicative step below 1 BPM shrinks in *absolute* terms as the value gets smaller,
        // so the same step count that dove down doesn't symmetrically climb back up from
        // whatever tiny floor it landed on - hence the larger per-move offset here.
        bpmNumber.performTouchInput {
            down(center)
            repeat(30) {
                moveBy(Offset(60f, 0f))
                advanceEventTime(16)
            }
            up()
        }
        composeTestRule.waitForIdle()
        val afterRight = MetronomeEngine.state.value.bpm
        assertTrue(
            "expected dragging back right to return to at least 1 BPM, got $afterRight",
            afterRight >= MetronomeEngine.MIN_BPM,
        )
        assertEquals("BPM", bpmDisplayUnit(afterRight))
    }
}
