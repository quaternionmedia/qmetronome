package media.quaternion.qmetronome.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The app's standard slider: a [Slider] flanked by press-and-hold-to-repeat +/- steppers
 * ([HoldRepeatButton]), with the value label doubling as two more affordances - long-press opens
 * [NumericEntryDialog] to type an exact value, double-tap resets to [defaultValue]. Long-press is
 * already claimed by numeric entry, so the reset gesture is a different one on the same label
 * (mirroring the double-tap-on-preview-toggles-playback precedent elsewhere in this app) rather
 * than adding a second visible control.
 *
 * [currentValue] defaults to closing over [value] itself, which is enough for a caller whose
 * state updates recompose fast relative to [HoldRepeatButton]'s repeat interval - but a hold can
 * fire several steps before recomposition catches up, and every one of those calls would then
 * see the same stale [value] and collapse into a single effective step (the same bug BPM/
 * beats-per-bar hit before their call sites started reading the backing `StateFlow` fresh at call
 * time). Callers backed by a `StateFlow` should pass `currentValue = { theFlow.value }` instead.
 */
@Composable
fun SteppedSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    defaultValue: Float,
    dialogTitle: String,
    modifier: Modifier = Modifier,
    currentValue: () -> Float = { value },
    valueLabel: (Float) -> String = { it.roundToInt().toString() },
    dialogValueLabel: (Float) -> String = valueLabel,
    dialogParse: (String) -> Float? = { it.toFloatOrNull() },
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        HoldRepeatButton(
            onStep = { onValueChange((currentValue() - step).coerceIn(valueRange)) },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease")
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = valueLabel(value),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { showDialog = true },
                        onDoubleTap = { onValueChange(defaultValue.coerceIn(valueRange)) },
                    )
                },
            )
        }
        HoldRepeatButton(
            onStep = { onValueChange((currentValue() + step).coerceIn(valueRange)) },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Increase")
        }
    }

    if (showDialog) {
        NumericEntryDialog(
            title = dialogTitle,
            initialValue = value,
            valueRange = valueRange,
            format = dialogValueLabel,
            parse = dialogParse,
            onConfirm = {
                onValueChange(it.coerceIn(valueRange))
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
}
