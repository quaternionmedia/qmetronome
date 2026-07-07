package media.quaternion.qmetronome.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * A single global slot for "where is the simulated finger right now" - test-only, not part of the
 * shipped app (real device screenshots/recordings don't show a touch point either, unless "Show
 * taps" is enabled in developer options - this is that same idea, for the same illustrative
 * reason). `*VideoTest.kt` files write to it directly (via [at]/[moveBy]/[clear]) alongside each
 * `performTouchInput` call - see `BpmDragVideoTest.kt` for the pattern - since Roborazzi's own
 * frame capture has no idea where a synthetic touch event's coordinates are, only the test does.
 *
 * A plain object (process-wide, like `MetronomeEngine`) rather than something threaded through
 * each test's own composition - every video test already renders through the one shared
 * [TouchIndicatorOverlay], so a single global slot is simplest and needs no extra plumbing.
 * `@Before`/`@After` in each video test should [clear] it, the same reset discipline
 * `MetronomeEngine.resetForTesting()` already established, so a position can't leak between tests.
 */
object TouchIndicator {
    var position by mutableStateOf<Offset?>(null)
        private set

    /** Call with a node's own `fetchSemanticsNode().boundsInRoot.center` right before its first
     * `down()` - that's the same root-coordinate space [TouchIndicatorOverlay]'s `Canvas` draws
     * in, since both sit in the same composition [ComposeTestSupport.setThemedContent] builds. */
    fun at(position: Offset) {
        this.position = position
    }

    /** Call with the *same* `Offset` passed to that step's `moveBy(...)` - `performTouchInput`'s
     * own `moveBy` is a relative delta, so the indicator accumulates identically alongside it. */
    fun moveBy(delta: Offset) {
        position = (position ?: Offset.Zero) + delta
    }

    fun clear() {
        position = null
    }
}

/** Wraps [content] with a visible dot+ring at [TouchIndicator.position] drawn on top of it -
 * everything a `*VideoTest` needs to make its recorded gesture's touch point visible in the GIF,
 * in one call around `MainScreen()`/etc. */
@Composable
fun TouchIndicatorOverlay(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        val position = TouchIndicator.position
        if (position != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(color = TOUCH_INDICATOR_COLOR.copy(alpha = 0.35f), radius = TOUCH_INDICATOR_RADIUS, center = position)
                drawCircle(
                    color = TOUCH_INDICATOR_COLOR,
                    radius = TOUCH_INDICATOR_RADIUS,
                    center = position,
                    style = Stroke(width = TOUCH_INDICATOR_STROKE),
                )
            }
        }
    }
}

private val TOUCH_INDICATOR_COLOR = Color(0xFFFFCC00)
private const val TOUCH_INDICATOR_RADIUS = 42f
private const val TOUCH_INDICATOR_STROKE = 5f
