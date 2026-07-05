package media.quaternion.qmetronome.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import media.quaternion.qmetronome.engine.BeatPhase
import media.quaternion.qmetronome.engine.MetronomeEngine
import media.quaternion.qmetronome.engine.TimeSignature
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
        // A real time signature - numerator over denominator, no fraction slash, each with its
        // own steppers - rather than a single "N/D" line with the denominator only reachable via
        // the long-press dialog. The denominator's steppers are the same size as the numerator's
        // (both peers now, not one primary/one hidden), but the whole pair sits a notch smaller
        // than BPM's own steppers above it to keep the visual hierarchy - tempo first, meter
        // second - the numerator row still carries the "• staged" label, since only beatCount
        // (not unitNoteValue) is staging-aware. Zero gap between the two rows (rather than even a
        // small spacer) is what actually reads as "one time signature" instead of two unrelated
        // stepper rows that happen to be stacked - a thin shared vertical rhythm, not a gap.
        TimeSignatureNumberRow(
            value = displayBeats,
            onDecrement = { MetronomeEngine.setBeatsPerBar(currentBeats() - 1) },
            onIncrement = { MetronomeEngine.setBeatsPerBar(currentBeats() + 1) },
            onLongPress = { showDialog = true },
            contentDescriptionNoun = "beats per bar",
            stagedLabel = holdMode != MetronomeEngine.HoldMode.Off,
        )
        TimeSignatureNumberRow(
            value = timeSignature.unitNoteValue,
            // Same "read the authoritative value fresh at call time" fix as currentBeats() above -
            // unitNoteValue isn't staged, but a hold-repeat gesture can still fire several times
            // before recomposition catches up, so closing over the recomposition-time
            // timeSignature.unitNoteValue directly would have the same collapse-to-one-step bug.
            onDecrement = { MetronomeEngine.setUnitNoteValue(MetronomeEngine.timeSignature.value.unitNoteValue - 1) },
            onIncrement = { MetronomeEngine.setUnitNoteValue(MetronomeEngine.timeSignature.value.unitNoteValue + 1) },
            onLongPress = { showDialog = true },
            contentDescriptionNoun = "note value",
            stagedLabel = false,
        )

        Spacer(Modifier.height(4.dp))

        // A fixed height, center-aligned, rather than sizing to content - BarQueueDots' bar
        // heights change as bars are added/removed/switched, and letting the row size to that
        // would change this Column's total height on every such change, which (being centered in
        // the parent layout) visibly shifted the BPM/transport rows above and below it up and
        // down. A fixed band keeps the row's footprint constant; center-aligning (rather than
        // bottom-aligning) keeps every bar's own centerline level with the icon buttons' own
        // centerline regardless of the bar's height, instead of everything sharing a bottom edge.
        Row(
            modifier = Modifier
                .height(QUEUE_ROW_HEIGHT)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            QueueIconButton(
                icon = Icons.Filled.Delete,
                contentDescription = "Long-press to clear the queue and reset to a single default bar",
                showDestructiveBadge = true,
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

/** One number of the time signature (numerator or denominator) with its own +/- steppers -
 * deliberately a notch smaller than BPM's own steppers to keep the visual hierarchy (tempo
 * first, meter second), and a fixed minimum width so the numerator and denominator rows stay
 * aligned with each other regardless of digit count (e.g. "4" vs "24"). Long-press opens the
 * combined [TimeSignatureEntryDialog] from either number. */
@Composable
private fun TimeSignatureNumberRow(
    value: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onLongPress: () -> Unit,
    contentDescriptionNoun: String,
    stagedLabel: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HoldRepeatButton(
            onStep = onDecrement,
            modifier = Modifier.size(TIME_SIG_STEPPER_SIZE),
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "Fewer $contentDescriptionNoun", modifier = Modifier.size(TIME_SIG_ICON_SIZE))
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(min = TIME_SIG_NUMBER_MIN_WIDTH)
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { onLongPress() })
                },
        ) {
            // No fillMaxWidth() here - this Column only has a *minimum* width (widthIn(min=...)),
            // not a fixed one, so a fillMaxWidth() child would demand all available width from
            // whatever unconstrained ancestor is above it, ballooning the Column - and therefore
            // this whole Row - out to (and past) the screen edge, pushing the "+" stepper off it
            // entirely. The Column's own horizontalAlignment already centers this Text within
            // whatever width it actually settles on.
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            if (stagedLabel) {
                Text(
                    text = "• staged",
                    style = MaterialTheme.typography.labelSmall,
                    color = RecordingRed,
                )
            }
        }
        HoldRepeatButton(
            onStep = onIncrement,
            modifier = Modifier.size(TIME_SIG_STEPPER_SIZE),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "More $contentDescriptionNoun", modifier = Modifier.size(TIME_SIG_ICON_SIZE))
        }
    }
}

/**
 * One rectangle per bar in the queue - a minimal, always-visible (even for the default single
 * bar) page indicator, since swiping alone gave no feedback about whether it did anything.
 * Deliberately mirrors the physical Glyph Matrix's own queue indicator
 * ([media.quaternion.qmetronome.visualizers.QueueOverlay]) rather than keeping a separate visual
 * language on-screen: a bar's *width* scales with its beat count relative to the rest of the
 * queue (not the full 1..24 theoretical range, which made everyday differences like 3 vs 7 beats
 * barely register), and its *height* scales with its own tempo - the same two axes, split the
 * same way, so the on-screen row and the glyph read as one consistent idea instead of two
 * different ones. The one bar actually active reads at full brightness; the rest are dimmed to
 * the same ratio the glyph overlay uses. Tap a bar to jump to it; long-press to remove it.
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

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        queue.forEachIndexed { index, spec ->
            val isActive = index == activeIndex
            val widthFraction = if (maxBeats == minBeats) {
                1f
            } else {
                (spec.beatCount - minBeats).toFloat() / beatSpan
            }
            val heightFraction = ((spec.bpm - MetronomeEngine.MIN_BPM) / (MetronomeEngine.MAX_BPM - MetronomeEngine.MIN_BPM))
                .coerceIn(0f, 1f)
            val barWidth = MIN_BAR_WIDTH + (MAX_BAR_WIDTH - MIN_BAR_WIDTH) * widthFraction
            val barHeight = MIN_BAR_HEIGHT + (MAX_BAR_HEIGHT - MIN_BAR_HEIGHT) * heightFraction
            val baseAlpha = if (isActive) ACTIVE_BAR_ALPHA else INACTIVE_BAR_ALPHA

            Box(
                modifier = Modifier
                    .width(barWidth.coerceAtLeast(MIN_BAR_HIT_WIDTH))
                    .height(MAX_BAR_HEIGHT)
                    .pointerInput(index) {
                        detectTapGestures(
                            onTap = { onDotClick(index) },
                            onLongPress = { onDotRemove(index) },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                // The bar is divided into one segment per beat - beats "represented as part of
                // the larger rectangle" rather than a separate tick row above it. Beat 0 at the
                // top, later beats stacked downward, so playback animates top-to-bottom. Only the
                // active bar's current beat animates (via the same decayEase flash curve the
                // Glyph Matrix visualizers use); every other segment sits at a flat brightness -
                // active bar segments brighter than inactive bars', so which bar is playing still
                // reads at a glance between pulses.
                Column(modifier = Modifier.width(barWidth).height(barHeight)) {
                    for (beatIdx in 0 until spec.beatCount) {
                        val isCurrentBeat = isActive && beatIdx == beat.beatIndex
                        val flash = if (isCurrentBeat) decayEase(beat.phase) else 0f
                        val segmentAlpha = baseAlpha + (1f - baseAlpha) * flash
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(PureWhite.copy(alpha = segmentAlpha)),
                        )
                    }
                }
            }
        }
    }
}

/** The minimal icon-only tap target shared by every bar-queue control (add/remove/mode-cycle) -
 * a plain [Box] + [clickable] rather than a Material [IconButton], which carries its own
 * min-touch-target padding/ripple sizing that reads as heavier than this row's otherwise
 * pixel-block-minimal controls. [showDestructiveBadge] adds a small red dot flagging a
 * destructive action (e.g. the reset-queue trash icon), the same accent used for staged/latched
 * state elsewhere, repurposed here as a "this can't be undone" cue rather than "in progress" -
 * and, since it can't be undone, also demotes [onClick] from a plain tap to a long-press (a
 * stray tap does nothing), the same "long-press for destructive" precedent [BarQueueDots]
 * already sets for removing a single bar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    showDestructiveBadge: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .combinedClickable(
                enabled = enabled,
                onClick = { if (!showDestructiveBadge) onClick() },
                onLongClick = if (showDestructiveBadge) onClick else null,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(15.dp).alpha(if (enabled) 1f else 0.3f),
        )
        if (showDestructiveBadge && enabled) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(6.dp)
                    .background(RecordingRed, CircleShape),
            )
        }
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
private val MIN_BAR_WIDTH = 8.dp
private val MAX_BAR_WIDTH = 22.dp
private val MIN_BAR_HEIGHT = 6.dp
private val MAX_BAR_HEIGHT = 30.dp
private val MIN_BAR_HIT_WIDTH = 22.dp
private const val INACTIVE_BAR_ALPHA = 0.35f
private const val ACTIVE_BAR_ALPHA = 0.7f
private val TIME_SIG_STEPPER_SIZE = 26.dp
private val TIME_SIG_ICON_SIZE = 11.dp
private val TIME_SIG_NUMBER_MIN_WIDTH = 20.dp

/** Fixed height for the whole queue row (bars + icon buttons) - tall enough for the tallest
 * possible bar plus a little buffer, so the row's footprint never changes as bars are
 * added/removed/resized. See the row's own kdoc for why a content-sized row caused the rest of
 * the screen to visibly shift. */
private val QUEUE_ROW_HEIGHT = 40.dp
