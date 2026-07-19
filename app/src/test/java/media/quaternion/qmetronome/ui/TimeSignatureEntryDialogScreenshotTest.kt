package media.quaternion.qmetronome.ui

import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import media.quaternion.qmetronome.engine.BeatAccent
import media.quaternion.qmetronome.ui.theme.QMetronomeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Same real-`AlertDialog` capture shape as [BpmUnitEntryDialogScreenshotTest] - see its own kdoc
 * for why this constructs the dialog directly at Robolectric's default (small, focus-incapable)
 * window rather than via [FULLSCREEN_QUALIFIERS], and captures via [isDialog] rather than
 * [ComposeTestSupport.onScreenshotRoot].
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class TimeSignatureEntryDialogScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `tapping a beat's chip cycles its accent tier, confirm reports the authored pattern`() {
        var confirmedAccentPattern: List<BeatAccent>? = null
        composeTestRule.setContent {
            QMetronomeTheme {
                TimeSignatureEntryDialog(
                    initialBeatCount = 4,
                    beatCountRange = 1..24,
                    initialUnitNoteValue = 4,
                    unitNoteValueRange = 1..64,
                    initialAccentPattern = emptyList(),
                    onConfirm = { _, _, accentPattern -> confirmedAccentPattern = accentPattern },
                    onDismiss = {},
                )
            }
        }

        // Beat 1 is a fixed, non-interactive "Bar" chip; beats 2-4 start unmarked, each showing
        // its own (1-indexed) number until tapped.
        composeTestRule.onNodeWithText("Bar").assertExists()
        composeTestRule.onNodeWithText("2").assertExists()

        composeTestRule.onNode(isDialog()).captureRoboImage(screenshotPath("marking-beat-accents"))

        // Tapping beat 2's chip cycles NONE -> ACCENT, relabeling it "A".
        composeTestRule.onNodeWithText("2").performClick()
        composeTestRule.onNodeWithText("A").assertExists()

        composeTestRule.onNodeWithText("Set").performClick()
        assertEquals(BeatAccent.ACCENT, confirmedAccentPattern?.get(1))
    }
}
