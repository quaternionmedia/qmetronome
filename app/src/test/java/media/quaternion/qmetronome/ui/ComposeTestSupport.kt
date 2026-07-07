package media.quaternion.qmetronome.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import media.quaternion.qmetronome.ui.theme.QMetronomeTheme

/** Tag for the screenshot-capture target inside [ComposeContentTestRule.setThemedContent] - use
 * with `onNodeWithTag(SCREENSHOT_ROOT_TAG).captureRoboImage(...)` rather than `onRoot()`, so
 * captures crop tightly to the actual content instead of the full (mostly-black) test window. When
 * [content] is a genuinely full-screen composable (e.g. [MainScreen], [SettingsSheet],
 * [HelpScreen]) this crops to nothing at all in practice - `wrapContentSize` only shrinks around
 * content that's smaller than its available space to begin with. */
const val SCREENSHOT_ROOT_TAG = "screenshot_root"

/**
 * A `Config(qualifiers = ...)` string producing an exact 1080x2400px Robolectric test window - a
 * realistic modern-phone portrait resolution (matching the class Nothing's own devices ship in),
 * not Robolectric's own default (320x470px - confirmed by inspecting a captured PNG's own
 * dimensions - a dated, non-representative device profile). Every `*ScreenshotTest`'s `@Config`
 * should use this so a screenshot showing the whole app actually looks like a real phone screen.
 *
 * Expressed as dp+density rather than raw pixels because that's what Robolectric's qualifier
 * syntax actually takes: `xxhdpi` is 480dpi, a 3.0x scale factor off the 160dpi baseline, so
 * 360dp x 800dp -> exactly 1080px x 2400px (360*3, 800*3).
 */
const val FULLSCREEN_QUALIFIERS = "w360dp-h800dp-xxhdpi"

/**
 * Wraps [content] in this app's real theme + a [Surface], matching `MainActivity`'s exact setup -
 * every screenshot/UI test should render through this rather than a bare `QMetronomeTheme { }`,
 * since `LocalContentColor` doesn't resolve the way it does in the real app without the `Surface`
 * that normally comes from it - text/icons render invisibly (black-on-black) otherwise, not merely
 * differently styled. Found the hard way while proving out this test suite's first screenshot.
 *
 * [content] itself sits in an inner, [SCREENSHOT_ROOT_TAG]-tagged `Box` sized to
 * [wrapContentSize] - the outer `Surface` still fills the whole test window (needed for correct
 * color resolution), but capturing the *inner* box instead of `onRoot()` crops the screenshot to
 * the control itself rather than mostly empty black space around it.
 */
fun ComposeContentTestRule.setThemedContent(content: @Composable () -> Unit) {
    setContent {
        QMetronomeTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier.wrapContentSize(Alignment.TopStart).testTag(SCREENSHOT_ROOT_TAG),
                    content = { content() },
                )
            }
        }
    }
}

/** Shorthand for `onNodeWithTag(SCREENSHOT_ROOT_TAG)` - the node [setThemedContent] wraps [content]
 * in, and what every screenshot test should call `captureRoboImage()` on instead of `onRoot()`. */
fun ComposeContentTestRule.onScreenshotRoot() = onNodeWithTag(SCREENSHOT_ROOT_TAG)

/** Screenshots captured by these tests are the illustrations for `docs/user-guide.md` - see
 * `app/build.gradle.kts`'s `roborazzi { }` block for why this is a hand-built path rather than
 * relying on that DSL's `outputDir` (it only applies to auto-named captures, not an explicit
 * filename like the one this builds). */
fun screenshotPath(id: String): String = "../docs/images/generated/screenshots/$id.png"

/** The animated-GIF counterpart to [screenshotPath], for topics whose gesture is genuinely
 * motion-based (a drag, a swipe, a timed hold) - captured via `recordRoboVideo` (see
 * `*VideoTest.kt` files) rather than `captureRoboImage`, a *separate* test from the static
 * screenshot rather than combined into it: the two capture mechanisms don't compose cleanly in
 * one test (recordRoboVideo's own frame-by-frame clock control conflicts with a plain
 * `captureRoboImage` call's expectations), and keeping them apart also means a change to one
 * doesn't risk silently breaking the other. */
fun videoPath(id: String): String = "../docs/images/generated/videos/$id.gif"

/**
 * Invokes a node's `OnClick` semantics action directly, rather than going through
 * [androidx.compose.ui.test.performClick]. Observed empirically: inside [SettingsSheet]
 * specifically (which - unlike every other composable in this test suite - keeps several
 * `collectAsState()` subscriptions live against [media.quaternion.qmetronome.midi.UsbMidiConnector],
 * whose `attach()` throws a caught `ServiceNotFoundException` under Robolectric, see every
 * screenshot test's captured stderr), `performClick()` on a deeply-nested control (a chip inside
 * an expanded [CollapsibleSection], a summary `Switch`) silently does nothing - the action is
 * present and correct (verified by fetching and calling it directly, which *does* reach the real
 * callback), but `performClick()`'s own node-fetch-and-dispatch doesn't end up invoking it. This
 * reaches the exact same callback a real tap would, just without whatever step of `performClick()`
 * is failing here - a workaround for an unresolved test-environment quirk, not a production bug.
 */
fun SemanticsNodeInteraction.invokeOnClick() {
    fetchSemanticsNode().config[SemanticsActions.OnClick].action?.invoke()
}
