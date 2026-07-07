package media.quaternion.qmetronome.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.delay
import media.quaternion.qmetronome.engine.MetronomeEngine
import media.quaternion.qmetronome.ui.theme.RecordingRed

/**
 * Drives [MetronomeEngine]'s hold/latch state machine directly off this button's own
 * [MutableInteractionSource] - no separate `pointerInput`/`detectTapGestures` is layered on top,
 * which would otherwise compete with the button's own press handling the way multi-gesture
 * surfaces elsewhere in this app (see the comment on `PreviewBox`) have to work around.
 *
 * - Press down: [MetronomeEngine.beginHold] (a no-op if already latched).
 * - Held past [LATCH_LONG_PRESS_MS] while still down: promotes to a sticky latch.
 * - A press-down within [DOUBLE_TAP_WINDOW_MS] of the previous release, while not currently
 *   staging anything: promotes straight to a sticky latch (double-tap).
 * - Release: flushes a plain momentary hold, or - if this press is a *separate* tap against an
 *   already-latched state (i.e. it didn't itself just cause the promotion) - unlatches and
 *   flushes. Releasing the same press that just latched is a no-op, so a long-press or the
 *   second tap of a double-tap doesn't immediately undo its own latch.
 */
@Composable
fun HoldButton(modifier: Modifier = Modifier) {
    val holdMode by MetronomeEngine.holdMode.collectAsState()
    val symbolicControlsEnabled by MetronomeEngine.symbolicControlsEnabled.collectAsState()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    var promotedThisPress by remember { mutableStateOf(false) }
    var lastReleaseAtNanos by remember { mutableStateOf(0L) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            val sinceLastReleaseMs = (System.nanoTime() - lastReleaseAtNanos) / 1_000_000
            val isDoubleTap = MetronomeEngine.holdMode.value == MetronomeEngine.HoldMode.Off &&
                lastReleaseAtNanos != 0L && sinceLastReleaseMs < DOUBLE_TAP_WINDOW_MS
            if (isDoubleTap) {
                MetronomeEngine.toggleLatch()
                promotedThisPress = true
            } else {
                promotedThisPress = false
                MetronomeEngine.beginHold()
                delay(LATCH_LONG_PRESS_MS)
                if (MetronomeEngine.holdMode.value == MetronomeEngine.HoldMode.Momentary) {
                    MetronomeEngine.toggleLatch()
                    promotedThisPress = true
                }
            }
        } else {
            lastReleaseAtNanos = System.nanoTime()
            if (MetronomeEngine.holdMode.value == MetronomeEngine.HoldMode.Latched) {
                if (!promotedThisPress) MetronomeEngine.toggleLatch()
            } else {
                MetronomeEngine.endHold()
            }
        }
    }

    OutlinedButton(
        onClick = {},
        interactionSource = interactionSource,
        modifier = modifier.testTag("hold_button"),
        colors = when (holdMode) {
            MetronomeEngine.HoldMode.Latched -> ButtonDefaults.outlinedButtonColors(
                containerColor = RecordingRed,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
            MetronomeEngine.HoldMode.Momentary -> ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            )
            MetronomeEngine.HoldMode.Off -> ButtonDefaults.outlinedButtonColors()
        },
    ) {
        if (symbolicControlsEnabled) {
            Icon(Icons.Filled.Lock, contentDescription = "Hold")
        } else {
            Text("HOLD")
        }
    }
}

private const val LATCH_LONG_PRESS_MS = 600L
private const val DOUBLE_TAP_WINDOW_MS = 350L
