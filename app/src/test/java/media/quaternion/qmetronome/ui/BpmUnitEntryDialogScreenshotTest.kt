package media.quaternion.qmetronome.ui

import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import media.quaternion.qmetronome.ui.theme.QMetronomeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [BpmUnitEntryDialog] renders through Compose's own [androidx.compose.material3.AlertDialog],
 * which draws into a separate window rather than as a child of whatever composable created it -
 * unlike every other screenshot test in this suite, this one captures via [isDialog] rather than
 * [ComposeTestSupport.onScreenshotRoot] (an empty node here, since nothing else shares its window),
 * and doesn't need [ComposeTestSupport.setThemedContent]'s [androidx.compose.material3.Surface]
 * wrapper either - Material's own `AlertDialog` already provides one internally.
 *
 * Deliberately constructs the dialog directly (no `@Config(qualifiers = FULLSCREEN_QUALIFIERS)`,
 * unlike this suite's other topics) rather than opening it via a real long-press on the full
 * [MainScreen]: a real Android dialog is never actually "fullscreen" anyway (a centered floating
 * box over whatever's behind it, so there's no full-device-screen version of this one to render),
 * and - confirmed empirically, not a guess - hosting it inside [MainScreen] at
 * [FULLSCREEN_QUALIFIERS]'s resolution makes the text field's autofocus genuinely succeed, where
 * at Robolectric's own default it throws (caught in `BpmUnitEntryDialog.kt`) and never actually
 * focuses. A *real* focused `BasicTextField`'s cursor blink is a legitimately-infinite
 * `withFrameNanos` loop that Compose's test idle-detection can never settle around, hanging every
 * later query/action on an `AppNotIdleException`. Staying on the default (smaller, focus-
 * incapable) window sidesteps the whole problem.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class BpmUnitEntryDialogScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `opens on the natural unit, switching units resets to a sensible default, confirm converts back to bpm`() {
        var confirmedBpm: Float? = null
        composeTestRule.setContent {
            QMetronomeTheme {
                BpmUnitEntryDialog(
                    initialBpm = 120f,
                    onConfirm = { confirmedBpm = it },
                    onDismiss = {},
                )
            }
        }

        // 120 BPM's natural unit is BPM itself - opens showing its own actual value, not a
        // placeholder default.
        composeTestRule.onNodeWithText("120").assertExists()

        composeTestRule.onNode(isDialog()).captureRoboImage(screenshotPath("bpm-unit-entry-dialog"))

        // Switching to BPH doesn't arithmetically convert 120 BPM into BPH terms (7200) - it
        // resets to bpmDefaultUnitValue(BPH), a sensible starting point in the new unit instead.
        composeTestRule.onNodeWithText("BPH").performClick()
        composeTestRule.onNodeWithText("30").assertExists()

        // Confirming from BPH converts back to raw bpm correctly (30 BPH == 0.5 BPM).
        composeTestRule.onNodeWithText("Set").performClick()
        assertEquals(0.5f, confirmedBpm!!, 0.001f)
    }
}
