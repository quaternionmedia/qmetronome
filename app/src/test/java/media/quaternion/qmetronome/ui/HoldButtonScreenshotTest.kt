package media.quaternion.qmetronome.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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

/**
 * [HoldButton]'s own press/latch state machine, driven by a real held-down gesture (split across
 * separate `down()`/`up()` calls with [ComposeTestRule.mainClock] advanced manually in between -
 * the documented pattern for testing long-press timing without racing a real delay). Once HOLD is
 * genuinely down, staging itself is exercised via direct [MetronomeEngine.setBpm] calls rather
 * than a second, simultaneous drag gesture - `setBpm` is what actually checks `holdMode` and
 * stages instead of applies (see its kdoc), the identical path a real BPM drag goes through, and
 * this test's own gesture focus is HOLD's state machine, not re-driving the drag gesture
 * [BpmDragScreenshotTest] already covers.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = FULLSCREEN_QUALIFIERS)
class HoldButtonScreenshotTest {

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

    // autoAdvance is set to false here, after setContent() rather than before it (e.g. in
    // @Before) - setting it before the first composition exists doesn't reliably stick, letting
    // the clock silently auto-advance straight through HOLD's 600ms long-press delay the moment
    // any later action (like performTouchInput) triggers its own idle sync, collapsing every
    // "catch it mid-press" assertion straight to the post-promotion Latched state.
    private fun setContent() {
        MetronomeEngine.markBpmHintShown()
        composeTestRule.setThemedContent {
            MainScreen(onActivateToy = {})
        }
        composeTestRule.mainClock.autoAdvance = false
    }

    @Test
    fun `momentary hold stages tempo changes, applying them only on release`() {
        MetronomeEngine.setBpm(120f)
        setContent()
        // Unlike the sticky-latch test below, this one never touches the button's own press
        // gesture, so there's no 600ms delay to race against here - autoAdvance can stay on
        // (setContent() turns it off by default) so waitForIdle() actually produces a real
        // drawn frame reflecting each direct engine mutation, rather than leaving the screenshot
        // showing whatever the very first composed frame looked like.
        composeTestRule.mainClock.autoAdvance = true

        // beginHold()/endHold() driven directly rather than through a real press: HOLD's
        // LaunchedEffect(isPressed) races its own 600ms promotion delay the instant ANY test
        // synchronization point runs (this environment's coroutine delay isn't gated by
        // mainClock - advancing it by even 16ms was observed to drain the full 600ms delay
        // regardless, making "advance by less than the threshold" unworkable here). The sticky-
        // latch test below - which only ever needs to land *past* the threshold, never inside
        // it - is what verifies the real button actually drives this same state machine;this
        // test's own focus is staging/flush correctness once Momentary is reached, however it
        // got there.
        MetronomeEngine.beginHold()
        composeTestRule.waitForIdle()
        assertEquals(MetronomeEngine.HoldMode.Momentary, MetronomeEngine.holdMode.value)

        MetronomeEngine.setBpm(140f)
        composeTestRule.waitForIdle()
        assertEquals(120f, MetronomeEngine.state.value.bpm, 0.001f)
        assertEquals(140f, MetronomeEngine.stagedBpm.value)

        composeTestRule.onScreenshotRoot().captureRoboImage(screenshotPath("hold-momentary-staging"))

        MetronomeEngine.endHold()
        composeTestRule.waitForIdle()
        assertEquals(MetronomeEngine.HoldMode.Off, MetronomeEngine.holdMode.value)
        assertEquals(140f, MetronomeEngine.state.value.bpm, 0.001f)
    }

    @Test
    fun `holding past the long-press threshold latches sticky, surviving release until a later tap`() {
        MetronomeEngine.setBpm(120f)
        setContent()

        val holdButton = composeTestRule.onNodeWithTag("hold_button")
        holdButton.performTouchInput { down(center) }
        composeTestRule.mainClock.advanceTimeBy(700) // past LATCH_LONG_PRESS_MS - auto-promotes to Latched
        assertEquals(MetronomeEngine.HoldMode.Latched, MetronomeEngine.holdMode.value)

        MetronomeEngine.setBpm(150f)
        composeTestRule.mainClock.advanceTimeBy(16)

        composeTestRule.onScreenshotRoot().captureRoboImage(screenshotPath("hold-sticky-latch"))

        // Releasing the very press that caused the promotion is a no-op - the latch stays sticky.
        holdButton.performTouchInput { up() }
        composeTestRule.mainClock.advanceTimeBy(100)
        assertEquals(MetronomeEngine.HoldMode.Latched, MetronomeEngine.holdMode.value)
        assertEquals(120f, MetronomeEngine.state.value.bpm, 0.001f)

        // A separate, later tap on HOLD unlatches and flushes.
        holdButton.performTouchInput { down(center) }
        composeTestRule.mainClock.advanceTimeBy(50)
        holdButton.performTouchInput { up() }
        composeTestRule.mainClock.advanceTimeBy(100)
        assertEquals(MetronomeEngine.HoldMode.Off, MetronomeEngine.holdMode.value)
        assertEquals(150f, MetronomeEngine.state.value.bpm, 0.001f)
    }
}
