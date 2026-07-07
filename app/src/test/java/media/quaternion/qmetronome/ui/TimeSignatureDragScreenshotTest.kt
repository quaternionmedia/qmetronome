package media.quaternion.qmetronome.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import media.quaternion.qmetronome.engine.MetronomeEngine
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The beats-per-bar and note-value numbers scrub the same way [BpmDragScreenshotTest]'s BPM
 * number does - same gesture shape, different (discrete, Int-backed) accumulator. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = FULLSCREEN_QUALIFIERS)
class TimeSignatureDragScreenshotTest {

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
    fun `dragging the beats-per-bar and note-value numbers scrubs them`() {
        MetronomeEngine.setBeatsPerBar(4)
        MetronomeEngine.setUnitNoteValue(4)
        composeTestRule.setThemedContent { MainScreen(onActivateToy = {}) }

        composeTestRule.onScreenshotRoot().captureRoboImage(screenshotPath("time-signature-drag-scrub"))

        // Offsets are 3x what they'd be at 1x density - see BpmDragBoundaryScreenshotTest's kdoc
        // comment on FULLSCREEN_QUALIFIERS's xxhdpi (3.0) scale.
        val beatsNumber = composeTestRule.onNodeWithTag("beats_per_bar_number")
        beatsNumber.performTouchInput {
            down(center)
            repeat(6) {
                moveBy(Offset(30f, 0f))
                advanceEventTime(16)
            }
            up()
        }
        composeTestRule.waitForIdle()
        val afterBeatsRight = MetronomeEngine.state.value.beatsPerBar
        assertTrue(
            "expected dragging right to increase beats-per-bar from 4, got $afterBeatsRight",
            afterBeatsRight > 4,
        )

        val unitNumber = composeTestRule.onNodeWithTag("unit_note_value_number")
        unitNumber.performTouchInput {
            down(center)
            repeat(6) {
                moveBy(Offset(-30f, 0f))
                advanceEventTime(16)
            }
            up()
        }
        composeTestRule.waitForIdle()
        val afterUnitLeft = MetronomeEngine.timeSignature.value.unitNoteValue
        assertTrue(
            "expected dragging left to decrease note value from 4, got $afterUnitLeft",
            afterUnitLeft < 4,
        )
    }
}
