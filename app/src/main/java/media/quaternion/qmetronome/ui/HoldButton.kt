package media.quaternion.qmetronome.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

/**
 * A button that signals "held" while the user's finger is down. [onHoldChanged] fires with
 * true on press-down and false on release, letting the caller queue BPM changes while held
 * and flush them to the engine on release. No repeat firing — this is a shift-key.
 */
@Composable
fun HoldButton(isHeld: Boolean, onHoldChanged: (Boolean) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) { onHoldChanged(isPressed) }

    OutlinedButton(
        onClick = {},
        interactionSource = interactionSource,
        colors = if (isHeld) {
            ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            )
        } else {
            ButtonDefaults.outlinedButtonColors()
        },
    ) {
        Text("HOLD")
    }
}
