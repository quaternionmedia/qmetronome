package media.quaternion.qmetronome.ui

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import kotlin.math.roundToInt

/**
 * A "long-press the number to type an exact value" dialog, shared by every stepper/slider
 * control in the app (originally BPM-only; see [SteppedSlider] for the other adopter) so the
 * long-press-to-type affordance behaves identically everywhere.
 */
@Composable
fun NumericEntryDialog(
    title: String,
    initialValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onConfirm: (Float) -> Unit,
    onDismiss: () -> Unit,
    format: (Float) -> String = { it.roundToInt().toString() },
    parse: (String) -> Float? = { it.toFloatOrNull() },
) {
    val focusRequester = remember { FocusRequester() }
    val initialText = format(initialValue)
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(initialText, selection = TextRange(0, initialText.length)))
    }
    val parsed = parse(fieldValue.text)
    val isValid = parsed != null && parsed >= valueRange.start && parsed <= valueRange.endInclusive

    fun confirm() {
        if (isValid) onConfirm(parsed!!)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = fieldValue,
                onValueChange = { fieldValue = it },
                label = { Text("${format(valueRange.start)}–${format(valueRange.endInclusive)}") },
                isError = fieldValue.text.isNotEmpty() && !isValid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
                modifier = Modifier.focusRequester(focusRequester),
            )
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
