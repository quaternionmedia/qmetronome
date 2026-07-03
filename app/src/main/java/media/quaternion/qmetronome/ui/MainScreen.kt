package media.quaternion.qmetronome.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.TouchApp
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import media.quaternion.qmetronome.engine.BeatPhase
import media.quaternion.qmetronome.engine.MetronomeEngine
import media.quaternion.qmetronome.engine.TimeSignature
import media.quaternion.qmetronome.ui.theme.DimGray
import media.quaternion.qmetronome.ui.theme.PureWhite
import media.quaternion.qmetronome.ui.theme.RecordingRed
import media.quaternion.qmetronome.visualizers.decayEase
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
            Spacer(Modifier.height(12.dp))
            BeatsPerBarControls()
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
            Spacer(Modifier.height(8.dp))
            BeatsPerBarControls()
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

/**
 * Beats-per-bar, on the main screen rather than tucked into Settings - a live meter control
 * belongs next to tempo, not behind a settings overlay you'd have to leave the beat to open.
 * Mirrors the BPM number's own pattern (steppers + long-press-to-type + staged-in-red) at a
 * visually secondary scale, since tempo is still the primary control.
 *
 * Below it, a dedicated row is the entry point to the bar queue (see
 * [MetronomeEngine.timeSignatureQueue]) - a "dead simple" way to line up a sequence of
 * differently-metered bars. Deliberately explicit buttons/taps rather than gestures layered onto
 * the tempo row above: an earlier version used double-tap-to-add and swipe-to-navigate on the
 * beats/bar label itself, but that label is narrow and sits between two small stepper buttons -
 * stacking a third and fourth gesture recognizer on it made even the existing long-press-to-type
 * feature less reliable (every tap had to wait out the double-tap timeout to disambiguate), and a
 * swipe starting that close to a button could easily be captured by the button instead. A
 * single-entry queue - the default - still shows one dot, but otherwise behaves exactly like a
 * plain, unchanging time signature.
 */
@Composable
private fun BeatsPerBarControls() {
    val beat by MetronomeEngine.state.collectAsState()
    val stagedBeatsPerBar by MetronomeEngine.stagedBeatsPerBar.collectAsState()
    val holdMode by MetronomeEngine.holdMode.collectAsState()
    val timeSignature by MetronomeEngine.timeSignature.collectAsState()
    val queue by MetronomeEngine.timeSignatureQueue.collectAsState()
    val queueIndex by MetronomeEngine.queueIndex.collectAsState()
    val queueMode by MetronomeEngine.queueMode.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    val displayBeats = stagedBeatsPerBar ?: beat.beatsPerBar
    val hasQueue = queue.size > 1

    // Same "read the authoritative value fresh at call time" fix as BpmControls.currentBpm() -
    // see its kdoc for why closing over displayBeats here would have the same bug.
    fun currentBeats(): Int = MetronomeEngine.stagedBeatsPerBar.value ?: MetronomeEngine.state.value.beatsPerBar

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            HoldRepeatButton(
                onStep = { MetronomeEngine.setBeatsPerBar(currentBeats() - 1) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Fewer beats per bar", modifier = Modifier.size(14.dp))
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onLongPress = { showDialog = true })
                    },
            ) {
                // "N/D" - a real time signature, numerator (beats, the steppers above edit this)
                // over denominator (note value, edited independently via the long-press dialog).
                Text(
                    text = "$displayBeats/${timeSignature.unitNoteValue}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
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
                onStep = { MetronomeEngine.setBeatsPerBar(currentBeats() + 1) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "More beats per bar", modifier = Modifier.size(14.dp))
            }
        }

        Spacer(Modifier.height(4.dp))

        // A fixed height, bottom-aligned, rather than sizing to content - BarQueueDots' dot
        // sizes and tick-row heights change as bars are added/removed/switched, and letting the
        // row size to that would change this Column's total height on every such change, which
        // (being centered in the parent layout) visibly shifted the BPM/transport rows above and
        // below it up and down. A fixed band with everything bottom-aligned keeps every dot's own
        // baseline level regardless of its size, and keeps the rest of the screen still.
        Row(
            modifier = Modifier
                .height(QUEUE_ROW_HEIGHT)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            QueueIconButton(
                icon = Icons.Filled.Delete,
                contentDescription = "Clear the queue and reset to a single default bar",
                onClick = MetronomeEngine::resetQueueToDefault,
            )

            QueueIconButton(
                icon = Icons.Filled.Remove,
                contentDescription = "Remove the active bar from the queue",
                enabled = hasQueue,
                onClick = MetronomeEngine::removeCurrentBarFromQueue,
            )

            BarQueueDots(
                queue = queue,
                activeIndex = queueIndex,
                beat = beat,
                onDotClick = MetronomeEngine::goToQueueBar,
                onDotRemove = MetronomeEngine::removeBarFromQueue,
            )

            QueueIconButton(
                icon = Icons.Filled.Add,
                contentDescription = "Add a bar to the queue",
                onClick = MetronomeEngine::addBarToQueue,
            )

            val nextMode = when (queueMode) {
                MetronomeEngine.QueueMode.LOOP -> MetronomeEngine.QueueMode.ONCE
                MetronomeEngine.QueueMode.ONCE -> MetronomeEngine.QueueMode.MANUAL
                MetronomeEngine.QueueMode.MANUAL -> MetronomeEngine.QueueMode.LOOP
            }
            QueueIconButton(
                icon = when (queueMode) {
                    MetronomeEngine.QueueMode.LOOP -> Icons.Filled.Repeat
                    MetronomeEngine.QueueMode.ONCE -> Icons.Filled.SkipNext
                    MetronomeEngine.QueueMode.MANUAL -> Icons.Filled.TouchApp
                },
                contentDescription = "Bar queue mode: ${queueMode.name.lowercase()} - tap to change",
                onClick = { MetronomeEngine.setQueueMode(nextMode) },
            )
        }
    }

    if (showDialog) {
        TimeSignatureEntryDialog(
            initialBeatCount = displayBeats,
            beatCountRange = MetronomeEngine.MIN_BEATS_PER_BAR..MetronomeEngine.MAX_BEATS_PER_BAR,
            initialUnitNoteValue = timeSignature.unitNoteValue,
            unitNoteValueRange = 1..MetronomeEngine.MAX_UNIT_NOTE_VALUE,
            onConfirm = { newBeatCount, newUnitNoteValue ->
                MetronomeEngine.setBeatsPerBar(newBeatCount)
                MetronomeEngine.setUnitNoteValue(newUnitNoteValue)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
}

/**
 * One pixel-block dot per bar in the queue - a minimal, always-visible (even for the default
 * single bar) page indicator, since swiping alone gave no feedback about whether it did anything.
 * Square, not round, and strictly grayscale - "on theme" with the rest of the app's blocky
 * monochrome look; red is deliberately reserved for staged/latched state elsewhere (see
 * [RecordingRed]'s own kdoc), not repurposed here for tempo since it reads as an error/warning
 * rather than "fast". Each dot's *size* scales with that bar's beat count *relative to the rest of
 * the queue* (not the full 1..24 theoretical range, which made everyday differences like 3 vs 7
 * beats barely register) and its *color* gradients dark gray (slow) to white (fast) by that bar's
 * own tempo - a way to see the whole queue's shape at a glance, since tempo is now per-bar. Tap a
 * dot to jump to it; long-press to remove it. Above every dot, [BeatTicks] shows that bar's own
 * beats - only the active bar's row pulses live, so every bar's shape is visible at once but only
 * the one actually playing draws the eye.
 */
@Composable
private fun BarQueueDots(
    queue: List<TimeSignature>,
    activeIndex: Int,
    beat: BeatPhase,
    onDotClick: (Int) -> Unit,
    onDotRemove: (Int) -> Unit,
) {
    val minBeats = queue.minOf { it.beatCount }
    val maxBeats = queue.maxOf { it.beatCount }
    val beatSpan = (maxBeats - minBeats).coerceAtLeast(1)

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        queue.forEachIndexed { index, spec ->
            val isActive = index == activeIndex
            val sizeFraction = if (maxBeats == minBeats) {
                1f
            } else {
                (spec.beatCount - minBeats).toFloat() / beatSpan
            }
            val baseDotSize = MIN_DOT_SIZE + (MAX_DOT_SIZE - MIN_DOT_SIZE) * sizeFraction
            val dotSize = if (isActive) baseDotSize + ACTIVE_DOT_BOOST else baseDotSize
            val speedFraction = ((spec.bpm - MetronomeEngine.MIN_BPM) / (MetronomeEngine.MAX_BPM - MetronomeEngine.MIN_BPM))
                .coerceIn(0f, 1f)
            // Dark gray = slow, white = fast - plain grayscale rather than red, which read as an
            // error/warning state instead of a tempo indicator; red stays reserved for staged/
            // latched state elsewhere (see RecordingRed's own kdoc).
            val baseColor = lerp(DimGray, PureWhite, speedFraction)
            val dotColor = if (isActive) baseColor else baseColor.copy(alpha = 0.45f)

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BeatTicks(
                    beatCount = spec.beatCount,
                    beatIndex = if (isActive) beat.beatIndex else -1,
                    phase = if (isActive) beat.phase else 0f,
                )
                Spacer(Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .size(dotSize.coerceAtLeast(MIN_DOT_HIT_TARGET))
                        .pointerInput(index) {
                            detectTapGestures(
                                onTap = { onDotClick(index) },
                                onLongPress = { onDotRemove(index) },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(modifier = Modifier.size(dotSize).background(dotColor))
                }
            }
        }
    }
}

/** A small upward tick per beat, the active bar's current beat pulsing with the same [decayEase]
 * flash curve the Glyph Matrix visualizers use, so the beat can be followed along with by eye
 * even without the physical matrix. [beatIndex] of -1 (an inactive bar) shows every tick at rest. */
@Composable
private fun BeatTicks(beatCount: Int, beatIndex: Int, phase: Float) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        repeat(beatCount) { index ->
            val isCurrent = index == beatIndex
            val flash = if (isCurrent) decayEase(phase) else 0f
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(6.dp)
                    .background(PureWhite.copy(alpha = if (isCurrent) (0.35f + 0.65f * flash) else 0.15f)),
            )
        }
    }
}

/** The minimal icon-only tap target shared by every bar-queue control (add/remove/mode-cycle) -
 * a plain [Box] + [clickable] rather than a Material [IconButton], which carries its own
 * min-touch-target padding/ripple sizing that reads as heavier than this row's otherwise
 * pixel-block-minimal controls. */
@Composable
private fun QueueIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(15.dp).alpha(if (enabled) 1f else 0.3f),
        )
    }
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
private val MIN_DOT_SIZE = 8.dp
private val MAX_DOT_SIZE = 22.dp
private val ACTIVE_DOT_BOOST = 4.dp
private val MIN_DOT_HIT_TARGET = 22.dp

/** Fixed height for the whole queue row (ticks + dots + icon buttons) - tall enough for the
 * tallest possible content (max tick height + spacer + largest active dot) plus a little buffer,
 * so the row's footprint never changes as bars are added/removed/resized. See the row's own kdoc
 * for why a content-sized row caused the rest of the screen to visibly shift. */
private val QUEUE_ROW_HEIGHT = 44.dp
