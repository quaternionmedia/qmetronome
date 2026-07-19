package media.quaternion.qmetronome.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import media.quaternion.qmetronome.engine.BeatAccent
import media.quaternion.qmetronome.engine.MetronomeEngine
import media.quaternion.qmetronome.ui.icons.ExtraIcons

/**
 * A time signature is a "1x2 matrix" - numerator (beats) over denominator (note value) - edited
 * as two independent number fields rather than one combined dialog like [NumericEntryDialog],
 * since the two halves have entirely different valid ranges and meanings (the numerator drives
 * actual beat counting; the denominator, [MetronomeEngine.setUnitNoteValue], is presentational
 * only today). Confirming applies both at once.
 *
 * Below the two numbers, one chip per beat authors that bar's [BeatAccent] pattern (see
 * [MetronomeEngine.setAccentPattern]) - the only place in the app this is editable, since nothing
 * else needs "which beats are accented" as input. Beat 1 is always a fixed, non-interactive "Bar"
 * chip (beat 0 is unconditionally [media.quaternion.qmetronome.engine.ClickSound.BAR] regardless
 * of any pattern - see [MetronomeEngine.beatTypeFor]); every other chip tap-cycles
 * NONE -> ACCENT -> STRONG_ACCENT -> CUSTOM -> NONE. The chip row tracks the live beat-count field
 * as it's edited, not just the value the dialog opened with, so resizing the bar and marking its
 * accents can happen in the same pass - growing pads with [BeatAccent.NONE], shrinking truncates.
 */
@Composable
fun TimeSignatureEntryDialog(
    initialBeatCount: Int,
    beatCountRange: IntRange,
    initialUnitNoteValue: Int,
    unitNoteValueRange: IntRange,
    initialAccentPattern: List<BeatAccent>,
    onConfirm: (beatCount: Int, unitNoteValue: Int, accentPattern: List<BeatAccent>) -> Unit,
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

    val accentPattern = remember {
        mutableStateListOf<BeatAccent>().apply {
            addAll((0 until initialBeatCount).map { initialAccentPattern.getOrElse(it) { BeatAccent.NONE } })
        }
    }
    // Keeps the chip row sized to whatever beat count is currently typed, not just the value the
    // dialog opened with - growing pads with NONE, shrinking truncates from the end.
    LaunchedEffect(beatCount) {
        val target = beatCount?.coerceIn(beatCountRange) ?: return@LaunchedEffect
        while (accentPattern.size < target) accentPattern.add(BeatAccent.NONE)
        while (accentPattern.size > target) accentPattern.removeAt(accentPattern.lastIndex)
    }

    fun confirm() {
        if (isValid) onConfirm(beatCount!!, unitNoteValue!!, accentPattern.toList())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set time signature") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                if (isBeatCountValid) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val unitSymbolsEnabled by MetronomeEngine.unitSymbolsEnabled.collectAsState()
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (unitSymbolsEnabled) {
                                Icon(
                                    ExtraIcons.UnitBeatType,
                                    contentDescription = null,
                                    modifier = Modifier.size(10.dp),
                                    tint = MaterialTheme.colorScheme.secondary,
                                )
                            }
                            Text(
                                text = "Accents - tap a beat to cycle",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            for (beatIndex in 0 until beatCount!!) {
                                if (beatIndex == 0) {
                                    FilterChip(
                                        selected = true,
                                        enabled = false,
                                        onClick = {},
                                        label = { Text("Bar") },
                                    )
                                } else {
                                    val accent = accentPattern.getOrElse(beatIndex) { BeatAccent.NONE }
                                    FilterChip(
                                        selected = accent != BeatAccent.NONE,
                                        onClick = {
                                            // beatCount's own recomposition can render a chip for
                                            // beatIndex before the LaunchedEffect above has grown
                                            // accentPattern to match (it reacts to beatCount on its
                                            // own next effect pass, not synchronously with this
                                            // composition) - grow here too so a tap in that window
                                            // still lands instead of throwing IndexOutOfBounds.
                                            while (accentPattern.size <= beatIndex) accentPattern.add(BeatAccent.NONE)
                                            accentPattern[beatIndex] = accent.next()
                                        },
                                        label = { Text(accent.chipLabel(beatIndex + 1)) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = ::confirm, enabled = isValid) { Text("Set") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )

    // Robolectric's default (small, focus-incapable) test window throws here - see
    // BpmUnitEntryDialog.kt's identical guard, which this mirrors, and
    // TimeSignatureEntryDialogScreenshotTest's own kdoc for why that's the window this dialog is
    // deliberately tested at rather than a real device losing nothing by failing silently.
    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }
}

/** Cycles a beat's accent tier on tap: NONE -> ACCENT -> STRONG_ACCENT -> CUSTOM -> NONE. */
private fun BeatAccent.next(): BeatAccent = when (this) {
    BeatAccent.NONE -> BeatAccent.ACCENT
    BeatAccent.ACCENT -> BeatAccent.STRONG_ACCENT
    BeatAccent.STRONG_ACCENT -> BeatAccent.CUSTOM
    BeatAccent.CUSTOM -> BeatAccent.NONE
}

/** Compact enough to read across up to 24 chips in a row: an unaccented beat just shows its own
 * (1-indexed) number, an accented one shows a single letter for its tier. */
private fun BeatAccent.chipLabel(beatNumber: Int): String = when (this) {
    BeatAccent.NONE -> beatNumber.toString()
    BeatAccent.ACCENT -> "A"
    BeatAccent.STRONG_ACCENT -> "S"
    BeatAccent.CUSTOM -> "C"
}
