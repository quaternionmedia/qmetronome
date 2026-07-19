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
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import media.quaternion.qmetronome.engine.Phrase
import media.quaternion.qmetronome.engine.BeatPhase
import media.quaternion.qmetronome.engine.MetronomeEngine
import media.quaternion.qmetronome.engine.TimeSignature
import media.quaternion.qmetronome.midi.MidiActionSender
import media.quaternion.qmetronome.ui.icons.ExtraIcons
import media.quaternion.qmetronome.ui.theme.PureWhite
import media.quaternion.qmetronome.ui.theme.RecordingRed
import media.quaternion.qmetronome.visualizers.decayEase
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The Glyph Matrix preview is the focal point by design - it's a 1:1 stand-in for what's
 * actually showing on the hardware, and the rest of the screen exists to support it, not
 * compete with it. Settings (see [SettingsSheet]) now embeds the same live [TempoTransportCluster]
 * shown here too - not because tempo/transport belongs there conceptually, but so there is only
 * ever one live, composed instance of it: while Settings is open, this screen stops composing its
 * own copy entirely (its layouts' `showControls` parameter) rather than leaving an invisible,
 * still-recomposing duplicate running underneath the overlay.
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
    val previewSize = previewSizeFor(frame)

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val useCompactLayout = isLandscape && compactLandscape

    var showSettings by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showBpmDialog by remember { mutableStateOf(false) }
    val showControls = !showSettings && !showHelp

    Box(modifier = modifier.fillMaxSize()) {
        if (useCompactLayout) {
            CompactLandscapeLayout(
                previewSize = previewSize,
                frame = frame,
                beat = beat,
                showControls = showControls,
                onShowSettings = { showSettings = true },
                onShowBpmDialog = { showBpmDialog = true },
            )
        } else {
            PortraitLayout(
                previewSize = previewSize,
                frame = frame,
                beat = beat,
                showControls = showControls,
                onShowSettings = { showSettings = true },
                onShowBpmDialog = { showBpmDialog = true },
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(8.dp),
        ) {
            IconButton(onClick = { showHelp = true }) {
                Icon(ExtraIcons.Help, contentDescription = "Help")
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
            }
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

    if (showHelp) {
        HelpScreen(onDismiss = { showHelp = false })
    }

    if (showBpmDialog) {
        BpmUnitEntryDialog(
            initialBpm = stagedBpm ?: beat.bpm,
            onConfirm = { bpm ->
                // A BPH/BPS-range value needs the extended range actually turned on, or
                // setBpm's own clamping would immediately snap it back into 1-400 - the
                // dialog itself doesn't gate unit selection on this toggle, so this is the
                // one place that has to.
                if (bpm < MetronomeEngine.MIN_BPM || bpm > MetronomeEngine.MAX_BPM) {
                    MetronomeEngine.setExtendedBpmRangeEnabled(true)
                }
                MetronomeEngine.setBpm(bpm)
                showBpmDialog = false
            },
            onDismiss = { showBpmDialog = false },
        )
    }
}

/** Below [MetronomeEngine.MIN_BPM], a "0 BPM"-ish number reads as broken even though the actual
 * tempo (in BPH terms) is meaningful - so under 1 bpm switches to beats-per-hour, and above
 * [MetronomeEngine.MAX_BPM] switches to beats-per-second, both to 2 decimal places. Inside the
 * normal range, unchanged: a rounded whole-number BPM. Shared by [BpmControls] and its Settings
 * mirror so the two never disagree on how a given bpm reads. */
internal fun bpmDisplayValue(bpm: Float): String = when {
    bpm < MetronomeEngine.MIN_BPM -> String.format(java.util.Locale.ROOT, "%.2f", bpm * 60f)
    bpm > MetronomeEngine.MAX_BPM -> String.format(java.util.Locale.ROOT, "%.2f", bpm / 60f)
    else -> bpm.roundToInt().toString()
}

/** The unit label paired with [bpmDisplayValue] - see its kdoc. */
internal fun bpmDisplayUnit(bpm: Float): String = when {
    bpm < MetronomeEngine.MIN_BPM -> "BPH"
    bpm > MetronomeEngine.MAX_BPM -> "BPS"
    else -> "BPM"
}

/** [MatrixPreview]'s `matrixSize` derived from [frame]'s own length (it's always a perfect
 * square - one brightness value per cell) rather than a guessed constant - shared by [MainScreen]
 * and [HelpScreen] so neither can pass a mismatched size that samples the wrong corner of the
 * real matrix. Falls back to 25 (this app's actual physical Glyph Matrix dimension) only for the
 * one frame [frame] is ever empty: before [MetronomeEngine.attach] has run. */
internal fun previewSizeFor(frame: IntArray): Int =
    if (frame.isNotEmpty()) sqrt(frame.size.toDouble()).roundToInt() else 25

/**
 * BPM/BPH/BPS stepping shared by [BpmControls]' +/- hold-repeat buttons and its drag-to-scrub
 * gesture - flat, additive stepping inside [MetronomeEngine.MIN_BPM]..[MetronomeEngine.MAX_BPM]
 * (identical to this app's original, everyday behavior - a fixed [BPM_STEP] per whole step), and
 * multiplicative/logarithmic stepping outside it. A flat step size that feels right at 120 BPM is
 * either imperceptible or a single giant leap at the extremes of the 0.1-12000 BPM extended range -
 * a ~5%-per-step multiplicative formula instead covers that same huge range by *ratio*, so
 * traversal feels equally responsive near either end. [steps] is a signed, possibly-fractional
 * step count (fractional for the drag gesture's per-pixel progress, whole for the hold-repeat
 * buttons); this only computes the candidate value - [MetronomeEngine.setBpm]'s own clamping is
 * what actually enforces whichever bound (normal or extended) is currently in effect.
 */
internal fun steppedBpm(currentBpm: Float, steps: Float): Float {
    return if (currentBpm in MetronomeEngine.MIN_BPM..MetronomeEngine.MAX_BPM) {
        currentBpm + steps * BPM_STEP
    } else {
        currentBpm * LOG_BPM_STEP_FACTOR.pow(steps)
    }
}

// ── Shared preview + controls sub-composables ──────────────────────────────

@Composable
private fun PortraitLayout(
    previewSize: Int,
    frame: IntArray,
    beat: BeatPhase,
    showControls: Boolean,
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

        if (showControls) {
            Spacer(Modifier.height(28.dp))
            TempoTransportCluster(
                beat = beat,
                onShowBpmDialog = onShowBpmDialog,
                modifier = Modifier.alpha(CONTROLS_ALPHA),
            )
        }
    }
}

@Composable
private fun CompactLandscapeLayout(
    previewSize: Int,
    frame: IntArray,
    beat: BeatPhase,
    showControls: Boolean,
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
        if (showControls) {
            TempoTransportCluster(
                beat = beat,
                onShowBpmDialog = onShowBpmDialog,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(bottom = 32.dp)
                    .alpha(CONTROLS_ALPHA),
                verticalArrangement = Arrangement.Center,
                bpmToBeatsSpacing = 8.dp,
                beatsToTransportSpacing = 16.dp,
            )
        }
    }
}

/**
 * The BPM/time-signature/bar-queue controls plus TAP/play-stop/HOLD transport, bundled into one
 * composable and reused by both the main screen's own layouts and [SettingsSheet]'s "Tempo &
 * Bars" mirror - a single live instance of this cluster rather than two independently-recomposing
 * copies of the same `StateFlow`-driven UI (see [MainScreen]'s `showControls` handling, which stops
 * composing the main screen's own copy entirely while Settings is open, rather than leaving it
 * running invisibly underneath). Centered horizontally wherever it's used.
 */
@Composable
internal fun TempoTransportCluster(
    beat: BeatPhase,
    onShowBpmDialog: () -> Unit,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    bpmToBeatsSpacing: Dp = 12.dp,
    beatsToTransportSpacing: Dp = 24.dp,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = verticalArrangement,
    ) {
        BpmControls(beat = beat, onShowBpmDialog = onShowBpmDialog)
        Spacer(Modifier.height(bpmToBeatsSpacing))
        BeatsPerBarControls()
        Spacer(Modifier.height(beatsToTransportSpacing))
        TransportRow(beat = beat)
    }
}

/** Internal (not private): [PreviewGestureScreenshotTest] drives this composable's swipe/double-
 * tap/long-press gestures directly, the same "test the real production composable" pattern as
 * [BpmControls] and the rest. */
@Composable
internal fun PreviewBox(
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
            .testTag("matrix_preview")
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

/** Internal (not private): reused by [SettingsSheet]'s live "Tempo & Bars" mirror so that section
 * never has a second, independently-drifting copy of this display - see [BeatsPerBarControls]'s
 * own kdoc for the same reasoning. */
@Composable
internal fun BpmControls(beat: BeatPhase, onShowBpmDialog: () -> Unit) {
    val holdMode by MetronomeEngine.holdMode.collectAsState()
    val stagedBpm by MetronomeEngine.stagedBpm.collectAsState()
    val hasShownBpmHint by MetronomeEngine.hasShownBpmHint.collectAsState()
    val symbolicControlsEnabled by MetronomeEngine.symbolicControlsEnabled.collectAsState()
    val unitSymbolsEnabled by MetronomeEngine.unitSymbolsEnabled.collectAsState()
    val bpmDragPxPerStep = with(LocalDensity.current) { 6.dp.toPx() }

    val displayBpm = stagedBpm ?: beat.bpm

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
            onStep = { MetronomeEngine.setBpm(steppedBpm(currentBpm(), -1f)) },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(ExtraIcons.Remove, contentDescription = "Decrease BPM")
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .testTag("bpm_number")
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
                        MetronomeEngine.setBpm(steppedBpm(currentBpm(), dragAmount / bpmDragPxPerStep))
                    }
                },
        ) {
            Text(
                text = bpmDisplayValue(displayBpm),
                style = MaterialTheme.typography.displayMedium,
                modifier = if (symbolicControlsEnabled) {
                    Modifier.semantics { contentDescription = "${bpmDisplayValue(displayBpm)} ${bpmDisplayUnit(displayBpm)}" }
                } else {
                    Modifier
                },
            )
            if (holdMode != MetronomeEngine.HoldMode.Off) {
                StagedIndicator(symbolMode = symbolicControlsEnabled)
            }
        }
        HoldRepeatButton(
            onStep = { MetronomeEngine.setBpm(steppedBpm(currentBpm(), 1f)) },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Increase BPM")
        }
    }
    if (!symbolicControlsEnabled || unitSymbolsEnabled) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (unitSymbolsEnabled) {
                Icon(
                    ExtraIcons.UnitBpm,
                    contentDescription = null,
                    modifier = Modifier.size(UNIT_SYMBOL_SIZE),
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
            if (!symbolicControlsEnabled) {
                Text(
                    text = bpmDisplayUnit(displayBpm),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
    BpmGestureHint(visible = !hasShownBpmHint)
}

/** The "this value is staged, not yet applied" indicator - a small [RecordingRed] dot in symbol
 * mode (the same 6dp `CircleShape` badge already used elsewhere for staged/active-state cues, e.g.
 * [QueueIconButton]'s destructive badge), or the literal text otherwise. Shared by [BpmControls]
 * and [TimeSignatureNumberRow] so the two can never disagree on which form is showing. */
@Composable
private fun StagedIndicator(symbolMode: Boolean) {
    if (symbolMode) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(6.dp)
                .background(RecordingRed, CircleShape)
                .semantics { contentDescription = "Staged, not yet applied" },
        )
    } else {
        Text(
            text = "• staged",
            style = MaterialTheme.typography.labelSmall,
            color = RecordingRed,
        )
    }
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
 * plain, unchanging time signature. The reset/remove icons (destructive, no confirmation dialog)
 * only render while HOLD is active - dots/add/mode-cycle stay available regardless - so a stray
 * tap can't wipe queued bars or phrases outside of a deliberate hold/latch gesture.
 *
 * Internal (not private): [SettingsSheet]'s "Tempo & Bars" section embeds this same composable
 * directly rather than building a second beats-per-bar/bar-queue display, which would otherwise
 * either drift out of sync with this one or double the upkeep of every future change here.
 */
@Composable
internal fun BeatsPerBarControls() {
    val beat by MetronomeEngine.state.collectAsState()
    val stagedBeatsPerBar by MetronomeEngine.stagedBeatsPerBar.collectAsState()
    val holdMode by MetronomeEngine.holdMode.collectAsState()
    val timeSignature by MetronomeEngine.timeSignature.collectAsState()
    val queue by MetronomeEngine.timeSignatureQueue.collectAsState()
    val queueIndex by MetronomeEngine.queueIndex.collectAsState()
    val queueMode by MetronomeEngine.queueMode.collectAsState()
    val phrases by MetronomeEngine.phrases.collectAsState()
    val activePhraseIndex by MetronomeEngine.activePhraseIndex.collectAsState()
    val phraseQueueMode by MetronomeEngine.phraseQueueMode.collectAsState()
    val unitSymbolsEnabled by MetronomeEngine.unitSymbolsEnabled.collectAsState()
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
            testTag = "beats_per_bar_number",
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
            testTag = "unit_note_value_number",
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
            if (unitSymbolsEnabled) {
                Icon(
                    ExtraIcons.UnitBar,
                    contentDescription = null,
                    modifier = Modifier.size(UNIT_SYMBOL_SIZE),
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
            if (holdMode != MetronomeEngine.HoldMode.Off) {
                QueueIconButton(
                    icon = Icons.Filled.Delete,
                    contentDescription = "Long-press to clear the queue and reset to a single default bar",
                    showDestructiveBadge = true,
                    onClick = MetronomeEngine::resetQueueToDefault,
                    modifier = Modifier.testTag("queue_reset_button"),
                )

                QueueIconButton(
                    icon = ExtraIcons.Remove,
                    contentDescription = "Remove the active bar from the queue",
                    enabled = hasQueue,
                    onClick = MetronomeEngine::removeCurrentBarFromQueue,
                    modifier = Modifier.testTag("queue_remove_button"),
                )
            }

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
                modifier = Modifier.testTag("queue_add_button"),
            )

            val nextMode = when (queueMode) {
                MetronomeEngine.QueueMode.LOOP -> MetronomeEngine.QueueMode.ONCE
                MetronomeEngine.QueueMode.ONCE -> MetronomeEngine.QueueMode.MANUAL
                MetronomeEngine.QueueMode.MANUAL -> MetronomeEngine.QueueMode.LOOP
            }
            QueueIconButton(
                icon = when (queueMode) {
                    MetronomeEngine.QueueMode.LOOP -> ExtraIcons.Repeat
                    MetronomeEngine.QueueMode.ONCE -> ExtraIcons.SkipNext
                    MetronomeEngine.QueueMode.MANUAL -> ExtraIcons.TouchApp
                },
                contentDescription = "Bar queue mode: ${queueMode.name.lowercase()} - tap to change",
                onClick = { MetronomeEngine.setQueueMode(nextMode) },
                modifier = Modifier.testTag("queue_mode_button"),
            )

            // The single new pixel that exists before phrases are ever invoked - see
            // PhraseQueueControls' own kdoc for why this disappears once it's done its job.
            if (phrases.size == 1) {
                QueueIconButton(
                    icon = ExtraIcons.Phrases,
                    contentDescription = "Add a phrase - group bars into song-form sections",
                    onClick = MetronomeEngine::addPhrase,
                    modifier = Modifier.testTag("phrase_add_entry_button"),
                )
            }
        }

        if (phrases.size > 1) {
            Spacer(Modifier.height(4.dp))
            PhraseQueueControls(
                phrases = phrases,
                activePhraseIndex = activePhraseIndex,
                phraseQueueMode = phraseQueueMode,
                holdMode = holdMode,
                unitSymbolsEnabled = unitSymbolsEnabled,
            )
        }
    }

    if (showDialog) {
        TimeSignatureEntryDialog(
            initialBeatCount = displayBeats,
            beatCountRange = MetronomeEngine.MIN_BEATS_PER_BAR..MetronomeEngine.MAX_BEATS_PER_BAR,
            initialUnitNoteValue = timeSignature.unitNoteValue,
            unitNoteValueRange = 1..MetronomeEngine.MAX_UNIT_NOTE_VALUE,
            initialAccentPattern = timeSignature.accentPattern.orEmpty(),
            onConfirm = { newBeatCount, newUnitNoteValue, newAccentPattern ->
                MetronomeEngine.setBeatsPerBar(newBeatCount)
                MetronomeEngine.setUnitNoteValue(newUnitNoteValue)
                MetronomeEngine.setAccentPattern(newAccentPattern)
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
 * combined [TimeSignatureEntryDialog] from either number; a horizontal drag scrubs it, same
 * pixel-per-step sensitivity as [BpmControls]' own drag, for the same gesture on every number in
 * this app rather than just the BPM one. */
@Composable
private fun TimeSignatureNumberRow(
    value: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onLongPress: () -> Unit,
    contentDescriptionNoun: String,
    stagedLabel: Boolean,
    testTag: String,
    unitIcon: ImageVector? = null,
) {
    val dragPxPerStep = with(LocalDensity.current) { 6.dp.toPx() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HoldRepeatButton(
            onStep = onDecrement,
            modifier = Modifier.size(TIME_SIG_STEPPER_SIZE),
        ) {
            Icon(ExtraIcons.Remove, contentDescription = "Fewer $contentDescriptionNoun", modifier = Modifier.size(TIME_SIG_ICON_SIZE))
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .testTag(testTag)
                .widthIn(min = TIME_SIG_NUMBER_MIN_WIDTH)
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { onLongPress() })
                }
                .pointerInput(Unit) {
                    // A local accumulator, not a step-per-event call: value is Int-backed (unlike
                    // BPM's Float engine state), so reading it back between events would silently
                    // discard sub-step drag progress without something to carry the remainder -
                    // same shape as PreviewBox's own swipe accumulator.
                    var dragAccumulatedPx = 0f
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        dragAccumulatedPx += dragAmount
                        while (dragAccumulatedPx >= dragPxPerStep) {
                            onIncrement()
                            dragAccumulatedPx -= dragPxPerStep
                        }
                        while (dragAccumulatedPx <= -dragPxPerStep) {
                            onDecrement()
                            dragAccumulatedPx += dragPxPerStep
                        }
                    }
                },
        ) {
            // No fillMaxWidth() here - this Column only has a *minimum* width (widthIn(min=...)),
            // not a fixed one, so a fillMaxWidth() child would demand all available width from
            // whatever unconstrained ancestor is above it, ballooning the Column - and therefore
            // this whole Row - out to (and past) the screen edge, pushing the "+" stepper off it
            // entirely. The Column's own horizontalAlignment already centers this Text within
            // whatever width it actually settles on.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                val unitSymbolsEnabled by MetronomeEngine.unitSymbolsEnabled.collectAsState()
                if (unitIcon != null && unitSymbolsEnabled) {
                    Icon(
                        unitIcon,
                        contentDescription = null,
                        modifier = Modifier.size(UNIT_SYMBOL_SIZE),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            if (stagedLabel) {
                val symbolicControlsEnabled by MetronomeEngine.symbolicControlsEnabled.collectAsState()
                StagedIndicator(symbolMode = symbolicControlsEnabled)
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
 * Where [value] falls between [min] and [max], as a 0..1 fraction - clamped, so a value outside
 * the observed range (can't happen for the min/max callers derive their own bounds from, but
 * guards a caller passing an unrelated value) never overshoots. 1f (not 0f) when [min] and [max]
 * are equal, since "nothing to compare against" should read as one consistent, full-size shape
 * rather than collapsing everything to the smallest possible one. Shared, directly-testable
 * scaling math for [BarQueueDots] (beat-count width, bpm height) and [PhraseQueueDots] (beat-count
 * width) - one formula instead of three near-identical inline copies.
 */
internal fun proportionFraction(value: Float, min: Float, max: Float): Float =
    if (max <= min) 1f else ((value - min) / (max - min)).coerceIn(0f, 1f)

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
 *
 * Both width and height are scaled relative to *this queue's own* min/max, not a fixed absolute
 * range - width already worked this way for beat count; tempo used to scale height against the
 * fixed [MetronomeEngine.MIN_BPM]/[MetronomeEngine.MAX_BPM] (1-400)
 * instead, which silently clipped: any bar using the extended BPM range (below 1 or above 400 -
 * see [MetronomeEngine.extendedBpmRangeEnabled]) rendered at the same min/max height as every
 * other extended-range bar, regardless of how much further beyond that boundary it actually was.
 * Scoping to the queue's own bpm span fixes that and stays consistent with how width already
 * behaves - a bar's rendered size is always meaningful relative to what's actually queued next to
 * it, never silently pinned by an absolute boundary most bars never approach.
 *
 * Internal (not private): [SettingsSheet]'s Beat Overrides picker embeds this same composable to
 * choose which bar to author an override for - the exact "graphic to choose" the main screen
 * already uses, rather than a second bar-picker UI. That call site passes a neutered [beat]
 * ([BeatPhase.IDLE] with `beatIndex = -1`, which never matches any real beat index) unless it's
 * genuinely showing the live-active bar, and always passes local, non-navigating callbacks (picking
 * a bar to *edit* there never calls [MetronomeEngine.goToQueueBar] the way this screen's own
 * `onDotClick` does) - see that call site's own kdoc.
 */
@Composable
internal fun BarQueueDots(
    queue: List<TimeSignature>,
    activeIndex: Int,
    beat: BeatPhase,
    onDotClick: (Int) -> Unit,
    onDotRemove: (Int) -> Unit,
) {
    val minBeats = queue.minOf { it.beatCount }
    val maxBeats = queue.maxOf { it.beatCount }
    val minBpm = queue.minOf { it.bpm }
    val maxBpm = queue.maxOf { it.bpm }

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        queue.forEachIndexed { index, spec ->
            val isActive = index == activeIndex
            val widthFraction = proportionFraction(spec.beatCount.toFloat(), minBeats.toFloat(), maxBeats.toFloat())
            val heightFraction = proportionFraction(spec.bpm, minBpm, maxBpm)
            val barWidth = MIN_BAR_WIDTH + (MAX_BAR_WIDTH - MIN_BAR_WIDTH) * widthFraction
            val barHeight = MIN_BAR_HEIGHT + (MAX_BAR_HEIGHT - MIN_BAR_HEIGHT) * heightFraction
            val baseAlpha = if (isActive) ACTIVE_BAR_ALPHA else INACTIVE_BAR_ALPHA

            Box(
                modifier = Modifier
                    .testTag("queue_bar_$index")
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

/**
 * The phrase-management strip - reset/remove/dots/add/mode-cycle, one level up from
 * [BarQueueDots]/the bar-queue row above it, with full "controls like that of the single current"
 * parity. Only ever composed while [phrases] holds more than one entry (see the call site in
 * [BeatsPerBarControls]) - before a second phrase is ever added, none of this exists on screen at
 * all, and the one small "+phrase" entry point on the bar-queue row above is the only trace of the
 * feature. Removing phrases back down to a single one makes this whole strip disappear again,
 * symmetric with how [MetronomeEngine.addPhrase] is what first made it appear. Mirrors
 * [BeatsPerBarControls]' own reset/remove hold-gating - those two icons only render while
 * [holdMode] is active.
 */
@Composable
private fun PhraseQueueControls(
    phrases: List<Phrase>,
    activePhraseIndex: Int,
    phraseQueueMode: MetronomeEngine.QueueMode,
    holdMode: MetronomeEngine.HoldMode,
    unitSymbolsEnabled: Boolean,
) {
    Row(
        modifier = Modifier
            .height(QUEUE_ROW_HEIGHT)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (unitSymbolsEnabled) {
            Icon(
                ExtraIcons.UnitPhrase,
                contentDescription = null,
                modifier = Modifier.size(UNIT_SYMBOL_SIZE),
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
        if (holdMode != MetronomeEngine.HoldMode.Off) {
            QueueIconButton(
                icon = Icons.Filled.Delete,
                contentDescription = "Long-press to clear all phrases and reset to a single default phrase",
                showDestructiveBadge = true,
                onClick = MetronomeEngine::resetPhrasesToDefault,
                modifier = Modifier.testTag("phrase_reset_button"),
            )

            QueueIconButton(
                icon = ExtraIcons.Remove,
                contentDescription = "Remove the active phrase",
                onClick = MetronomeEngine::removeCurrentPhrase,
                modifier = Modifier.testTag("phrase_remove_button"),
            )
        }

        PhraseQueueDots(
            phrases = phrases,
            activeIndex = activePhraseIndex,
            onDotClick = MetronomeEngine::goToPhrase,
            onDotRemove = MetronomeEngine::removePhrase,
        )

        QueueIconButton(
            icon = Icons.Filled.Add,
            contentDescription = "Add a phrase",
            onClick = MetronomeEngine::addPhrase,
            modifier = Modifier.testTag("phrase_add_button"),
        )

        val nextPhraseMode = when (phraseQueueMode) {
            MetronomeEngine.QueueMode.LOOP -> MetronomeEngine.QueueMode.ONCE
            MetronomeEngine.QueueMode.ONCE -> MetronomeEngine.QueueMode.MANUAL
            MetronomeEngine.QueueMode.MANUAL -> MetronomeEngine.QueueMode.LOOP
        }
        QueueIconButton(
            icon = when (phraseQueueMode) {
                MetronomeEngine.QueueMode.LOOP -> ExtraIcons.Repeat
                MetronomeEngine.QueueMode.ONCE -> ExtraIcons.SkipNext
                MetronomeEngine.QueueMode.MANUAL -> ExtraIcons.TouchApp
            },
            contentDescription = "Phrase mode: ${phraseQueueMode.name.lowercase()} - tap to change",
            onClick = { MetronomeEngine.setPhraseQueueMode(nextPhraseMode) },
            modifier = Modifier.testTag("phrase_mode_button"),
        )
    }
}

/**
 * The phrase-level counterpart to [BarQueueDots] - each phrase's dot is a miniature vertical
 * stack of thin bar-segments, one per bar in that phrase, rather than one uniform-sized rectangle.
 * A segment's *width* echoes its bar's beat count relative to *every bar in every queued phrase*,
 * not just its own phrase's bars - scoping the min/max per-phrase (as this originally shipped)
 * meant a phrase's single bar always rendered at full width regardless of its actual beat count,
 * since a lone bar is trivially both the min and the max of its own one-bar set: a 3-beat phrase
 * and a 9-beat phrase, each with one bar, were visually indistinguishable. Scoping to the whole
 * queue (the same fix [BarQueueDots] already gets for its own height/tempo scaling) makes a
 * phrase's shape actually comparable to its neighbors, not just internally self-consistent -
 * simplified/miniaturized relative to [BarQueueDots] in every other way (no per-segment tempo/
 * height scaling, no per-segment beat ticks - deliberately not a second full [BarQueueDots], per
 * the explicit "stay minimal" instruction this feature shipped under; just enough shape to read
 * "this phrase is made of N bars of roughly these relative lengths, comparable to its neighbors"
 * at a glance). The dot's own overall bounding box stays fixed at
 * [MIN_BAR_HIT_WIDTH]x[MAX_BAR_HEIGHT] regardless of bar count, so the phrase strip's own layout
 * never shifts as bars are added/removed within a phrase - only what's drawn *inside* that fixed
 * box changes. Tap a phrase to jump to it (see [MetronomeEngine.goToPhrase]); long-press to remove
 * it, the same "long-press for destructive" gesture [BarQueueDots] already uses.
 *
 * Internal (not private): [SettingsSheet] embeds this same composable in two of its own sections -
 * Beat Overrides (picking which phrase to browse for an override) and Phrase Actions (picking
 * which phrase to author an action for) - both with local, non-navigating callbacks (`onDotClick`
 * updates that section's own selection state, never [MetronomeEngine.goToPhrase]) and `onDotRemove`
 * a no-op, since a picker shouldn't double as a destructive control - removal stays the main
 * screen's own hold-gated job.
 */
@Composable
internal fun PhraseQueueDots(
    phrases: List<Phrase>,
    activeIndex: Int,
    onDotClick: (Int) -> Unit,
    onDotRemove: (Int) -> Unit,
) {
    val allBars = phrases.flatMap { it.bars }
    val minBeats = allBars.minOf { it.beatCount }
    val maxBeats = allBars.maxOf { it.beatCount }

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        phrases.forEachIndexed { index, phrase ->
            val isActive = index == activeIndex
            val alpha = if (isActive) ACTIVE_BAR_ALPHA else INACTIVE_BAR_ALPHA

            Box(
                modifier = Modifier
                    .testTag("phrase_dot_$index")
                    .width(MIN_BAR_HIT_WIDTH)
                    .height(MAX_BAR_HEIGHT)
                    .pointerInput(index) {
                        detectTapGestures(
                            onTap = { onDotClick(index) },
                            onLongPress = { onDotRemove(index) },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.width(MAX_BAR_WIDTH).height(MAX_BAR_HEIGHT),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    phrase.bars.forEach { bar ->
                        val widthFraction = proportionFraction(bar.beatCount.toFloat(), minBeats.toFloat(), maxBeats.toFloat())
                        val segmentWidth = MIN_BAR_WIDTH + (MAX_BAR_WIDTH - MIN_BAR_WIDTH) * widthFraction
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .width(segmentWidth.coerceAtLeast(MIN_BAR_WIDTH))
                                .background(PureWhite.copy(alpha = alpha)),
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
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

/**
 * TAP / PLAY-STOP / HOLD - three controls, deliberately symmetric around the big centered
 * PLAY/STOP circle, not four: the manual MIDI Trigger action (see [MidiActionSender.fire]) lives
 * as a *gesture* on the existing TAP button (see [TapTriggerButton]) rather than its own
 * always-visible fourth button, which read lopsided against this row's own established
 * TAP/PLAY/HOLD balance.
 */
@Composable
private fun TransportRow(beat: BeatPhase) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TapTriggerButton()
        FilledIconButton(
            onClick = MetronomeEngine::toggle,
            modifier = Modifier.size(PLAY_PAUSE_SIZE),
        ) {
            Icon(
                imageVector = if (beat.isPlaying) ExtraIcons.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (beat.isPlaying) "Stop" else "Start",
                modifier = Modifier.size(PLAY_PAUSE_ICON_SIZE),
            )
        }
        HoldButton(modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp))
    }
}

/**
 * TAP itself - tap-tempo normally, or the main screen's manual MIDI Trigger action while HOLD is
 * latched *and* MIDI Actions is on (see [TransportRow]'s own kdoc for why this lives on TAP
 * rather than a fourth button). Its own composable, not inlined into [TransportRow], so
 * [HelpScreen] can embed just this one control under the Trigger topic (itself
 * [media.quaternion.qmetronome.tutorial.TutorialCategory.MIDI], not TEMPO/TIME_SIGNATURE/
 * BAR_QUEUE) without also pulling in the rest of [TempoTransportCluster] - a second, simultaneously
 * live BPM/beats-per-bar/bar-queue surface next to the one already shown under those other
 * categories, not just visually redundant but duplicate `testTag`s for anything that queries by
 * one.
 *
 * [testTag] defaults to the one every production call site (this screen, Settings' mirror) shares,
 * but is overridable so a caller that needs a *second*, simultaneously-composed instance on the
 * same screen - [HelpScreen]'s MIDI category, alongside TEMPO's already-shown [TempoTransportCluster]
 * copy - can keep both uniquely queryable instead of colliding on the default tag.
 */
@Composable
internal fun TapTriggerButton(testTag: String = "tap_trigger_button") {
    val symbolicControlsEnabled by MetronomeEngine.symbolicControlsEnabled.collectAsState()
    val midiActionsEnabled by MidiActionSender.enabled.collectAsState()
    val holdMode by MetronomeEngine.holdMode.collectAsState()
    // While latched (and there's actually something to fire), TAP's own tap gesture switches
    // from tap-tempo to Trigger. Deliberately keyed on Latched specifically (not Momentary): a
    // still-held Momentary press means the finger's about to lift and flush a staged bpm/beats
    // change any moment, the wrong time to swap what a tap on a neighboring button does out from
    // under it.
    val tapFiresTrigger = holdMode == MetronomeEngine.HoldMode.Latched && midiActionsEnabled

    OutlinedButton(
        onClick = {
            if (tapFiresTrigger) {
                MidiActionSender.fire(MetronomeEngine.resolveMidiActionForBeat(MetronomeEngine.state.value.beatIndex), System.nanoTime())
            } else {
                MetronomeEngine.tapTempo()
            }
        },
        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag(testTag),
    ) {
        when {
            tapFiresTrigger -> Icon(ExtraIcons.Trigger, contentDescription = "Trigger the current beat's MIDI action")
            symbolicControlsEnabled -> Icon(ExtraIcons.TouchApp, contentDescription = "Tap tempo")
            else -> Text("TAP")
        }
    }
}

private const val BPM_STEP = 1f

/** [steppedBpm]'s multiplicative step size outside the normal BPM range - ~10% per step.
 * Deliberately larger than a "just barely log-scale" 5% would suggest: right at the BPM=1
 * boundary, a log step's *absolute* size is `1 * (factor-1)`, while the linear step just inside
 * the boundary is a full [BPM_STEP] (1) - at 5% that's a 20x responsiveness cliff crossing from
 * "drag right below 1 BPM" into "drag right at/above 1 BPM" (or the reverse, decreasing into BPH
 * territory), which reads as the control suddenly barely responding, not just stepping
 * differently. 10% narrows that cliff to ~10x without fully sacrificing this factor's other job -
 * feeling *equally* responsive by ratio out toward 12000 BPM. A single constant factor can't make
 * both boundaries (BPM=1 *and* BPM=400) perfectly continuous with the linear region at once (the
 * two boundary values differ by 400x) - this is a deliberate middle ground, not a full fix; the
 * BPM number's long-press dialog and Settings' "Jump to unit" switcher exist as the precise,
 * non-drag path into BPH/BPS territory specifically because of this tension. */
private const val LOG_BPM_STEP_FACTOR = 1.1f
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
private val TIME_SIG_STEPPER_SIZE = 32.dp
private val TIME_SIG_ICON_SIZE = 14.dp
private val TIME_SIG_NUMBER_MIN_WIDTH = 26.dp

/** Size for the small, secondary-colored unit-symbol marks (bpm/beat-type/bar/phrase) gated
 * by [MetronomeEngine.unitSymbolsEnabled] - deliberately tiny, a label rather than a control. */
private val UNIT_SYMBOL_SIZE = 10.dp

/** Fixed height for the whole queue row (bars + icon buttons) - tall enough for the tallest
 * possible bar plus a little buffer, so the row's footprint never changes as bars are
 * added/removed/resized. See the row's own kdoc for why a content-sized row caused the rest of
 * the screen to visibly shift. */
private val QUEUE_ROW_HEIGHT = 40.dp
