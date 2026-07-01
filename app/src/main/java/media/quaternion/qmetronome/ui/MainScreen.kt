package media.quaternion.qmetronome.ui

import android.content.res.Configuration
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import media.quaternion.qmetronome.engine.MetronomeEngine
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The Glyph Matrix preview is the focal point by design - it's a 1:1 stand-in for what's
 * actually showing on the hardware, and the rest of the screen exists to support it, not
 * compete with it. Everything that isn't "look at the beat" or "start/stop/tap" lives behind
 * the settings button in a full-screen overlay (see [SettingsSheet]), so the main screen stays
 * down to one functional grouping at a time. Two affordances don't rely on that small button:
 * long-pressing the preview/BPM readout also opens settings (the bottom-right button sits close
 * to the brand footer and can be a small target on some devices), and swiping the preview
 * left/right cycles visualizers without leaving the main screen at all.
 */
@Composable
fun MainScreen(onActivateToy: () -> Unit, modifier: Modifier = Modifier) {
    val beat by MetronomeEngine.state.collectAsState()
    val frame by MetronomeEngine.frame.collectAsState()
    val compactLandscape by MetronomeEngine.compactLandscape.collectAsState()
    val previewSize = if (frame.isNotEmpty()) sqrt(frame.size.toDouble()).roundToInt() else 25

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val useCompactLayout = isLandscape && compactLandscape

    var showSettings by remember { mutableStateOf(false) }
    var showBpmDialog by remember { mutableStateOf(false) }

    val swipeThresholdPx = with(LocalDensity.current) { 56.dp.toPx() }
    val bpmDragPxPerStep = with(LocalDensity.current) { 6.dp.toPx() }

    // HOLD/queue staging: while the HOLD button is held, BPM changes accumulate in pendingBpm
    // instead of going straight to the engine. On release, the pending value is flushed.
    var isHeld by remember { mutableStateOf(false) }
    var pendingBpm by remember { mutableFloatStateOf(beat.bpm) }

    LaunchedEffect(isHeld) {
        if (isHeld) {
            pendingBpm = MetronomeEngine.state.value.bpm
        } else {
            val staged = pendingBpm
            val live = MetronomeEngine.state.value.bpm
            if (staged != live) MetronomeEngine.setBpm(staged)
        }
    }

    fun applyBpm(newBpm: Float) {
        val clamped = newBpm.coerceIn(MetronomeEngine.MIN_BPM, MetronomeEngine.MAX_BPM)
        if (isHeld) pendingBpm = clamped else MetronomeEngine.setBpm(clamped)
    }

    val displayBpm = if (isHeld) pendingBpm.roundToInt() else beat.bpm.roundToInt()

    Box(modifier = modifier.fillMaxSize()) {
        if (useCompactLayout) {
            CompactLandscapeLayout(
                previewSize = previewSize,
                frame = frame,
                displayBpm = displayBpm,
                isHeld = isHeld,
                swipeThresholdPx = swipeThresholdPx,
                bpmDragPxPerStep = bpmDragPxPerStep,
                beat = beat,
                onShowSettings = { showSettings = true },
                onShowBpmDialog = { showBpmDialog = true },
                onHoldChanged = { isHeld = it },
                onApplyBpm = ::applyBpm,
            )
        } else {
            PortraitLayout(
                previewSize = previewSize,
                frame = frame,
                displayBpm = displayBpm,
                isHeld = isHeld,
                swipeThresholdPx = swipeThresholdPx,
                bpmDragPxPerStep = bpmDragPxPerStep,
                beat = beat,
                onShowSettings = { showSettings = true },
                onShowBpmDialog = { showBpmDialog = true },
                onHoldChanged = { isHeld = it },
                onApplyBpm = ::applyBpm,
            )
        }

        IconButton(
            onClick = { showSettings = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(8.dp),
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings")
        }

        BrandFooter(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
        )
    }

    if (showSettings) {
        SettingsSheet(onDismiss = { showSettings = false }, onActivateToy = onActivateToy)
    }

    if (showBpmDialog) {
        BpmEntryDialog(
            currentBpm = beat.bpm.roundToInt(),
            onConfirm = { bpm ->
                MetronomeEngine.setBpm(bpm.toFloat())
                showBpmDialog = false
            },
            onDismiss = { showBpmDialog = false },
        )
    }
}

// ── Shared preview + controls sub-composables ──────────────────────────────

@Composable
private fun PortraitLayout(
    previewSize: Int,
    frame: IntArray,
    displayBpm: Int,
    isHeld: Boolean,
    swipeThresholdPx: Float,
    bpmDragPxPerStep: Float,
    beat: media.quaternion.qmetronome.engine.BeatPhase,
    onShowSettings: () -> Unit,
    onShowBpmDialog: () -> Unit,
    onHoldChanged: (Boolean) -> Unit,
    onApplyBpm: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PreviewBox(
            previewSize = previewSize,
            frame = frame,
            swipeThresholdPx = swipeThresholdPx,
            onShowSettings = onShowSettings,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(28.dp))
        BpmControls(
            displayBpm = displayBpm,
            isHeld = isHeld,
            bpmDragPxPerStep = bpmDragPxPerStep,
            onApplyBpm = onApplyBpm,
            onShowBpmDialog = onShowBpmDialog,
        )
        Spacer(Modifier.height(24.dp))
        TransportRow(beat = beat, isHeld = isHeld, onHoldChanged = onHoldChanged)
    }
}

@Composable
private fun CompactLandscapeLayout(
    previewSize: Int,
    frame: IntArray,
    displayBpm: Int,
    isHeld: Boolean,
    swipeThresholdPx: Float,
    bpmDragPxPerStep: Float,
    beat: media.quaternion.qmetronome.engine.BeatPhase,
    onShowSettings: () -> Unit,
    onShowBpmDialog: () -> Unit,
    onHoldChanged: (Boolean) -> Unit,
    onApplyBpm: (Float) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        PreviewBox(
            previewSize = previewSize,
            frame = frame,
            swipeThresholdPx = swipeThresholdPx,
            onShowSettings = onShowSettings,
            modifier = Modifier.fillMaxHeight().aspectRatio(1f),
        )
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BpmControls(
                displayBpm = displayBpm,
                isHeld = isHeld,
                bpmDragPxPerStep = bpmDragPxPerStep,
                onApplyBpm = onApplyBpm,
                onShowBpmDialog = onShowBpmDialog,
            )
            Spacer(Modifier.height(16.dp))
            TransportRow(beat = beat, isHeld = isHeld, onHoldChanged = onHoldChanged)
        }
    }
}

@Composable
private fun PreviewBox(
    previewSize: Int,
    frame: IntArray,
    swipeThresholdPx: Float,
    onShowSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Long-press opens settings; double-tap toggles play/stop.
    // Both tap gestures share one detector so only one awaitPointerEventScope competes with the
    // horizontal drag below. Single-tap is left unhandled (not needed on the preview itself).
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onShowSettings() },
                    onDoubleTap = { MetronomeEngine.toggle() },
                )
            },
    ) {
        MatrixPreview(
            matrixSize = previewSize,
            frame = frame,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .pointerInput(Unit) {
                    var dragAccumulatedPx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragAccumulatedPx = 0f },
                        onDragEnd = {
                            when {
                                dragAccumulatedPx <= -swipeThresholdPx -> MetronomeEngine.nextVisualizer()
                                dragAccumulatedPx >= swipeThresholdPx -> MetronomeEngine.previousVisualizer()
                            }
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        dragAccumulatedPx += dragAmount
                    }
                },
        )
    }
}

@Composable
private fun BpmControls(
    displayBpm: Int,
    isHeld: Boolean,
    bpmDragPxPerStep: Float,
    onApplyBpm: (Float) -> Unit,
    onShowBpmDialog: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HoldRepeatButton(
            onStep = {
                val base = if (isHeld) displayBpm.toFloat() else MetronomeEngine.state.value.bpm
                onApplyBpm(base - BPM_STEP)
            },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease BPM")
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { onShowBpmDialog() })
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        val base = if (isHeld) displayBpm.toFloat()
                        else MetronomeEngine.state.value.bpm
                        onApplyBpm(base + dragAmount / bpmDragPxPerStep)
                    }
                },
        ) {
            Text(
                text = displayBpm.toString(),
                style = MaterialTheme.typography.displayLarge,
            )
            if (isHeld) {
                Text(
                    text = "• staged",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        HoldRepeatButton(
            onStep = {
                val base = if (isHeld) displayBpm.toFloat() else MetronomeEngine.state.value.bpm
                onApplyBpm(base + BPM_STEP)
            },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Increase BPM")
        }
    }
    Text(
        text = "BPM",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.secondary,
    )
}

@Composable
private fun TransportRow(
    beat: media.quaternion.qmetronome.engine.BeatPhase,
    isHeld: Boolean,
    onHoldChanged: (Boolean) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = { MetronomeEngine.tapTempo() }) {
            Text("TAP")
        }
        // HOLD queues BPM changes until released - use a custom pressable button so we can
        // detect the held-down state via interactionSource rather than just onClick.
        HoldButton(isHeld = isHeld, onHoldChanged = onHoldChanged)
        FilledIconButton(
            onClick = MetronomeEngine::toggle,
            modifier = Modifier.size(64.dp),
        ) {
            Icon(
                imageVector = if (beat.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (beat.isPlaying) "Stop" else "Start",
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

// ── BPM dialog ─────────────────────────────────────────────────────────────

@Composable
private fun BpmEntryDialog(currentBpm: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(currentBpm.toString(), selection = TextRange(0, currentBpm.toString().length)))
    }
    val parsed = fieldValue.text.toIntOrNull()
    val isValid = parsed != null && parsed >= MetronomeEngine.MIN_BPM.toInt() && parsed <= MetronomeEngine.MAX_BPM.toInt()

    fun confirm() {
        if (isValid) onConfirm(parsed!!)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set BPM") },
        text = {
            OutlinedTextField(
                value = fieldValue,
                onValueChange = { fieldValue = it },
                label = { Text("${MetronomeEngine.MIN_BPM.toInt()}–${MetronomeEngine.MAX_BPM.toInt()}") },
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

private const val BPM_STEP = 1f
