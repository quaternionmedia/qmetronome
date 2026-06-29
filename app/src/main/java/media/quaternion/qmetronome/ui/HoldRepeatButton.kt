package media.quaternion.qmetronome.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.FilledIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

/**
 * An icon button that fires [onStep] once immediately on press, then keeps firing on a
 * geometrically-shrinking interval for as long as it's held - a quick tap nudges by one step,
 * holding it down ramps up to many steps a second. All repeat behavior lives in this
 * press-state effect; the button's own `onClick` is intentionally a no-op, otherwise a quick tap
 * would double-count (once from the press-down firing, once from the click callback).
 */
@Composable
fun HoldRepeatButton(
    onStep: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        if (!isPressed) return@LaunchedEffect
        onStep()
        delay(INITIAL_DELAY_MS)
        var intervalMs = FIRST_REPEAT_MS
        while (isPressed) {
            onStep()
            delay(intervalMs)
            intervalMs = (intervalMs * ACCELERATION).toLong().coerceAtLeast(MIN_INTERVAL_MS)
        }
    }

    FilledIconButton(
        onClick = {},
        interactionSource = interactionSource,
        modifier = modifier,
        content = content,
    )
}

private const val INITIAL_DELAY_MS = 450L
private const val FIRST_REPEAT_MS = 220L
private const val MIN_INTERVAL_MS = 30L
private const val ACCELERATION = 0.80f
