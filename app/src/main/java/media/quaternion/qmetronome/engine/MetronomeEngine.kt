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

    /** Bounds in effect when [extendedBpmRangeEnabled] is on - generous, round numbers covering
     * anything from a near-static drone pulse to a tremolo-fast click, displayed as BPH/BPS (see
     * `bpmDisplayValue`/`bpmDisplayUnit` in `MainScreen.kt`) rather than a literal "0.1 BPM". */
    const val EXTENDED_MIN_BPM = 0.1f
    const val EXTENDED_MAX_BPM = 3000f

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

    private val scope = CoroutineScope(SupervisorJob() + exceptionHandler + newTimingDispatcher("metronome-timing"))
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

    /** Mirrors [clickPlayer]'s own specs so Settings can observe/edit them - [clickPlayer] itself
     * isn't a `StateFlow`-based class, since it also owns real `AudioTrack` resources. */
    private val _clickSpecs = MutableStateFlow(ClickSound.entries.associateWith(ClickSpec::defaultFor))
    val clickSpecs: StateFlow<Map<ClickSound, ClickSpec>> = _clickSpecs.asStateFlow()

    private val _visualOffsetMs = MutableStateFlow(DEFAULT_VISUAL_OFFSET_MS)
    val visualOffsetMs: StateFlow<Float> = _visualOffsetMs.asStateFlow()

    /** See [setAudioOffsetMs] for how this is actually scheduled - a negative value needs genuine
     * lookahead (see [startAudioLookahead]), unlike [visualOffsetMs]'s continuous phase-shift. */
    private val _audioOffsetMs = MutableStateFlow(DEFAULT_AUDIO_OFFSET_MS)
    val audioOffsetMs: StateFlow<Float> = _audioOffsetMs.asStateFlow()

    private val _compactLandscape = MutableStateFlow(false)
    val compactLandscape: StateFlow<Boolean> = _compactLandscape.asStateFlow()

    /** When on, the main screen's live tempo/transport cluster (`TempoTransportCluster` in
     * `MainScreen.kt`, also embedded in Settings' "Tempo & Bars" mirror) drops every text label
     * (unit text, "staged" text) in favor of icons/dots only - see the composables themselves for
     * what each one swaps to. Off by default, matching every other display toggle in this app. */
    private val _symbolicControlsEnabled = MutableStateFlow(false)
    val symbolicControlsEnabled: StateFlow<Boolean> = _symbolicControlsEnabled.asStateFlow()

    /** See [MetronomeSettings.persistentModeEnabled] - watched by `QMetronomeApp` to start/stop
     * `PersistentPlaybackService`, and by `MetronomeGlyphService` to decide whether a Glyph Toy
     * unbind should stop playback. */
    private val _persistentModeEnabled = MutableStateFlow(false)
    val persistentModeEnabled: StateFlow<Boolean> = _persistentModeEnabled.asStateFlow()

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

    /** How many bars [progressiveMuteEnabled]'s ramp takes to reach its target probability - the
     * ramp's slope, in effect, since it's linear from 0. Tunable rather than the fixed 8-bar
     * constant this used to be. */
    private val _progressiveMuteRampBars = MutableStateFlow(DEFAULT_PROGRESSIVE_MUTE_RAMP_BARS)
    val progressiveMuteRampBars: StateFlow<Int> = _progressiveMuteRampBars.asStateFlow()

    /** When on, [setBpm]/[setBpmImmediate] clamp against [EXTENDED_MIN_BPM]/[EXTENDED_MAX_BPM]
     * instead of [MIN_BPM]/[MAX_BPM] - an explicit opt-in since the vast majority of tempos anyone
     * ever wants live inside the normal range, and a wide-open default range would make the BPM
     * number's drag-to-scrub gesture far less precise for everyday use. */
    private val _extendedBpmRangeEnabled = MutableStateFlow(false)
    val extendedBpmRangeEnabled: StateFlow<Boolean> = _extendedBpmRangeEnabled.asStateFlow()

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
    private var audioLookaheadJob: Job? = null
    private var persistBpmJob: Job? = null
    private var lastTapNanos = 0L
    private val tapIntervalsMs = mutableListOf<Long>()

    /** A one-shot mute/sound decision for beat [totalBeats] (see [ResolvedBeatAudio]), resolved
     * either early by [startAudioLookahead] or on-time by [onBeat] itself - whichever gets there
     * first. Guarded by [audioResolutionLock] rather than relying on `@Volatile` alone: both the
     * lookahead loop and [onBeat] run as independent coroutines that can genuinely execute on
     * different threads (this object's [scope] has no dispatcher of its own, so `launch` falls
     * back to `Dispatchers.Default`'s thread pool), and resolving involves a non-atomic
     * check-then-set plus a single [random] roll that must happen exactly once per beat. */
    @Volatile private var resolvedBeatAudio: ResolvedBeatAudio? = null
    private val audioResolutionLock = Any()

    /** For tests only - lets a test observe exactly when/what audio actually fired without
     * depending on [ClickPlayer]'s real `AudioTrack` side effects, which Robolectric can't
     * meaningfully assert on. Mirrors [seedRandomForTesting]'s existing testing-seam precedent. */
    @Volatile private var clickListenerForTesting: ((ClickSound, Long) -> Unit)? = null

    /** One resolved beat's audio decision - which [ClickSound] to play, or `null` if this beat is
     * muted/click-disabled - cached by [totalBeats] (not by timestamp: a lookahead-predicted
     * timestamp and the real one can differ by a hair, but the beat counter is an exact integer,
     * which is what actually prevents a double-fire or a missed one). */
    private data class ResolvedBeatAudio(val totalBeats: Long, val sound: ClickSound?)

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
        val restoredSpecs = ClickSound.entries.associateWith { store.clickSpec(it) }
        restoredSpecs.forEach { (sound, spec) -> clickPlayer.setSpec(sound, spec) }
        _clickSpecs.value = restoredSpecs
        _visualOffsetMs.value = store.visualOffsetMs
        _audioOffsetMs.value = store.audioOffsetMs
        _compactLandscape.value = store.compactLandscape
        _symbolicControlsEnabled.value = store.symbolicControlsEnabled
        _persistentModeEnabled.value = store.persistentModeEnabled
        _hasShownBpmHint.value = store.hasShownBpmHint
        _muteProbability.value = store.muteProbability
        _progressiveMuteEnabled.value = store.progressiveMuteEnabled
        _progressiveMuteRampBars.value = store.progressiveMuteRampBars
        _extendedBpmRangeEnabled.value = store.extendedBpmRangeEnabled
        _queueOverlayEnabled.value = store.queueOverlayEnabled
        _visualizerEnabled.value = store.visualizerEnabled

        MidiClockSource.onExternalActivity = { if (!usingMidiClock) useMidiClock() }
        MidiClockSource.onTransportStart = {
            if (!usingMidiClock) useMidiClock()
            start(primeVisualFlash = false)
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
        val clamped = bpm.coerceIn(effectiveMinBpm(), effectiveMaxBpm())
        if (_holdMode.value != HoldMode.Off) {
            _stagedBpm.value = clamped
            return
        }
        setBpmImmediate(clamped)
    }

    /** The bpm bounds currently in effect - [EXTENDED_MIN_BPM]/[EXTENDED_MAX_BPM] when
     * [extendedBpmRangeEnabled] is on, [MIN_BPM]/[MAX_BPM] otherwise. */
    private fun effectiveMinBpm(): Float = if (_extendedBpmRangeEnabled.value) EXTENDED_MIN_BPM else MIN_BPM
    private fun effectiveMaxBpm(): Float = if (_extendedBpmRangeEnabled.value) EXTENDED_MAX_BPM else MAX_BPM

    /** Tempo is per-bar, like beats-per-bar - this always writes into whichever bar is currently
     * active in the queue (see [goToQueueBar]), so navigating back to an earlier bar recalls the
     * tempo it was set at, not whatever the most recently-edited bar's tempo happened to be. */
    private fun setBpmImmediate(bpm: Float) {
        val clamped = bpm.coerceIn(effectiveMinBpm(), effectiveMaxBpm())
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
        return (currentBpm * (newValue.toFloat() / oldValue.toFloat())).coerceIn(effectiveMinBpm(), effectiveMaxBpm())
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

    fun setClickSpec(sound: ClickSound, spec: ClickSpec) {
        clickPlayer.setSpec(sound, spec)
        _clickSpecs.update { it + (sound to spec) }
        settings?.setClickSpec(sound, spec)
    }

    fun setVisualOffsetMs(ms: Float) {
        val clamped = ms.coerceIn(-500f, 500f)
        _visualOffsetMs.value = clamped
        settings?.visualOffsetMs = clamped
    }

    /** A negative value leads the click ahead of the true beat via genuine lookahead scheduling
     * (see [startAudioLookahead]); zero/positive delays it, scheduled off the beat's own real
     * timestamp (see [scheduleReactiveAudio]) rather than a naive relative `delay()` that could
     * drift under scheduling pressure. */
    fun setAudioOffsetMs(ms: Float) {
        val clamped = ms.coerceIn(-500f, 500f)
        _audioOffsetMs.value = clamped
        settings?.audioOffsetMs = clamped
    }

    fun setCompactLandscape(enabled: Boolean) {
        _compactLandscape.value = enabled
        settings?.compactLandscape = enabled
    }

    fun setSymbolicControlsEnabled(enabled: Boolean) {
        _symbolicControlsEnabled.value = enabled
        settings?.symbolicControlsEnabled = enabled
    }

    fun setPersistentModeEnabled(enabled: Boolean) {
        _persistentModeEnabled.value = enabled
        settings?.persistentModeEnabled = enabled
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

    /** When enabled, [muteProbability] ramps up linearly from 0 over [progressiveMuteRampBars]
     * bars after [start] instead of applying at full strength immediately. */
    fun setProgressiveMuteEnabled(enabled: Boolean) {
        _progressiveMuteEnabled.value = enabled
        settings?.progressiveMuteEnabled = enabled
    }

    /** Adjusts the ramp's slope - how many bars it takes to reach the target probability. */
    fun setProgressiveMuteRampBars(bars: Int) {
        val clamped = bars.coerceIn(MIN_PROGRESSIVE_MUTE_RAMP_BARS, MAX_PROGRESSIVE_MUTE_RAMP_BARS)
        _progressiveMuteRampBars.value = clamped
        settings?.progressiveMuteRampBars = clamped
    }

    /** Toggles [EXTENDED_MIN_BPM]/[EXTENDED_MAX_BPM] as the active clamp range. Turning it off
     * immediately re-clamps the live bpm back inside [MIN_BPM]/[MAX_BPM] - otherwise a tempo set
     * while extended (e.g. 900 bpm) would sit stuck above every other control's range with no way
     * to move it back in range without a fresh extended edit. */
    fun setExtendedBpmRangeEnabled(enabled: Boolean) {
        _extendedBpmRangeEnabled.value = enabled
        settings?.extendedBpmRangeEnabled = enabled
        if (!enabled) setBpmImmediate(_state.value.bpm)
    }

    /** For tests only - makes mute-probability rolls deterministic. */
    fun seedRandomForTesting(seed: Long) {
        random = Random(seed)
    }

    /** For tests only - see [clickListenerForTesting]. */
    fun setClickListenerForTesting(listener: ((ClickSound, Long) -> Unit)?) {
        clickListenerForTesting = listener
    }

    /** The mute chance actually in effect [barsElapsed] bars into playback - the configured
     * target immediately, or ramped up linearly from 0 over [progressiveMuteRampBars] bars
     * when progressive start is on. Exposed (not private) so it's directly unit-testable without
     * needing to drive a real beat loop or intercept [ClickPlayer]. */
    fun effectiveMuteProbability(barsElapsed: Int): Float {
        val target = _muteProbability.value
        val rampBars = _progressiveMuteRampBars.value
        return if (_progressiveMuteEnabled.value && barsElapsed < rampBars) {
            target * (barsElapsed / rampBars.toFloat())
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
     *
     * [primeVisualFlash] fires an immediate "bar" flash (`beatIndex = 0, phase = 0f,
     * isAccent = true`, plus resetting [lastBeatNanos] to now so the render loop's decay
     * animation starts from this instant) so the display doesn't sit blank while waiting for the
     * first beat. That's correct for [InternalClockSource], whose real first [onBeat] fires within
     * microseconds of this call - the priming flash and the real one are visually the same event.
     * It's wrong for a MIDI-driven start ([MidiClockSource.onTransportStart], wired in [attach]):
     * the real first beat there only arrives after 24 clock ticks actually elapse, roughly a full
     * beat later, so priming here would play out one whole "bar" decay animation and then play a
     * second, identical-looking one when the real beat lands - two bar flashes where there should
     * be one. Passing `false` skips the priming and leaves [lastBeatNanos] stale, so the render
     * loop reads a saturated, fully-decayed/resting frame until the real tick-driven [onBeat] call
     * supplies the one correct flash.
     */
    fun start(primeVisualFlash: Boolean = true) {
        if (_state.value.isPlaying && renderJob?.isActive == true) return
        beatIndex = 0
        totalBeats = 0
        barsElapsedSincePlay = 0
        resolvedBeatAudio = null
        goToQueueBar(0)
        if (primeVisualFlash) {
            lastBeatNanos = System.nanoTime()
            _state.update { it.copy(isPlaying = true, beatIndex = 0, phase = 0f, isAccent = true) }
        } else {
            _state.update { it.copy(isPlaying = true) }
        }
        clock.start(scope, _state.value.bpm, ::onBeat)
        startRenderLoop()
        startAudioLookahead()
    }

    /**
     * Stops ticking. Also force-clears any hold/latch in progress, flushing staged BPM and
     * committing any beats-per-bar change waiting on a bar boundary that will now never arrive -
     * staging only makes sense during a live, attended session, so a "stuck red" latch surviving
     * past the end of playback would be wrong, not just stale.
     */
    fun stop() {
        clock.stop()
        // Flipped first, before cancelling anything - playClickSafely's own isPlaying check is
        // what actually stops an in-flight reactive delay (scheduleReactiveAudio's one-off
        // coroutine isn't tracked/cancelled the way renderJob/audioLookaheadJob are) or a
        // lookahead-loop iteration that was already past its gating check from firing audio after
        // stop was pressed - flipping this as early as possible narrows that race to a minimum.
        _state.update { it.copy(isPlaying = false, phase = 0f) }
        renderJob?.cancel()
        renderJob = null
        audioLookaheadJob?.cancel()
        audioLookaheadJob = null
        resolvedBeatAudio = null
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
     * the rolling average of the last few tap intervals. Decoupled from playback - tapping alone
     * is a pure tempo-dialing gesture, usable while stopped without triggering play, the same way
     * dragging or stepping the BPM number doesn't start anything either. The one exception: while
     * [HoldMode.Latched], a tap that produces a real interval (by construction, the *second or
     * later* tap - the first always returns early below with no interval yet) is a deliberate
     * "commit this staged tempo and go" gesture - flushes the tap-derived bpm (now staged, same
     * as any other change while latched) and any staged beats-per-bar, clears the latch, and
     * starts at the resulting tempo/time signature.
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
        if (_holdMode.value == HoldMode.Latched) {
            flushStagedChanges()
            _holdMode.value = HoldMode.Off
            if (!_state.value.isPlaying) start()
        }
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
        // No separate defensive copy needed before publishing here (unlike renderFrame()) - this
        // transform itself always allocates a brand new array, never publishing a GlyphCanvas-
        // pooled buffer (see GlyphCanvas.BufferPool) directly.
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
        ClickSound.entries.forEach { clickPlayer.setSpec(it, ClickSpec.defaultFor(it)) }
        _clickSpecs.value = ClickSound.entries.associateWith(ClickSpec::defaultFor)
        _visualOffsetMs.value = DEFAULT_VISUAL_OFFSET_MS
        _audioOffsetMs.value = DEFAULT_AUDIO_OFFSET_MS
        resolvedBeatAudio = null
        clickListenerForTesting = null
        _compactLandscape.value = false
        _symbolicControlsEnabled.value = false
        _persistentModeEnabled.value = false
        _hasShownBpmHint.value = false
        _muteProbability.value = 0f
        _progressiveMuteEnabled.value = false
        _progressiveMuteRampBars.value = DEFAULT_PROGRESSIVE_MUTE_RAMP_BARS
        _extendedBpmRangeEnabled.value = false
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
                bpm = measuredBpm?.coerceIn(effectiveMinBpm(), effectiveMaxBpm()) ?: it.bpm,
                beatIndex = index,
                totalBeats = count,
                isAccent = isAccent,
                phase = 0f,
            )
        }
        if (usingMidiClock) {
            _clockStatus.value = ClockStatus.Midi(measuredBpm, MidiClockSource.activeSource?.name)
        }
        // Reuse the lookahead loop's resolution if it already got there first (and, in doing so,
        // already fired the click) - otherwise resolve fresh, right now, and schedule its audio
        // reactively. Either way this must resolve exactly once per beat - see [ResolvedBeatAudio].
        val (resolved, alreadyFired) = synchronized(audioResolutionLock) {
            val cached = resolvedBeatAudio?.takeIf { it.totalBeats == count }
            resolvedBeatAudio = null
            if (cached != null) cached to true else resolveBeatAudio(count, index) to false
        }
        if (!alreadyFired) {
            resolved.sound?.let { scheduleReactiveAudio(it, timestampNanos) }
        }
        if (beatIndex == 0) barsElapsedSincePlay++
        totalBeats++
        beatIndex = (beatIndex + 1) % _state.value.beatsPerBar.coerceAtLeast(1)
    }

    /** Resolves whether beat [totalBeatsForBeat] (at bar position [beatIndexForBeat]) should play
     * a click, and if so which [ClickSound] - the one place [effectiveMuteProbability]'s roll and
     * the beat-vs-accent-vs-regular sound choice happen, called by whichever of [onBeat] or the
     * audio lookahead loop ([startAudioLookahead]) gets there first for a given beat. Beat 0 is
     * unconditionally [ClickSound.BAR] regardless of a bar-queue advance that may not have
     * happened yet when this runs ahead of time; a non-zero beat's accent comes from the *current*
     * time signature, which by construction can't change before the next beat-0 boundary - both
     * are exactly why resolving ahead of the real beat is safe. */
    private fun resolveBeatAudio(totalBeatsForBeat: Long, beatIndexForBeat: Int): ResolvedBeatAudio {
        val probability = effectiveMuteProbability(barsElapsedSincePlay)
        val muted = probability > 0f && random.nextFloat() < probability
        val sound = when {
            !_clickEnabled.value || muted -> null
            beatIndexForBeat == 0 -> ClickSound.BAR
            _timeSignature.value.isAccented(beatIndexForBeat) -> ClickSound.ACCENT
            else -> ClickSound.REGULAR
        }
        return ResolvedBeatAudio(totalBeatsForBeat, sound)
    }

    /** Fires (or schedules) [sound] for a beat whose real timestamp ([beatTimestampNanos]) has
     * already arrived - the reactive path used whenever the audio lookahead loop didn't already
     * fire this beat early, which covers zero/positive [audioOffsetMs] as well as any beat the
     * lookahead loop is disabled for (click off, non-negative offset, or following an external
     * MIDI clock - see [startAudioLookahead]). Schedules off the beat's own absolute timestamp
     * plus the offset - not a naive relative `delay(offsetMs)` - so whatever latency already
     * elapsed between the true beat and this call doesn't compound into extra lag. A negative
     * offset reaching here (MIDI-follow mode, where lookahead is intentionally skipped) simply
     * computes a target already in the past and fires immediately - documented no-op behavior,
     * not a silent pretense of leading a clock this engine doesn't control. */
    private fun scheduleReactiveAudio(sound: ClickSound, beatTimestampNanos: Long) {
        val fireAtNanos = beatTimestampNanos + (_audioOffsetMs.value * 1_000_000.0).toLong()
        val now = System.nanoTime()
        if (fireAtNanos <= now) {
            playClickSafely(sound)
        } else {
            scope.launch {
                delay((fireAtNanos - now) / 1_000_000)
                playClickSafely(sound)
            }
        }
    }

    /** The single point every click - lookahead or reactive - actually fires through. Re-checking
     * [_clickEnabled] and [BeatPhase.isPlaying] right here (not just at the moment the fire was
     * scheduled or resolved) is what stops a click from sounding after the user has already
     * pressed stop: [scheduleReactiveAudio]'s delayed coroutine isn't cancelled by [stop] the way
     * [renderJob]/[audioLookaheadJob] are, and even the lookahead loop's own iteration can be
     * mid-flight past its gating check at the exact moment [stop] runs. */
    private fun playClickSafely(sound: ClickSound) {
        if (!_clickEnabled.value || !_state.value.isPlaying) return
        clickListenerForTesting?.invoke(sound, System.nanoTime())
        try {
            clickPlayer.playClick(sound)
        } catch (e: Exception) {
            Log.e(TAG, "ClickPlayer failed; continuing without audio for this beat", e)
        }
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

    /**
     * Pre-triggers the audible click when [audioOffsetMs] is negative - the genuine lookahead a
     * discrete one-shot trigger needs to fire *before* the true beat, since (unlike
     * [visualOffsetMs]'s continuous phase-shift) there's no equivalent math trick for an event
     * that either has or hasn't happened yet. Only active while playing, [clickEnabled] is on, the
     * offset is negative, and timing isn't coming from an external MIDI clock - a followed clock
     * is already reactive with nothing of its own to predict, so lookahead is skipped there and
     * [scheduleReactiveAudio] fires immediately instead (documented no-op, not a silent pretense).
     *
     * Polls in slices bounded by [AUDIO_LOOKAHEAD_POLL_NANOS] (matching
     * `MidiClockSender.RESYNC_POLL_NANOS`'s precedent) rather than a single `delay()` up to the
     * target - re-checking regularly means a bpm change, an offset change, or a MIDI clock takeover
     * mid-wait all take effect within one slice instead of only being noticed a whole beat later.
     */
    private fun startAudioLookahead() {
        audioLookaheadJob?.cancel()
        audioLookaheadJob = scope.launch {
            while (isActive) {
                val offsetMs = _audioOffsetMs.value
                val gated = _state.value.isPlaying && _clickEnabled.value && offsetMs < 0f && !usingMidiClock
                if (!gated) {
                    delay(AUDIO_LOOKAHEAD_IDLE_POLL_MS)
                    continue
                }
                val remainingNanos = nanosUntilNextBeat(System.nanoTime(), _state.value.bpm) + offsetMs * 1_000_000.0
                if (remainingNanos <= 0.0) {
                    fireLookaheadAudioIfNeeded()
                } else {
                    delay((minOf(remainingNanos, AUDIO_LOOKAHEAD_POLL_NANOS.toDouble()) / 1_000_000.0).toLong().coerceAtLeast(1))
                }
            }
        }
    }

    /** The lookahead loop's own resolve-and-fire step, for the upcoming beat identified by the
     * *current* [totalBeats]/[beatIndex] fields (not yet incremented - see [onBeat]'s own end-of-
     * function increment). A no-op if this beat was already resolved (by an earlier poll of this
     * same loop - there is no other caller this early), which is what stops a beat from firing
     * more than once across repeated polls while its offset window is still open. */
    private fun fireLookaheadAudioIfNeeded() {
        val targetTotalBeats = totalBeats
        val targetBeatIndex = beatIndex
        val resolved = synchronized(audioResolutionLock) {
            val existing = resolvedBeatAudio
            if (existing != null && existing.totalBeats == targetTotalBeats) {
                null
            } else {
                resolveBeatAudio(targetTotalBeats, targetBeatIndex).also { resolvedBeatAudio = it }
            }
        }
        resolved?.sound?.let { playClickSafely(it) }
    }

    /** Nanoseconds remaining until the *next* beat is predicted to land, extrapolating from
     * [lastBeatNanos] at [bpm] - the one formula [renderFrame]'s phase calculation and the audio
     * lookahead loop ([startAudioLookahead]) both schedule from, instead of two independently-
     * maintained copies of `60_000_000_000.0 / bpm`. Negative once the predicted moment has
     * already passed. [bpm] is taken as a parameter (rather than read internally from [_state])
     * so a caller that already captured its own snapshot of bpm can't get a second, possibly
     * different, reading of it within the same calculation. */
    private fun nanosUntilNextBeat(nowNanos: Long, bpm: Float): Double {
        val intervalNanos = 60_000_000_000.0 / bpm
        return (lastBeatNanos + intervalNanos) - nowNanos
    }

    private fun renderFrame() {
        val current = _state.value
        if (!current.isPlaying) return
        val intervalNanos = 60_000_000_000.0 / current.bpm
        val rawElapsedNanos = intervalNanos - nanosUntilNextBeat(System.nanoTime(), current.bpm)
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
            // The defensive copy here (not in GlyphCanvas itself) is what keeps buffer reuse safe:
            // `base` may be a GlyphCanvas-pooled buffer that gets handed out again in a couple of
            // frames (see GlyphCanvas.BufferPool) - copying once, right before anything reaches
            // `_frame`'s StateFlow (and from there, the widget, MatrixPreview, and the closed-source
            // Glyph Matrix hardware SDK), guarantees every published frame is still a distinct,
            // never-reused-again array, exactly as before pooling existed.
            _frame.value = withQueueOverlay(base, phaseState.beatIndex, phaseState.phase).copyOf()
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

    /** Poll slice for [startAudioLookahead] while actively closing in on a target fire time -
     * matches `MidiClockSender.RESYNC_POLL_NANOS`'s precedent. */
    private const val AUDIO_LOOKAHEAD_POLL_NANOS = 3_000_000L

    /** Poll interval for [startAudioLookahead] while its gating conditions aren't met (click off,
     * non-negative offset, MIDI-follow, or not playing) - these rarely change faster than a frame,
     * so there's no need to re-check at [AUDIO_LOOKAHEAD_POLL_NANOS]'s much tighter cadence. */
    private const val AUDIO_LOOKAHEAD_IDLE_POLL_MS = 25L

    /** How many beat-intervals of silence from the MIDI clock before falling back to internal timing. */
    private const val MIDI_SILENCE_BEATS = 4

    /** Fraction of full brightness used for the idle glyph preview when the metronome is stopped. */
    private const val IDLE_GLYPH_SCALE = 0.06f

    /** Default/bounds for [progressiveMuteRampBars] - how many bars a progressive mute ramp takes
     * to reach its configured target probability. */
    const val DEFAULT_PROGRESSIVE_MUTE_RAMP_BARS = 8
    const val MIN_PROGRESSIVE_MUTE_RAMP_BARS = 1
    const val MAX_PROGRESSIVE_MUTE_RAMP_BARS = 32

    /** Upper bound for [setUnitNoteValue] - generous enough for any real time signature (32nd
     * notes) without pretending every integer in between is a musically meaningful denominator. */
    const val MAX_UNIT_NOTE_VALUE = 32
}
