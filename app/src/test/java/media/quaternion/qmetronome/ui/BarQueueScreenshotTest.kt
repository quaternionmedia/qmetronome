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

/** Covers both [BeatsPerBarControls]' bar-queue row: building/trimming the queue itself, and
 * separately cycling its advance mode - two different topics/screenshots from one composable. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = FULLSCREEN_QUALIFIERS)
class BarQueueScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        MetronomeEngine.resetForTesting()
        MetronomeEngine.attach(RuntimeEnvironment.getApplication())
        MetronomeEngine.resetQueueToDefault()
    }

    @After
    fun tearDown() {
        MetronomeEngine.resetForTesting()
    }

    @Test
    fun `add bars to the queue, tap to jump, long-press to remove`() {
        composeTestRule.setThemedContent { MainScreen(onActivateToy = {}) }

        val addButton = composeTestRule.onNodeWithTag("queue_add_button")
        addButton.performClick()
        composeTestRule.waitForIdle()
        addButton.performClick()
        composeTestRule.waitForIdle()
        assertEquals(3, MetronomeEngine.timeSignatureQueue.value.size)

        composeTestRule.onScreenshotRoot().captureRoboImage(screenshotPath("bar-queue-management"))

        // Tap the first bar to jump to it.
        composeTestRule.onNodeWithTag("queue_bar_0").performTouchInput { click(center) }
        composeTestRule.waitForIdle()
        assertEquals(0, MetronomeEngine.queueIndex.value)

        // Long-press the last bar to remove it.
        composeTestRule.onNodeWithTag("queue_bar_2").performTouchInput { longClick(center) }
        composeTestRule.waitForIdle()
        assertEquals(2, MetronomeEngine.timeSignatureQueue.value.size)
    }

    @Test
    fun `tapping the queue mode icon cycles Loop, Once, Manual`() {
        composeTestRule.setThemedContent { MainScreen(onActivateToy = {}) }
        assertEquals(MetronomeEngine.QueueMode.LOOP, MetronomeEngine.queueMode.value)

        composeTestRule.onScreenshotRoot().captureRoboImage(screenshotPath("bar-queue-mode-cycling"))

        val modeButton = composeTestRule.onNodeWithTag("queue_mode_button")
        modeButton.performClick()
        composeTestRule.waitForIdle()
        assertEquals(MetronomeEngine.QueueMode.ONCE, MetronomeEngine.queueMode.value)

        modeButton.performClick()
        composeTestRule.waitForIdle()
        assertEquals(MetronomeEngine.QueueMode.MANUAL, MetronomeEngine.queueMode.value)

        modeButton.performClick()
        composeTestRule.waitForIdle()
        assertEquals(MetronomeEngine.QueueMode.LOOP, MetronomeEngine.queueMode.value)
    }

    @Test
    fun `reset and remove buttons only appear while HOLD is active`() {
        composeTestRule.setThemedContent { MainScreen(onActivateToy = {}) }

        composeTestRule.onNodeWithTag("queue_reset_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("queue_remove_button").assertDoesNotExist()

        MetronomeEngine.beginHold()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("queue_reset_button").assertExists()
        composeTestRule.onNodeWithTag("queue_remove_button").assertExists()

        MetronomeEngine.endHold()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("queue_reset_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("queue_remove_button").assertDoesNotExist()
    }
}
