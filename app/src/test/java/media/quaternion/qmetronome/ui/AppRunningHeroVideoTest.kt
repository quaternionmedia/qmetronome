package media.quaternion.qmetronome.ui

import androidx.compose.ui.test.junit4.createComposeRule
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

/**
 * Not a [media.quaternion.qmetronome.tutorial.TutorialTopic] - this isn't a gesture, it's the
 * plain "app running, nothing being touched" hero shot used by the top-level docs (README and
 * friends) to show what qMetronome actually looks like at rest: default visualizer
 * (`VisualizerRegistry.default`, the traditional triangle-metronome), default 120 BPM, playing, no
 * [TouchIndicatorOverlay] since nothing is being demonstrated here. No corresponding
 * `*ScreenshotTest` either, for the same reason the topic video tests don't duplicate their
 * screenshot - a still frame of this would just be "the app," which the docs already show plenty
 * of elsewhere.
 *
 * `settleTimeoutMillis = 0` for the same reason as [PreviewDoubleTapVideoTest]: playback is
 * genuinely running for the whole recording, so the default 3s post-block settle window would
 * keep capturing real-time frames well past what this test intends and risk the same
 * heap-exhaustion this project hit once already.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = FULLSCREEN_QUALIFIERS)
class AppRunningHeroVideoTest {

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

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun `record the default visualizer running at the default tempo`() {
        MetronomeEngine.markBpmHintShown()
        composeTestRule.setThemedContent {
            MainScreen(onActivateToy = {})
        }

        composeTestRule.onScreenshotRoot().recordRoboVideo(
            composeRule = composeTestRule,
            filePath = videoPath("app-running"),
            videoOptions = RoboVideoOptions(fps = 10, settleTimeoutMillis = 0),
        ) {
            MetronomeEngine.toggle()
            delay(2000)
        }
    }
}
