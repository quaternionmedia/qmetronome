package media.quaternion.qmetronome.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * [BpmControls]' long-press-to-type dialog - a unit-aware alternative to plugging BPM straight
 * into the generic [NumericEntryDialog], since typing "in BPH" or "in BPS" needs converting to/
 * from raw bpm, which [NumericEntryDialog] has no concept of. [BpmUnit] chips let you pick which
 * unit you're reading/typing in *before* landing on a number - switching chips is the "convert
 * between BPH/BPM/BPS" affordance itself, not a separate gesture.
 *
 * Opens on whichever unit the current [initialBpm] already reads as ([bpmUnitFor]), showing its
 * *actual* value converted into that unit - not a placeholder. Switching to a *different* unit,
 * though, resets to [bpmDefaultUnitValue] rather than arithmetically converting the old field's
 * number: converting e.g. 120 BPM into raw BPH terms gives 7200 BPH, a real conversion but not
 * remotely what picking "BPH" is trying to reach - a sensible starting point in the new unit is
 * more useful than a technically-correct but nonsensical-looking one.
 */
@Composable
fun BpmUnitEntryDialog(
    initialBpm: Float,
    onConfirm: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val naturalUnit = remember(initialBpm) { bpmUnitFor(initialBpm) }
    var unit by remember { mutableStateOf(naturalUnit) }
    val focusRequester = remember { FocusRequester() }

    var fieldValue by remember(unit) {
        val startingValue = if (unit == naturalUnit) bpmToUnitValue(initialBpm, unit) else bpmDefaultUnitValue(unit)
        val text = String.format(java.util.Locale.ROOT, "%.2f", startingValue).trimEnd('0').trimEnd('.')
        mutableStateOf(TextFieldValue(text, selection = TextRange(0, text.length)))
    }

    val range = bpmRangeFor(unit)
    val typedValue = fieldValue.text.toFloatOrNull()
    val isValid = typedValue != null && typedValue in range

    fun confirm() {
        if (isValid) onConfirm(bpmFromUnitValue(typedValue!!, unit))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set tempo") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BpmUnit.entries.forEach { candidate ->
                        FilterChip(
                            selected = unit == candidate,
                            onClick = { unit = candidate },
                            label = { Text(candidate.label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = fieldValue,
                    onValueChange = { fieldValue = it },
                    label = {
                        Text(
                            "${formatRangeBound(range.start)}–${formatRangeBound(range.endInclusive)} ${unit.label}",
                        )
                    },
                    isError = fieldValue.text.isNotEmpty() && !isValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    modifier = Modifier.focusRequester(focusRequester),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = ::confirm, enabled = isValid) { Text("Set") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )

    // Auto-focus is a convenience (skip a manual tap before typing), not a requirement - the
    // field is still fully usable either way, so a request made before the text field's own
    // focus target has actually attached (observed in Robolectric-hosted Compose UI tests, where
    // a dialog's content can be a composition frame behind the effect that requests focus for it)
    // fails silently rather than crashing the whole dialog.
    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }
}

/** Whole numbers display without a decimal tail (e.g. "1" not "1.00"); anything else keeps 2
 * decimal places - matches [bpmDisplayValue]'s own precision for BPH/BPS bounds. */
private fun formatRangeBound(value: Float): String {
    return if (value == value.roundToInt().toFloat()) {
        value.roundToInt().toString()
    } else {
        String.format(java.util.Locale.ROOT, "%.2f", value)
    }
}
