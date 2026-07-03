package media.quaternion.qmetronome.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/**
 * A time signature is a "1x2 matrix" - numerator (beats) over denominator (note value) - edited
 * as two independent number fields rather than one combined dialog like [NumericEntryDialog],
 * since the two halves have entirely different valid ranges and meanings (the numerator drives
 * actual beat counting; the denominator, [MetronomeEngine.setUnitNoteValue], is presentational
 * only today). Confirming applies both at once.
 */
@Composable
fun TimeSignatureEntryDialog(
    initialBeatCount: Int,
    beatCountRange: IntRange,
    initialUnitNoteValue: Int,
    unitNoteValueRange: IntRange,
    onConfirm: (beatCount: Int, unitNoteValue: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var beatCountField by remember {
        val text = initialBeatCount.toString()
        mutableStateOf(TextFieldValue(text, selection = TextRange(0, text.length)))
    }
    var unitNoteValueField by remember { mutableStateOf(TextFieldValue(initialUnitNoteValue.toString())) }

    val beatCount = beatCountField.text.toIntOrNull()
    val unitNoteValue = unitNoteValueField.text.toIntOrNull()
    val isBeatCountValid = beatCount != null && beatCount in beatCountRange
    val isUnitNoteValueValid = unitNoteValue != null && unitNoteValue in unitNoteValueRange
    val isValid = isBeatCountValid && isUnitNoteValueValid

    fun confirm() {
        if (isValid) onConfirm(beatCount!!, unitNoteValue!!)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set time signature") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = beatCountField,
                    onValueChange = { beatCountField = it },
                    label = { Text("Beats (${beatCountRange.first}-${beatCountRange.last})") },
                    isError = beatCountField.text.isNotEmpty() && !isBeatCountValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                )
                Text("/", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(
                    value = unitNoteValueField,
                    onValueChange = { unitNoteValueField = it },
                    label = { Text("Note value (${unitNoteValueRange.first}-${unitNoteValueRange.last})") },
                    isError = unitNoteValueField.text.isNotEmpty() && !isUnitNoteValueValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    modifier = Modifier.weight(1f),
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

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}
