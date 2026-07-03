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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import media.quaternion.qmetronome.engine.BeatPhase
import media.quaternion.qmetronome.engine.MetronomeEngine
import media.quaternion.qmetronome.ui.theme.RecordingRed
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The Glyph Matrix preview is the focal point by design - it's a 1:1 stand-in for what's
 * actually showing on the hardware, and the rest of the screen exists to support it, not
 * compete with it. Everything that isn't "look at the beat" or "start/stop/tap" lives behind
 * the settings button in a full-screen overlay (see [SettingsSheet]), so the main screen stays
 * down to one functional grouping at a time.
 *
 * A few affordances don't rely on that small button: long-pressing the preview opens settings
 * (the bottom-right button sits close to the brand footer and can be a small target on some
 * devices), swiping the preview left/right cycles visualizers, and the BPM number itself is
 * triple-duty - tap it for tap-tempo, long-press it to type an exact value, drag it to scrub -
 * with a one-time hint (see [BpmGestureHint]) so that isn't a hidden secret. The transport row's
 * controls are deliberately dimmed/shrunk (see [CONTROLS_ALPHA]) relative to the bright preview,
 * which stays the visual focal point even in a dark room.
 */
@Composable
fun MainScreen(onActivateToy: () -> Unit, modifier: Modifier = Modifier) {
    val beat by MetronomeEngine.state.collectAsState()
    val frame by MetronomeEngine.frame.collectAsState()
    val compactLandscape by MetronomeEngine.compactLandscape.collectAsState()
    val stagedBpm by MetronomeEngine.stagedBpm.collectAsState()
    val previewSize = if (frame.isNotEmpty()) sqrt(frame.size.toDouble()).roundToInt() else 25

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val useCompactLayout = isLandscape && compactLandscape

    var showSettings by remember { mutableStateOf(false) }
    var showBpmDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        if (useCompactLayout) {
            CompactLandscapeLayout(
                previewSize = previewSize,
                frame = frame,
                beat = beat,
                onShowSettings = { showSettings = true },
                onShowBpmDialog = { showBpmDialog = true },
            )
        } else {
            PortraitLayout(
                previewSize = previewSize,
                frame = frame,
                beat = beat,
                onShowSettings = { showSettings = true },
                onShowBpmDialog = { showBpmDialog = true },
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

        QmBrandMark(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(8.dp),
        )

        AppBrandMark(
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
        NumericEntryDialog(
            title = "Set BPM",
            initialValue = stagedBpm ?: beat.bpm,
            valueRange = MetronomeEngine.MIN_BPM..MetronomeEngine.MAX_BPM,
            onConfirm = { bpm ->
                MetronomeEngine.setBpm(bpm)
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
    beat: BeatPhase,
    onShowSettings: () -> Unit,
    onShowBpmDialog: () -> Unit,
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
            onShowSettings = onShowSettings,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(28.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(CONTROLS_ALPHA),
        ) {
            BpmControls(beat = beat, onShowBpmDialog = onShowBpmDialog)
            Spacer(Modifier.height(24.dp))
            TransportRow(beat = beat)
        }
    }
}

@Composable
private fun CompactLandscapeLayout(
    previewSize: Int,
    frame: IntArray,
    beat: BeatPhase,
    onShowSettings: () -> Unit,
    onShowBpmDialog: () -> Unit,
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
            onShowSettings = onShowSettings,
            modifier = Modifier.fillMaxHeight().aspectRatio(1f),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(bottom = 32.dp)
                .alpha(CONTROLS_ALPHA),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BpmControls(beat = beat, onShowBpmDialog = onShowBpmDialog)
            Spacer(Modifier.height(16.dp))
            TransportRow(beat = beat)
        }
    }
}

@Composable
private fun PreviewBox(
    previewSize: Int,
    frame: IntArray,
    onShowSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val swipeThresholdPx = with(LocalDensity.current) { 56.dp.toPx() }

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
private fun BpmControls(beat: BeatPhase, onShowBpmDialog: () -> Unit) {
    val holdMode by MetronomeEngine.holdMode.collectAsState()
    val stagedBpm by MetronomeEngine.stagedBpm.collectAsState()
    val hasShownBpmHint by MetronomeEngine.hasShownBpmHint.collectAsState()
    val bpmDragPxPerStep = with(LocalDensity.current) { 6.dp.toPx() }

    val displayBpm = (stagedBpm ?: beat.bpm).roundToInt()

    // Reads the authoritative bpm fresh at call time rather than closing over `displayBpm` -
    // both the hold-repeat buttons' loop and the drag gesture's detector are long-running
    // coroutines that don't restart every recomposition (see HoldRepeatButton's kdoc), so a step
    // callback that only closed over `displayBpm` would keep re-applying the same stale base for
    // the entire gesture instead of accumulating - the bug that limited holding +/- or dragging
    // to a single effective step, most noticeable while held/latched but not exclusive to it.
    fun currentBpm(): Float = MetronomeEngine.stagedBpm.value ?: MetronomeEngine.state.value.bpm

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HoldRepeatButton(
            onStep = { MetronomeEngine.setBpm(currentBpm() - BPM_STEP) },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease BPM")
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .pointerInput(Unit) {
                    // A plain tap registers a tap-tempo beat; long-press opens exact entry. Both
                    // share one detector (see PreviewBox) rather than layering a second one.
                    detectTapGestures(
                        onTap = { MetronomeEngine.tapTempo() },
                        onLongPress = { onShowBpmDialog() },
                    )
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        MetronomeEngine.setBpm(currentBpm() + dragAmount / bpmDragPxPerStep)
                    }
                },
        ) {
            Text(
                text = displayBpm.toString(),
                style = MaterialTheme.typography.displayMedium,
            )
            if (holdMode != MetronomeEngine.HoldMode.Off) {
                Text(
                    text = "• staged",
                    style = MaterialTheme.typography.labelSmall,
                    color = RecordingRed,
                )
            }
        }
        HoldRepeatButton(
            onStep = { MetronomeEngine.setBpm(currentBpm() + BPM_STEP) },
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
    BpmGestureHint(visible = !hasShownBpmHint)
}

/** Shown once, ever, so the BPM number's tap/long-press/drag trio isn't a hidden secret -
 * auto-dismisses itself after a few seconds rather than requiring an explicit close tap, which
 * would otherwise add a second interactive target right next to the very gestures it explains. */
@Composable
private fun BpmGestureHint(visible: Boolean) {
    if (!visible) return
    LaunchedEffect(Unit) {
        delay(BPM_HINT_DURATION_MS)
        MetronomeEngine.markBpmHintShown()
    }
    Text(
        text = "tap for tempo · long-press to type · drag to scrub",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.secondary,
    )
}

@Composable
private fun TransportRow(beat: BeatPhase) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = { MetronomeEngine.tapTempo() },
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
        ) {
            Text("TAP")
        }
        FilledIconButton(
            onClick = MetronomeEngine::toggle,
            modifier = Modifier.size(PLAY_PAUSE_SIZE),
        ) {
            Icon(
                imageVector = if (beat.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (beat.isPlaying) "Stop" else "Start",
                modifier = Modifier.size(PLAY_PAUSE_ICON_SIZE),
            )
        }
        HoldButton(modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp))
    }
}

private const val BPM_STEP = 1f
private const val BPM_HINT_DURATION_MS = 4000L
private const val CONTROLS_ALPHA = 0.82f
private val PLAY_PAUSE_SIZE = 76.dp
private val PLAY_PAUSE_ICON_SIZE = 40.dp
