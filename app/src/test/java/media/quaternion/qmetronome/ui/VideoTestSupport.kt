package media.quaternion.qmetronome.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoboVideoRecorderScope

/**
 * Drags [this] node by [steps] increments of [stepOffset], keeping [TouchIndicator] in sync so
 * the recorded video shows where the simulated finger actually is - the same down/moveBy/up shape
 * every `*ScreenshotTest`'s drag already uses, just spread across [RoboVideoRecorderScope.delay]
 * calls instead of one uninterrupted gesture, so `recordRoboVideo` actually gets a frame per step.
 */
@OptIn(ExperimentalRoborazziApi::class)
fun RoboVideoRecorderScope.dragWithIndicator(
    node: SemanticsNodeInteraction,
    steps: Int,
    stepOffset: Offset,
    stepDelayMs: Long = 100,
) {
    TouchIndicator.at(node.fetchSemanticsNode().boundsInRoot.center)
    node.performTouchInput { down(center) }
    delay(stepDelayMs)
    repeat(steps) {
        node.performTouchInput { moveBy(stepOffset) }
        TouchIndicator.moveBy(stepOffset)
        delay(stepDelayMs)
    }
    node.performTouchInput { up() }
    TouchIndicator.clear()
}

/** Presses and holds [this] node in place (no movement) for [holdMs], keeping [TouchIndicator]
 * visible at its center the whole time - for gestures like HOLD's long-press-to-latch where the
 * finger doesn't move but timing still matters. */
@OptIn(ExperimentalRoborazziApi::class)
fun RoboVideoRecorderScope.pressAndHoldWithIndicator(
    node: SemanticsNodeInteraction,
    holdMs: Long,
    stepDelayMs: Long = 100,
) {
    TouchIndicator.at(node.fetchSemanticsNode().boundsInRoot.center)
    node.performTouchInput { down(center) }
    var remaining = holdMs
    while (remaining > 0) {
        delay(stepDelayMs)
        remaining -= stepDelayMs
    }
    node.performTouchInput { up() }
    TouchIndicator.clear()
}

/** A quick, un-held tap - down then up shortly after - for e.g. "a later tap unlatches" steps. */
@OptIn(ExperimentalRoborazziApi::class)
fun RoboVideoRecorderScope.tapWithIndicator(node: SemanticsNodeInteraction, holdMs: Long = 100) {
    TouchIndicator.at(node.fetchSemanticsNode().boundsInRoot.center)
    node.performTouchInput { down(center) }
    delay(holdMs)
    node.performTouchInput { up() }
    TouchIndicator.clear()
}

/** A double-tap, for gestures like the preview's play/stop toggle. */
@OptIn(ExperimentalRoborazziApi::class)
fun RoboVideoRecorderScope.doubleTapWithIndicator(node: SemanticsNodeInteraction, holdMs: Long = 150) {
    TouchIndicator.at(node.fetchSemanticsNode().boundsInRoot.center)
    node.performTouchInput { doubleClick(center) }
    delay(holdMs)
    TouchIndicator.clear()
}

/** [SemanticsNodeInteraction.invokeOnClick] (not a real touch) with the indicator shown at the
 * node's position anyway - for controls inside [SettingsSheet] where a real `performTouchInput`
 * doesn't reliably reach the callback (see `invokeOnClick`'s own kdoc in `ComposeTestSupport.kt`)
 * but the video should still show *where* that tap logically happened. */
@OptIn(ExperimentalRoborazziApi::class)
fun RoboVideoRecorderScope.invokeClickWithIndicator(node: SemanticsNodeInteraction, holdMs: Long = 150) {
    TouchIndicator.at(node.fetchSemanticsNode().boundsInRoot.center)
    node.invokeOnClick()
    delay(holdMs)
    TouchIndicator.clear()
}
