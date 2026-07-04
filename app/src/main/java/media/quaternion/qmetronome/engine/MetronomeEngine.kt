package media.quaternion.qmetronome.engine

import android.content.Context
import android.util.Log
import media.quaternion.qmetronome.midi.MidiClockSource
import media.quaternion.qmetronome.visualizers.GlyphVisualizer
import media.quaternion.qmetronome.visualizers.QueueOverlay
import media.quaternion.qmetronome.visualizers.VisualizerRegistry
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The single source of truth for tempo, beat position and the current Glyph frame. It is a
 * process-wide singleton so the on-screen preview (in [media.quaternion.qmetronome.MainActivity]) and
 * the real Glyph Matrix (in [media.quaternion.qmetronome.glyph.MetronomeGlyphService]) are always
 * showing exactly the same thing, whether or not the app UI happens to be open.
 *
 * Timing is produced by a [ClockSource]. It defaults to [InternalClockSource], but
 * auto-switches to following [MidiClockSource] the moment any external MIDI clock activity
 * arrives (see [attach]), and falls back to internal timing again if that feed goes quiet.
 */
object MetronomeEngine {

    const val MIN_BPM = 1f
    const val MAX_BPM = 400f

    const val MIN_BEATS_PER_BAR = 1
    const val MAX_BEATS_PER_BAR = 24

    /** Where beat timing is currently coming from. */
    sealed interface ClockStatus {
        data object Internal : ClockStatus
        data class Midi(val measuredBpm: Float?, val source: String?) : ClockStatus
    }

    /**
     * Whether BPM/beats-per-bar changes apply immediately or get staged. [Momentary] is the
     * classic "shift key" - held down, released, flushed. [Latched] is the same staging behavior
     * but sticky: entered via a long-press or double-tap on HOLD, and only cleared by a
     * subsequent tap on HOLD (or by [stop]). See [beginHold], [endHold], [toggleLatch].
     */
    sealed interface HoldMode {
        data object Off : HoldMode
        data object Momentary : HoldMode
        data object Latched : HoldMode
    }

    /**
     * How the bar queue ([timeSignatureQueue]) advances at each bar boundary when it holds more
     * than one entry - a "dead simple" way to queue up a sequence of differently-metered bars
     * (e.g. 3 bars of 4/4 then 1 bar of 3/4) without building a full score editor.
     * [LOOP] (the default) wraps back to the first bar after the last; [ONCE] stops advancing
     * once it reaches the last bar, holding there; [MANUAL] never auto-advances - only
     * [nextQueueBar]/[previousQueueBar] (e.g. a swipe) move the active bar.
     */
    enum class QueueMode { LOOP, ONCE, MANUAL }

    /**
     * Last-resort safety net: a visualizer or clock implementation throwing should never
     * silently wedge the engine into a state where [_state] says "playing" but nothing is
     * actually ticking - see [start]'s liveness check, which is the actual recovery mechanism.
     * This only logs so a future bug is diagnosable instead of invisible.
     */
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Unhandled engine coroutine failure", throwable)
    }

    private val scope = CoroutineScope(SupervisorJob() + exceptionHandler)
    @Volatile private var clock: ClockSource = InternalClockSource()
    private val clickPlayer = ClickPlayer()

    private var settings: MetronomeSettings? = null

    private val _state = MutableStateFlow(BeatPhase.IDLE)
    val state: StateFlow<BeatPhase> = _state.asStateFlow()

    private val _visualizer = MutableStateFlow<GlyphVisualizer>(VisualizerRegistry.default)
    val visualizer: StateFlow<GlyphVisualizer> = _visualizer.asStateFlow()

    private val _frame = MutableStateFlow(IntArray(0))
    val frame: StateFlow<IntArray> = _frame.asStateFlow()

    private val _clockStatus = MutableStateFlow<ClockStatus>(ClockStatus.Internal)
    val clockStatus: StateFlow<ClockStatus> = _clockStatus.asStateFlow()

    private val _clickEnabled = MutableStateFlow(false)
    val clickEnabled: StateFlow<Boolean> = _clickEnabled.asStateFlow()

    private val _visualOffsetMs = MutableStateFlow(0f)
    val visualOffsetMs: StateFlow<Float> = _visualOffsetMs.asStateFlow()

    private val _compactLandscape = MutableStateFlow(false)
    val compactLandscape: StateFlow<Boolean> = _compactLandscape.asStateFlow()

    private val _timeSignature = MutableStateFlow(TimeSignature.DEFAULT)
    val timeSignature: StateFlow<TimeSignature> = _timeSignature.asStateFlow()

    /** The queue of bars to cycle through - a single entry (today's default) behaves exactly
     * like a plain, unchanging time signature. See [QueueMode] for how playback advances through
     * more than one. */
    private val _timeSignatureQueue = MutableStateFlow(listOf(TimeSignature.DEFAULT))
    val timeSignatureQueue: StateFlow<List<TimeSignature>> = _timeSignatureQueue.asStateFlow()

    private val _queueIndex = MutableStateFlow(0)
    val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()

    private val _queueMode = MutableStateFlow(QueueMode.LOOP)
    val queueMode: StateFlow<QueueMode> = _queueMode.asStateFlow()

    /** Whether [QueueOverlay]'s ambient per-bar/per-beat background is drawn into the Glyph
     * frame at all - on by default, but purely cosmetic, so it's fully optional. */
    private val _queueOverlayEnabled = MutableStateFlow(true)
    val queueOverlayEnabled: StateFlow<Boolean> = _queueOverlayEnabled.asStateFlow()

    /** Whether the selected [GlyphVisualizer] itself renders at all - independent of
     * [queueOverlayEnabled], so a performer can run either, both, or neither. Disabled, the base
     * frame is blank rather than skipped, so the queue overlay (if enabled) still draws onto a
     * clean canvas instead of whatever the visualizer last rendered. */
    private val _visualizerEnabled = MutableStateFlow(true)
    val visualizerEnabled: StateFlow<Boolean> = _visualizerEnabled.asStateFlow()

    private val _holdMode = MutableStateFlow<HoldMode>(HoldMode.Off)
    val holdMode: StateFlow<HoldMode> = _holdMode.asStateFlow()

    private val _stagedBpm = MutableStateFlow<Float?>(null)
    val stagedBpm: StateFlow<Float?> = _stagedBpm.asStateFlow()

    private val _stagedBeatsPerBar = MutableStateFlow<Int?>(null)
    val stagedBeatsPerBar: StateFlow<Int?> = _stagedBeatsPerBar.asStateFlow()

    private val _hasShownBpmHint = MutableStateFlow(false)
    val hasShownBpmHint: StateFlow<Boolean> = _hasShownBpmHint.asStateFlow()

    private val _muteProbability = MutableStateFlow(0f)
    val muteProbability: StateFlow<Float> = _muteProbability.asStateFlow()

    private val _progressiveMuteEnabled = MutableStateFlow(false)
    val progressiveMuteEnabled: StateFlow<Boolean> = _progressiveMuteEnabled.asStateFlow()

    @Volatile private var matrixSize = 25
    @Volatile private var lastBeatNanos = System.nanoTime()
    @Volatile private var beatIndex = 0
    @Volatile private var totalBeats = 0L
    @Volatile private var usingMidiClock = false
    @Volatile private var barsElapsedSincePlay = 0
    @Volatile private var random: Random = Random.Default

    /** A beats-per-bar change staged while latched/held, waiting for the next bar's downbeat to
     * actually land - see [onBeat]. Unlike BPM, it can't take effect mid-bar without a transient. */
    @Volatile private var pendingBeatsPerBarCommit: Int? = null

    private var renderJob: Job? = null
    private var persistBpmJob: Job? = null
    private var lastTapNanos = 0L
    private val tapIntervalsMs = mutableListOf<Long>()

    /** Loads persisted tempo/visualizer choice and wires up MIDI auto-switch/fallback. Safe to call multiple times from different entry points. */
    fun attach(context: Context) {
        if (settings != null) return
        val store = MetronomeSettings(context.applicationContext)
        settings = store
        _visualizer.value = VisualizerRegistry.byId(store.visualizerId)
        val restoredQueue = store.queue
        val restoredIndex = store.queueIndex.coerceIn(0, restoredQueue.size - 1)
        val restoredSpec = restoredQueue[restoredIndex]
        _timeSignatureQueue.value = restoredQueue
        _queueIndex.value = restoredIndex
        _queueMode.value = store.queueMode
        _timeSignature.value = restoredSpec
        _state.value = BeatPhase.IDLE.copy(bpm = restoredSpec.bpm, beatsPerBar = restoredSpec.beatCount)
        // The active bar's own visualizer choice (if it's ever pinned one) wins over the plain
        // last-used global visualizer restored above.
        restoredSpec.visualizerId?.let { _visualizer.value = VisualizerRegistry.byId(it) }
        _clickEnabled.value = store.clickEnabled
        _visualOffsetMs.value = store.visualOffsetMs
        _compactLandscape.value = store.compactLandscape
        _hasShownBpmHint.value = store.hasShownBpmHint
        _muteProbability.value = store.muteProbability
        _progressiveMuteEnabled.value = store.progressiveMuteEnabled
        _queueOverlayEnabled.value = store.queueOverlayEnabled
        _visualizerEnabled.value = store.visualizerEnabled

        MidiClockSource.onExternalActivity = { if (!usingMidiClock) useMidiClock() }
        MidiClockSource.onTransportStart = {
            if (!usingMidiClock) useMidiClock()
            start()
        }
        MidiClockSource.onTransportStop = ::stop
        emitIdleFrame()
    }

    /** Switches to following [MidiClockSource]. Called automatically once MIDI clock activity is seen. */
    fun useMidiClock() {
        if (usingMidiClock) return
        switchClockSource(MidiClockSource)
        usingMidiClock = true
        _clockStatus.value = ClockStatus.Midi(measuredBpm = null, source = MidiClockSource.activeSource?.name)
    }

    /** Stops following an external clock and resumes internal timing at the last known tempo. */
    fun useInternalClock() {
        if (!usingMidiClock) return
        switchClockSource(InternalClockSource())
        usingMidiClock = false
        _clockStatus.value = ClockStatus.Internal
    }

    private fun switchClockSource(source: ClockSource) {
        val wasPlaying = _state.value.isPlaying
        clock.stop()
        clock = source
        if (wasPlaying) clock.start(scope, _state.value.bpm, ::onBeat)
    }

    fun setMatrixSize(size: Int) {
        if (size > 0) {
            matrixSize = size
            emitIdleFrame()
        }
    }

    /**
     * The press-and-hold step buttons and the drag-to-scrub gesture can both call this many
     * times a second - the engine state update stays instant, but the SharedPreferences write
     * is debounced rather than firing on every single call, otherwise a few seconds of dragging
     * would queue hundreds of disk writes for no UI-visible benefit.
     *
     * While [holdMode] isn't [HoldMode.Off], the value is staged instead of applied - see
     * [beginHold]/[toggleLatch]/[flushStagedChanges].
     */
    fun setBpm(bpm: Float) {
        val clamped = bpm.coerceIn(MIN_BPM, MAX_BPM)
        if (_holdMode.value != HoldMode.Off) {
            _stagedBpm.value = clamped
            return
        }
        setBpmImmediate(clamped)
    }

    /** Tempo is per-bar, like beats-per-bar - this always writes into whichever bar is currently
     * active in the queue (see [goToQueueBar]), so navigating back to an earlier bar recalls the
     * tempo it was set at, not whatever the most recently-edited bar's tempo happened to be. */
    private fun setBpmImmediate(bpm: Float) {
        val clamped = bpm.coerceIn(MIN_BPM, MAX_BPM)
        clock.setBpm(clamped)
        _state.update { it.copy(bpm = clamped) }
        _timeSignature.value = _timeSignature.value.copy(bpm = clamped)
        _timeSignatureQueue.update { queue ->
            val index = _queueIndex.value
            if (index !in queue.indices) return@update queue
            queue.toMutableList().apply { this[index] = this[index].copy(bpm = clamped) }
        }
        persistBpmJob?.cancel()
        persistBpmJob = scope.launch {
            delay(BPM_PERSIST_DEBOUNCE_MS)
            settings?.bpm = clamped
            settings?.queue = _timeSignatureQueue.value
        }
        // The queue overlay's dot height reflects bpm, and this is the one function every
        // bpm-committing path (direct edits, goToQueueBar switching bars, the denominator
        // rescale) funnels through - refreshing here is what makes the glyph update immediately
        // while stopped, not just once playback starts. No-ops while playing (renderFrame owns
        // the frame then).
        emitIdleFrame()
    }

    /** Same staging behavior as [setBpm]; see [applyBeatsPerBarImmediate] for why a live change
     * commits at the next bar's downbeat instead of instantly. */
    fun setBeatsPerBar(count: Int) {
        val clamped = count.coerceIn(MIN_BEATS_PER_BAR, MAX_BEATS_PER_BAR)
        if (_holdMode.value != HoldMode.Off) {
            _stagedBeatsPerBar.value = clamped
            return
        }
        applyBeatsPerBarImmediate(clamped)
    }

    private fun applyBeatsPerBarImmediate(count: Int) {
        val clamped = count.coerceIn(MIN_BEATS_PER_BAR, MAX_BEATS_PER_BAR)
        _state.update { it.copy(beatsPerBar = clamped) }
        _timeSignature.value = _timeSignature.value.copy(beatCount = clamped)
        _timeSignatureQueue.update { queue ->
            val index = _queueIndex.value
            if (index !in queue.indices) return@update queue
            queue.toMutableList().apply { this[index] = this[index].copy(beatCount = clamped) }
        }
        settings?.beatsPerBar = clamped
        settings?.queue = _timeSignatureQueue.value
        // The queue overlay's dot width reflects beat count - see setBpmImmediate's matching call.
        emitIdleFrame()
    }

    /**
     * The time-signature *denominator* (e.g. the "4" in 4/4) - edited independently of
     * [beatCount][TimeSignature.beatCount]. The field itself is always applied immediately (no
     * mid-bar transient to stage around), but changing it rescales bpm to preserve the underlying
     * tempo - see [rescaledBpmForUnitNoteValueChange] - so e.g. switching a bar from 6/4 to 3/2
     * redistributes the same bar duration into 3 clicks instead of 6, rather than silently
     * doubling the felt tempo because "bpm" numerically didn't change.
     */
    fun setUnitNoteValue(value: Int) {
        val clamped = value.coerceIn(1, MAX_UNIT_NOTE_VALUE)
        val oldValue = _timeSignature.value.unitNoteValue
        if (clamped != oldValue) {
            // Read whatever bpm is currently in effect - staged if a hold is already staging one,
            // committed otherwise - so this rescale never computes from a stale base and then
            // clobbers an in-flight staged change out from under the user.
            val effectiveBpm = _stagedBpm.value ?: _state.value.bpm
            setBpm(rescaledBpmForUnitNoteValueChange(effectiveBpm, oldValue, clamped))
        }
        _timeSignature.value = _timeSignature.value.copy(unitNoteValue = clamped)
        _timeSignatureQueue.update { queue ->
            val index = _queueIndex.value
            if (index !in queue.indices) return@update queue
            queue.toMutableList().apply { this[index] = this[index].copy(unitNoteValue = clamped) }
        }
        settings?.queue = _timeSignatureQueue.value
    }

    /**
     * Tempo-preserving denominator math for [setUnitNoteValue]: rescales [currentBpm] so the
     * "pulse" (`bpm / unitNoteValue` - a tempo-invariant whole-notes-per-minute equivalent, e.g.
     * quarter note = 120bpm and half note = 60bpm are the same pulse, 30) is unchanged when the
     * denominator moves from [oldValue] to [newValue], instead of leaving the numeric bpm
     * untouched - which would silently change the felt tempo, since bpm is literally "clicks per
     * minute" regardless of note value. Clamped like every other bpm change. Exposed (not
     * private) so this is directly unit-testable without driving engine state, the same way
     * [nextQueueIndexAfterBar] is.
     */
    fun rescaledBpmForUnitNoteValueChange(currentBpm: Float, oldValue: Int, newValue: Int): Float {
        if (oldValue == newValue || oldValue <= 0) return currentBpm
        return (currentBpm * (newValue.toFloat() / oldValue.toFloat())).coerceIn(MIN_BPM, MAX_BPM)
    }

    /** Jumps directly to a bar in the queue (clamped to a valid index), making it - beats, note
     * value, and tempo alike - the active time signature immediately. Queue *position* isn't
     * staged the way editing a bar's own values is, since it's more "which page am I looking at"
     * than a pending settings change. */
    fun goToQueueBar(index: Int) {
        val queue = _timeSignatureQueue.value
        if (queue.isEmpty()) return
        val clampedIndex = index.coerceIn(0, queue.size - 1)
        _queueIndex.value = clampedIndex
        settings?.queueIndex = clampedIndex
        val spec = queue[clampedIndex]
        _timeSignature.value = spec
        _state.update { it.copy(beatsPerBar = spec.beatCount) }
        // Switch the visualizer *before* setBpmImmediate() below, which is what actually refreshes
        // the idle-frame preview while stopped - switching after would mean that refresh still
        // rendered with the outgoing visualizer.
        spec.visualizerId?.let { id ->
            val visualizer = VisualizerRegistry.byId(id)
            if (visualizer.id != _visualizer.value.id) {
                _visualizer.value = visualizer
                settings?.visualizerId = visualizer.id
            }
        }
        setBpmImmediate(spec.bpm)
    }

    /** Manual navigation - e.g. a swipe on the beats-per-bar control - one bar at a time,
     * clamped rather than wrapping (wrapping is [QueueMode.LOOP]'s job at a bar boundary). */
    fun nextQueueBar() = goToQueueBar(_queueIndex.value + 1)
    fun previousQueueBar() = goToQueueBar(_queueIndex.value - 1)

    /** Appends a copy of the currently-active bar to the end of the queue and jumps to it. */
    fun addBarToQueue() {
        val queue = _timeSignatureQueue.value
        val newBar = queue.getOrElse(_queueIndex.value) { TimeSignature.DEFAULT }
        _timeSignatureQueue.value = queue + newBar
        settings?.queue = _timeSignatureQueue.value
        goToQueueBar(queue.size)
    }

    /**
     * Removes a specific bar from the queue (e.g. long-pressing its dot) - a no-op if it's the
     * only one left, since the queue (and therefore the active time signature) can never be
     * empty, or if [index] is out of range. Removing a bar *other* than the active one keeps the
     * same bar active (adjusting for the shift); removing the active one itself lands on
     * whichever bar now occupies its old slot, clamped to the new last bar if it was the end.
     */
    fun removeBarFromQueue(index: Int) {
        val queue = _timeSignatureQueue.value
        if (queue.size <= 1 || index !in queue.indices) return
        val updated = queue.toMutableList().apply { removeAt(index) }
        _timeSignatureQueue.value = updated
        settings?.queue = updated
        val activeIndex = _queueIndex.value
        val newActiveIndex = if (index < activeIndex) activeIndex - 1 else activeIndex
        goToQueueBar(newActiveIndex.coerceAtMost(updated.size - 1))
    }

    /** Removes the currently-active bar - see [removeBarFromQueue]. */
    fun removeCurrentBarFromQueue() = removeBarFromQueue(_queueIndex.value)

    /** Collapses the whole queue back to a single default bar and [QueueMode.LOOP] - the "start
     * over" counterpart to building up a queue one [addBarToQueue] at a time, for when a
     * performer wants a clean slate rather than removing bars one by one. */
    fun resetQueueToDefault() {
        _timeSignatureQueue.value = listOf(TimeSignature.DEFAULT)
        settings?.queue = _timeSignatureQueue.value
        _queueMode.value = QueueMode.LOOP
        settings?.queueMode = QueueMode.LOOP
        goToQueueBar(0)
    }

    fun setQueueMode(mode: QueueMode) {
        _queueMode.value = mode
        settings?.queueMode = mode
    }

    fun setQueueOverlayEnabled(enabled: Boolean) {
        _queueOverlayEnabled.value = enabled
        settings?.queueOverlayEnabled = enabled
        emitIdleFrame()
    }

    fun setVisualizerEnabled(enabled: Boolean) {
        _visualizerEnabled.value = enabled
        settings?.visualizerEnabled = enabled
        emitIdleFrame()
    }

    /**
     * Pure decision logic for [advanceQueueAtBarBoundary] - returns the index to advance to, or
     * null if nothing should change ([QueueMode.MANUAL], a single-entry queue, or [QueueMode.ONCE]
     * having already reached the last bar - it stays there rather than stopping playback
     * outright, since the performer decides when to actually stop). Exposed (not private) so the
     * decision is directly unit-testable without driving a real beat loop.
     */
    fun nextQueueIndexAfterBar(currentIndex: Int, queueSize: Int, mode: QueueMode): Int? {
        if (queueSize <= 1) return null
        if (mode == QueueMode.MANUAL) return null
        val next = currentIndex + 1
        return when {
            next < queueSize -> next
            mode == QueueMode.LOOP -> 0
            else -> null // ONCE: stay on the last bar
        }
    }

    /** Called at each bar boundary (see [onBeat]) once the *previous* bar has fully played out. */
    private fun advanceQueueAtBarBoundary() {
        val queue = _timeSignatureQueue.value
        val next = nextQueueIndexAfterBar(_queueIndex.value, queue.size, _queueMode.value) ?: return
        goToQueueBar(next)
    }

    /** Finger down on HOLD: starts staging BPM/beats-per-bar changes. No-op if already staging
     * (i.e. already [HoldMode.Momentary] or [HoldMode.Latched]). */
    fun beginHold() {
        if (_holdMode.value != HoldMode.Off) return
        _holdMode.value = HoldMode.Momentary
        _stagedBpm.value = _state.value.bpm
        _stagedBeatsPerBar.value = _state.value.beatsPerBar
    }

    /** Finger up after a momentary hold: flushes staged changes and stops staging. While
     * [HoldMode.Latched], this is a no-op - only [toggleLatch] can end a latch. */
    fun endHold() {
        if (_holdMode.value != HoldMode.Momentary) return
        flushStagedChanges()
        _holdMode.value = HoldMode.Off
    }

    /** Promotes momentary staging to a sticky latch (long-press/double-tap on HOLD), or ends an
     * existing latch and flushes (a subsequent tap on HOLD). */
    fun toggleLatch() {
        when (_holdMode.value) {
            HoldMode.Off -> {
                _holdMode.value = HoldMode.Latched
                _stagedBpm.value = _state.value.bpm
                _stagedBeatsPerBar.value = _state.value.beatsPerBar
            }
            HoldMode.Momentary -> _holdMode.value = HoldMode.Latched
            HoldMode.Latched -> {
                flushStagedChanges()
                _holdMode.value = HoldMode.Off
            }
        }
    }

    /**
     * Applies staged BPM immediately (a new tempo only ever takes effect on the next tick
     * anyway, so there's no mid-bar transient to avoid) and applies staged beats-per-bar
     * immediately if stopped, or defers it to the next bar's downbeat if playing - see [onBeat].
     */
    private fun flushStagedChanges() {
        val stagedBpmValue = _stagedBpm.value
        if (stagedBpmValue != null) {
            if (stagedBpmValue != _state.value.bpm) setBpmImmediate(stagedBpmValue)
            _stagedBpm.value = null
        }
        val stagedBeats = _stagedBeatsPerBar.value ?: return
        when {
            stagedBeats == _state.value.beatsPerBar -> _stagedBeatsPerBar.value = null
            _state.value.isPlaying -> pendingBeatsPerBarCommit = stagedBeats
            else -> {
                applyBeatsPerBarImmediate(stagedBeats)
                _stagedBeatsPerBar.value = null
            }
        }
    }

    fun setClickEnabled(enabled: Boolean) {
        _clickEnabled.value = enabled
        settings?.clickEnabled = enabled
    }

    fun setVisualOffsetMs(ms: Float) {
        val clamped = ms.coerceIn(-500f, 500f)
        _visualOffsetMs.value = clamped
        settings?.visualOffsetMs = clamped
    }

    fun setCompactLandscape(enabled: Boolean) {
        _compactLandscape.value = enabled
        settings?.compactLandscape = enabled
    }

    /** Marks the one-time BPM-number gesture hint as shown, so it never appears again. */
    fun markBpmHintShown() {
        _hasShownBpmHint.value = true
        settings?.hasShownBpmHint = true
    }

    /** Chance (0..1) that any given beat's click is skipped - see [onBeat]. Only the audible
     * click is affected; beat position, phase and visuals are untouched, so bar length and the
     * visual cue never desync from a muted click. */
    fun setMuteProbability(probability: Float) {
        val clamped = probability.coerceIn(0f, 1f)
        _muteProbability.value = clamped
        settings?.muteProbability = clamped
    }

    /** When enabled, [muteProbability] ramps up linearly from 0 over [PROGRESSIVE_MUTE_RAMP_BARS]
     * bars after [start] instead of applying at full strength immediately. */
    fun setProgressiveMuteEnabled(enabled: Boolean) {
        _progressiveMuteEnabled.value = enabled
        settings?.progressiveMuteEnabled = enabled
    }

    /** For tests only - makes mute-probability rolls deterministic. */
    fun seedRandomForTesting(seed: Long) {
        random = Random(seed)
    }

    /** The mute chance actually in effect [barsElapsed] bars into playback - the configured
     * target immediately, or ramped up linearly from 0 over [PROGRESSIVE_MUTE_RAMP_BARS] bars
     * when progressive start is on. Exposed (not private) so it's directly unit-testable without
     * needing to drive a real beat loop or intercept [ClickPlayer]. */
    fun effectiveMuteProbability(barsElapsed: Int): Float {
        val target = _muteProbability.value
        return if (_progressiveMuteEnabled.value && barsElapsed < PROGRESSIVE_MUTE_RAMP_BARS) {
            target * (barsElapsed / PROGRESSIVE_MUTE_RAMP_BARS.toFloat())
        } else {
            target
        }
    }

    /** Picking a visualizer also pins it to whichever bar is currently active - see
     * [TimeSignature.visualizerId] - the same "always edits whichever bar is active" pattern
     * [setBpm]/[setBeatsPerBar] already follow. */
    fun setVisualizer(visualizer: GlyphVisualizer) {
        _visualizer.value = visualizer
        settings?.visualizerId = visualizer.id
        _timeSignature.value = _timeSignature.value.copy(visualizerId = visualizer.id)
        _timeSignatureQueue.update { queue ->
            val index = _queueIndex.value
            if (index !in queue.indices) return@update queue
            queue.toMutableList().apply { this[index] = this[index].copy(visualizerId = visualizer.id) }
        }
        settings?.queue = _timeSignatureQueue.value
        emitIdleFrame()
    }

    fun nextVisualizer() {
        setVisualizer(VisualizerRegistry.next(_visualizer.value))
    }

    fun previousVisualizer() {
        setVisualizer(VisualizerRegistry.previous(_visualizer.value))
    }

    /**
     * Starts ticking. If [BeatPhase.isPlaying] is already true but the render loop has died
     * (e.g. a past unhandled exception, or a stale state left over from an anomalous Glyph
     * Toy rebind), this notices the render job isn't actually alive and restarts cleanly
     * instead of trusting the flag and no-opping forever.
     */
    fun start() {
        if (_state.value.isPlaying && renderJob?.isActive == true) return
        beatIndex = 0
        totalBeats = 0
        barsElapsedSincePlay = 0
        goToQueueBar(0)
        lastBeatNanos = System.nanoTime()
        _state.update { it.copy(isPlaying = true, beatIndex = 0, phase = 0f, isAccent = true) }
        clock.start(scope, _state.value.bpm, ::onBeat)
        startRenderLoop()
    }

    /**
     * Stops ticking. Also force-clears any hold/latch in progress, flushing staged BPM and
     * committing any beats-per-bar change waiting on a bar boundary that will now never arrive -
     * staging only makes sense during a live, attended session, so a "stuck red" latch surviving
     * past the end of playback would be wrong, not just stale.
     */
    fun stop() {
        clock.stop()
        renderJob?.cancel()
        renderJob = null
        _state.update { it.copy(isPlaying = false, phase = 0f) }
        pendingBeatsPerBarCommit?.let {
            applyBeatsPerBarImmediate(it)
            pendingBeatsPerBarCommit = null
        }
        if (_holdMode.value != HoldMode.Off) {
            flushStagedChanges()
            _holdMode.value = HoldMode.Off
        }
        emitIdleFrame()
    }

    fun toggle() {
        if (_state.value.isPlaying) stop() else start()
    }

    /**
     * Registers a tap (e.g. a UI button press or a Glyph Button touch-down) and derives BPM from
     * the rolling average of the last few tap intervals. Starts the engine once a usable tempo
     * has been established.
     */
    fun tapTempo(nowNanos: Long = System.nanoTime()) {
        val previous = lastTapNanos
        lastTapNanos = nowNanos
        if (previous == 0L) return
        val intervalMs = (nowNanos - previous) / 1_000_000
        if (intervalMs > MAX_TAP_GAP_MS || intervalMs <= 0) {
            tapIntervalsMs.clear()
            return
        }
        tapIntervalsMs.add(intervalMs)
        if (tapIntervalsMs.size > MAX_TAP_SAMPLES) tapIntervalsMs.removeAt(0)
        val averageMs = tapIntervalsMs.average()
        setBpm((60_000.0 / averageMs).roundToInt().toFloat())
        if (!_state.value.isPlaying) start()
    }

    fun release() {
        stop()
        clickPlayer.release()
    }

    /** Renders the current visualizer at its beat-1 resting pose, scaled to [IDLE_GLYPH_SCALE]
     * brightness, so the preview glows faintly on AMOLED when the metronome isn't running. The
     * queue overlay is applied *after* that dimming, at its own full computed brightness, rather
     * than also being scaled down to near-invisibility - the whole point of showing it while idle
     * is so browsing the queue (switching bars, editing a bar's beats/tempo) stays visible on the
     * glyph without having to start playback first. */
    private fun emitIdleFrame() {
        if (_state.value.isPlaying) return
        val idleBeat = _state.value.copy(phase = 0f, isAccent = true, isPlaying = false)
        val rendered = if (!_visualizerEnabled.value) {
            IntArray(matrixSize * matrixSize)
        } else {
            try {
                _visualizer.value.render(matrixSize, idleBeat)
            } catch (_: Exception) {
                IntArray(matrixSize * matrixSize) { 255 }
            }
        }
        val dimmed = IntArray(rendered.size) { i ->
            (rendered[i] * IDLE_GLYPH_SCALE).toInt().coerceIn(0, 255)
        }
        _frame.value = withQueueOverlay(dimmed, idleBeat.beatIndex, idleBeat.phase)
    }

    /** Resets all state back to defaults. For tests only - this is a process-wide singleton, so state would otherwise leak between test cases. */
    fun resetForTesting() {
        stop()
        persistBpmJob?.cancel()
        persistBpmJob = null
        clock = InternalClockSource()
        usingMidiClock = false
        settings = null
        _visualizer.value = VisualizerRegistry.default
        _state.value = BeatPhase.IDLE
        _clockStatus.value = ClockStatus.Internal
        _clickEnabled.value = false
        _visualOffsetMs.value = 0f
        _compactLandscape.value = false
        _hasShownBpmHint.value = false
        _muteProbability.value = 0f
        _progressiveMuteEnabled.value = false
        _timeSignature.value = TimeSignature.DEFAULT
        _timeSignatureQueue.value = listOf(TimeSignature.DEFAULT)
        _queueIndex.value = 0
        _queueMode.value = QueueMode.LOOP
        _queueOverlayEnabled.value = true
        _visualizerEnabled.value = true
        _holdMode.value = HoldMode.Off
        _stagedBpm.value = null
        _stagedBeatsPerBar.value = null
        pendingBeatsPerBarCommit = null
        barsElapsedSincePlay = 0
        random = Random.Default
        lastTapNanos = 0L
        tapIntervalsMs.clear()
        MidiClockSource.resetForTesting()
    }

    private fun onBeat(timestampNanos: Long, measuredBpm: Float?) {
        lastBeatNanos = timestampNanos
        if (beatIndex == 0) {
            pendingBeatsPerBarCommit?.let {
                applyBeatsPerBarImmediate(it)
                _stagedBeatsPerBar.value = null
                pendingBeatsPerBarCommit = null
            }
            // Skip the very first bar's downbeat (totalBeats == 0) - start() already put the
            // queue on bar 0, so this only fires once a bar has actually completed.
            if (totalBeats > 0) advanceQueueAtBarBoundary()
        }
        val isAccent = _timeSignature.value.isAccented(beatIndex)
        val index = beatIndex
        val count = totalBeats
        _state.update {
            it.copy(
                bpm = measuredBpm?.coerceIn(MIN_BPM, MAX_BPM) ?: it.bpm,
                beatIndex = index,
                totalBeats = count,
                isAccent = isAccent,
                phase = 0f,
            )
        }
        if (usingMidiClock) {
            _clockStatus.value = ClockStatus.Midi(measuredBpm, MidiClockSource.activeSource?.name)
        }
        val probability = effectiveMuteProbability(barsElapsedSincePlay)
        val muted = probability > 0f && random.nextFloat() < probability
        if (_clickEnabled.value && !muted) {
            try {
                clickPlayer.playClick(if (isAccent) ClickSound.BAR else ClickSound.REGULAR)
            } catch (e: Exception) {
                Log.e(TAG, "ClickPlayer failed; continuing without audio for this beat", e)
            }
        }
        if (beatIndex == 0) barsElapsedSincePlay++
        totalBeats++
        beatIndex = (beatIndex + 1) % _state.value.beatsPerBar.coerceAtLeast(1)
    }

    private fun startRenderLoop() {
        renderJob?.cancel()
        renderJob = scope.launch {
            while (isActive) {
                renderFrame()
                delay(FRAME_INTERVAL_MS)
            }
        }
    }

    private fun renderFrame() {
        val current = _state.value
        if (!current.isPlaying) return
        val intervalNanos = 60_000_000_000.0 / current.bpm
        val rawElapsedNanos = System.nanoTime() - lastBeatNanos
        if (usingMidiClock && rawElapsedNanos > intervalNanos * MIDI_SILENCE_BEATS) {
            useInternalClock()
        }
        val offsetNanos = _visualOffsetMs.value * 1_000_000.0
        val elapsedNanos = (rawElapsedNanos + offsetNanos).coerceAtLeast(0.0)
        val phase = (elapsedNanos / intervalNanos).toFloat().coerceIn(0f, 1f)
        val phaseState = current.copy(phase = phase)
        _state.value = phaseState
        // A misbehaving visualizer (bad matrixSize handling, NaN math, etc.) must not be able to
        // kill the render loop coroutine - that would silently wedge isPlaying=true forever with
        // nothing actually ticking. Skip the frame and keep going instead.
        try {
            val base = if (_visualizerEnabled.value) {
                _visualizer.value.render(matrixSize, phaseState)
            } else {
                IntArray(matrixSize * matrixSize)
            }
            _frame.value = withQueueOverlay(base, phaseState.beatIndex, phaseState.phase)
        } catch (e: Exception) {
            Log.e(TAG, "Visualizer '${_visualizer.value.id}' threw during render(); skipping frame", e)
        }
    }

    /** Bakes the ambient per-bar/per-beat background (see [QueueOverlay]) into an already-rendered
     * frame - a no-op when there's nothing to indicate, so a single-entry queue (the common case)
     * leaves every visualizer's own frame completely untouched. */
    private fun withQueueOverlay(rendered: IntArray, beatIndex: Int, phase: Float): IntArray {
        val queue = _timeSignatureQueue.value
        if (queue.size <= 1 || !_queueOverlayEnabled.value) return rendered
        return QueueOverlay.apply(rendered, matrixSize, queue, _queueIndex.value, beatIndex, phase, MIN_BPM, MAX_BPM)
    }

    private const val TAG = "MetronomeEngine"
    private const val MAX_TAP_GAP_MS = 2000L
    private const val MAX_TAP_SAMPLES = 5
    private const val FRAME_INTERVAL_MS = 25L
    private const val BPM_PERSIST_DEBOUNCE_MS = 250L

    /** How many beat-intervals of silence from the MIDI clock before falling back to internal timing. */
    private const val MIDI_SILENCE_BEATS = 4

    /** Fraction of full brightness used for the idle glyph preview when the metronome is stopped. */
    private const val IDLE_GLYPH_SCALE = 0.06f

    /** How many bars a progressive mute ramp takes to reach its configured target probability. */
    private const val PROGRESSIVE_MUTE_RAMP_BARS = 8

    /** Upper bound for [setUnitNoteValue] - generous enough for any real time signature (32nd
     * notes) without pretending every integer in between is a musically meaningful denominator. */
    const val MAX_UNIT_NOTE_VALUE = 32
}
