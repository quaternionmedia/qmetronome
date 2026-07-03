package media.quaternion.qmetronome.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

/**
 * An icon button that fires [onStep] once immediately on press, then keeps firing on a
 * geometrically-shrinking interval for as long as it's held - a quick tap nudges by one step,
 * holding it down ramps up to many steps a second. All repeat behavior lives in this
 * press-state effect; the button's own `onClick` is intentionally a no-op, otherwise a quick tap
 * would double-count (once from the press-down firing, once from the click callback).
 *
 * Renders as a plain icon with no filled circle behind it - the same minimal
 * [Box]-plus-[clickable] language as the bar-queue row's icon buttons, rather than a Material
 * [androidx.compose.material3.FilledIconButton], so BPM/beats-per-bar's steppers read as the same
 * control family as the queue's.
 *
 * [onStep] is read through [rememberUpdatedState] rather than captured directly - the repeat
 * loop below is a single long-running coroutine keyed on [isPressed], which does *not* restart
 * on every recomposition, so without this indirection it would keep calling whatever [onStep]
 * closure existed at the moment the press started for the entire duration of the hold. That
 * silently caps a hold-to-repeat gesture at a single step if [onStep] closes over a value that
 * only updates via recomposition (e.g. a `StateFlow`-derived display value) instead of reading
 * live state at call time - prefer having [onStep] read authoritative state fresh each call.
 */
@Composable
fun HoldRepeatButton(
    onStep: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val currentOnStep by rememberUpdatedState(onStep)

    LaunchedEffect(isPressed) {
        if (!isPressed) return@LaunchedEffect
        currentOnStep()
        delay(INITIAL_DELAY_MS)
        var intervalMs = FIRST_REPEAT_MS
        while (isPressed) {
            currentOnStep()
            delay(intervalMs)
            intervalMs = (intervalMs * ACCELERATION).toLong().coerceAtLeast(MIN_INTERVAL_MS)
        }
    }

    Box(
        modifier = modifier.clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            onClick = {},
        ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private const val INITIAL_DELAY_MS = 450L
private const val FIRST_REPEAT_MS = 220L
private const val MIN_INTERVAL_MS = 30L
private const val ACCELERATION = 0.80f
