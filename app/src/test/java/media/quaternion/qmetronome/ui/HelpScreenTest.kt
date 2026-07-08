package media.quaternion.qmetronome.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import media.quaternion.qmetronome.engine.MetronomeEngine
import media.quaternion.qmetronome.tutorial.TutorialTopics
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A smoke test, not a per-gesture one (those are covered by the individual composables'
 * `*ScreenshotTest`s already, and [HelpScreen] just embeds them) - confirms every
 * [TutorialTopics] category actually renders (title + at least its first topic's own title) and
 * that closing works, so a future category with no matching `when` branch or a topic silently
 * dropped from the screen would fail here. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = FULLSCREEN_QUALIFIERS)
class HelpScreenTest {

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
    fun `every category and topic renders, close dismisses`() {
        var dismissed = false
        composeTestRule.setThemedContent {
            HelpScreen(onDismiss = { dismissed = true })
        }

        composeTestRule.onNodeWithText("Help").assertExists()
        TutorialTopics.all.groupBy { it.category }.forEach { (category, topics) ->
            composeTestRule.onNodeWithText(category.displayName).assertExists()
            composeTestRule.onNodeWithText(topics.first().title).assertExists()
        }

        composeTestRule.onNodeWithContentDescription("Close help").performClick()
        composeTestRule.waitForIdle()
        assertTrue(dismissed)
    }
}
