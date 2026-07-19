package media.quaternion.qmetronome.engine

import android.content.Context
import android.util.Log
import media.quaternion.qmetronome.midi.MidiActionSender
import media.quaternion.qmetronome.midi.MidiClockSource
import media.quaternion.qmetronome.visualizers.GlyphVisualizer
import media.quaternion.qmetronome.visualizers.QueueOverlay
import media.quaternion.qmetronome.visualizers.VisualizerRegistry
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    /** The actual floor for the extended range, expressed in the unit its own display (BPH - see
     * `bpmDisplayValue`/`bpmDisplayUnit` in `MainScreen.kt`) uses - one beat per ten hours.
     * [EXTENDED_MIN_BPM] is derived from this, not the other way around, so the number that
     * matters ("how low can BPH go") is the explicit one, not an opaque BPM value someone has to
     * multiply by 60 to sanity-check. */
    const val MIN_BPH = 0.1f
    const val EXTENDED_MIN_BPM = MIN_BPH / 60f

    /** Upper bound for the extended range - 12000 BPM (200 BPS, a 5ms beat interval). This is an
     * estimate, not a measured device limit: `StreamingClickEngine`'s own sample-frame placement
     * has no hard ceiling (it mixes at an exact frame offset regardless of how close together
     * beats are), so the real constraint is [InternalClockSource]'s tick loop keeping up with its
     * own overhead (a `delay()` resume, a few state/map updates, audio resolution) rather than
     * falling behind and repeatedly hitting its stale-wait resync path. A 5ms floor is a
     * deliberately generous multiple of what a dedicated `THREAD_PRIORITY_URGENT_AUDIO` thread
     * (see `TimingDispatcher.kt`) should comfortably clear on modern hardware, chosen to push
     * meaningfully past the old 3000 BPM/50 BPS ceiling without leaning on unverified guesswork -
     * revise from real on-device profiling (does the clock loop's resync path fire constantly at
     * the top of the range?) if it turns out too high or too conservative for a given device. */
    const val EXTENDED_MAX_BPM = 12000f

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

    // One dedicated, elevated-priority scope per timing-critical role - see newTimingDispatcher's
    // kdoc for why a shared pool isn't good enough. The streaming writer specifically (audioWriterScope)
    // must never share a thread with anything else: its AudioTrack.write() calls block for real, and a
    // coroutine blocked on a Java call can't yield its thread back to a co-scheduled role.
    private val clockScope = CoroutineScope(SupervisorJob() + exceptionHandler + newTimingDispatcher("metronome-clock"))
    private val renderScope = CoroutineScope(SupervisorJob() + exceptionHandler + newTimingDispatcher("metronome-render"))
    private val audioScope = CoroutineScope(SupervisorJob() + exceptionHandler + newTimingDispatcher("metronome-audio-schedule"))
    private val audioWriterScope = CoroutineScope(SupervisorJob() + exceptionHandler + newTimingDispatcher("metronome-audio-writer"))

    // Not timing-critical - a debounced SharedPreferences write. Deliberately NOT one of the
    // elevated-priority scopes above: blocking disk I/O has no business running on a thread
    // reserved for real-time audio/visual work.
    private val persistScope = CoroutineScope(SupervisorJob() + exceptionHandler + Dispatchers.IO)

    @Volatile private var clock: ClockSource = InternalClockSource()
    private val clickPlayer = ClickPlayer()
    private val streamingClickEngine = StreamingClickEngine()

    /** Whether [streamingClickEngine] is the active audio-trigger path for the current session -
     * re-checked on every [start] call (cheap after the first successful one - see
     * [StreamingClickEngine.start]'s own kdoc for why repeat calls don't rebuild anything), and
     * flipped back to false mid-session if [StreamingClickEngine.hasFailedWarmup] ever reports
     * true (see [startAudioScheduling]). False means every click for this session falls back to
     * [clickPlayer]'s discrete-retrigger path - see [onBeat] and [fireLookaheadAudioIfNeeded]. */
    @Volatile private var usingStreamingClickEngine = false

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

    /** See [setAudioOffsetMs] for how this is actually scheduled - a non-positive value (negative
     * or exactly zero) gets genuine lookahead (see [startAudioScheduling]), unlike
     * [visualOffsetMs]'s continuous phase-shift. */
    private val _audioOffsetMs = MutableStateFlow(DEFAULT_AUDIO_OFFSET_MS)
    val audioOffsetMs: StateFlow<Float> = _audioOffsetMs.asStateFlow()

    /** See [beatZeroCountInNanos] for how this is actually used. */
    private val _firstBeatCountInCapMs = MutableStateFlow(DEFAULT_FIRST_BEAT_COUNT_IN_CAP_MS)
    val firstBeatCountInCapMs: StateFlow<Float> = _firstBeatCountInCapMs.asStateFlow()

    private val _compactLandscape = MutableStateFlow(false)
    val compactLandscape: StateFlow<Boolean> = _compactLandscape.asStateFlow()

    /** When on, the main screen's live tempo/transport cluster (`TempoTransportCluster` in
     * `MainScreen.kt`, also embedded in Settings' "Tempo & Bars" mirror) drops every text label
     * (unit text, "staged" text) in favor of icons/dots only - see the composables themselves for
     * what each one swaps to. Off by default, matching every other display toggle in this app. */
    private val _symbolicControlsEnabled = MutableStateFlow(false)
    val symbolicControlsEnabled: StateFlow<Boolean> = _symbolicControlsEnabled.asStateFlow()

    /** When on, a small secondary-colored unit-symbol mark (bpm/beat-type/bar/phrase - not
     * beats-per-bar, which dropped its own mark: three dots at this size read as a stray
     * dash/ellipsis rather than "three dots", the opposite of the intended "name it at a glance")
     * is shown next to each control's own value - purely a subtle label, not a second set of
     * controls. Separate from [symbolicControlsEnabled] (an unrelated toggle: text vs. icon-only
     * transport controls) - conflating the two would confuse both. On by default, unlike most
     * display toggles here, since the symbols are small/secondary enough not to need opt-in. */
    private val _unitSymbolsEnabled = MutableStateFlow(true)
    val unitSymbolsEnabled: StateFlow<Boolean> = _unitSymbolsEnabled.asStateFlow()

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

    /** The phrase queue - song-form sections, each carrying its own full bar queue (see [Phrase]).
     * A single entry (today's default) behaves exactly like there being no phrase concept at all:
     * [timeSignatureQueue]/[queueIndex]/[queueMode] always mirror `phrases[activePhraseIndex]` (kept in
     * sync by every function that touches either "side" - see `syncActivePhraseMemory`/[goToPhrase]),
     * so every existing bar-queue call site keeps working unmodified for that common case. */
    private val _phrases = MutableStateFlow(listOf(Phrase()))
    val phrases: StateFlow<List<Phrase>> = _phrases.asStateFlow()

    private val _activePhraseIndex = MutableStateFlow(0)
    val activePhraseIndex: StateFlow<Int> = _activePhraseIndex.asStateFlow()

    /** Governs how *phrases* advance into each other, the same [QueueMode] concept as [queueMode]
     * reused one level up - see `advanceQueueAtBarBoundary`'s phrase-boundary cascade. */
    private val _phraseQueueMode = MutableStateFlow(QueueMode.LOOP)
    val phraseQueueMode: StateFlow<QueueMode> = _phraseQueueMode.asStateFlow()

    /** Whether [QueueOverlay]'s ambient per-bar/per-beat background is drawn into the Glyph
     * frame at all - on by default, but purely cosmetic, so it's fully optional. */
    private val _queueOverlayEnabled = MutableStateFlow(true)
    val queueOverlayEnabled: StateFlow<Boolean> = _queueOverlayEnabled.asStateFlow()

    /** Whether [QueueOverlay]'s radial per-phrase dots are drawn - independent of
     * [queueOverlayEnabled] (which governs the per-bar rows), so a performer can run either, both,
     * or neither. On by default, matching [queueOverlayEnabled]'s own default; purely cosmetic. */
    private val _phraseIndicatorEnabled = MutableStateFlow(true)
    val phraseIndicatorEnabled: StateFlow<Boolean> = _phraseIndicatorEnabled.asStateFlow()

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
    private var audioSchedulingJob: Job? = null
    private var persistBpmJob: Job? = null

    /** The pending "wait out beat 0's count-in, then actually start ticking" coroutine - see
     * [start]/[beatZeroCountInNanos]. Null whenever no count-in is in flight (no delay configured,
     * or ticking has already begun). Tracked so a second [start] call (or [stop]) during the
     * window cancels it rather than leaving two overlapping starts racing. */
    private var countInJob: Job? = null
    private var lastTapNanos = 0L
    private val tapIntervalsMs = mutableListOf<Long>()

    /** One-shot mute/sound decisions, keyed by [ResolvedBeatAudio.totalBeats], resolved either
     * early by [startAudioScheduling] or on-time by [onBeat] itself - whichever gets there first.
     * A *map*, not a single nullable slot, deliberately: [onBeat] runs on [clockScope]'s dedicated
     * thread while the scheduling loop runs on [audioScope]'s - genuinely different threads, not
     * just different coroutines - so the scheduling loop can legitimately resolve-and-cache beat
     * N+1 microseconds before [onBeat] gets around to consuming beat N's own entry; a single
     * overwritable slot can't hold both at once, and whichever call loses that race would silently
     * clobber the other's entry, forcing a second, duplicate resolution (and duplicate
     * mute-probability roll) later. Guarded by [audioResolutionLock] rather than relying on
     * `@Volatile` alone, since resolving involves a non-atomic check-then-set. In practice this
     * never holds more than 1-2 entries - [onBeat] removes its own beat's entry the instant it
     * consumes it. */
    private val resolvedBeatAudioCache = mutableMapOf<Long, ResolvedBeatAudio>()
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
        streamingClickEngine.configureFromDevice(context.applicationContext)
        _visualizer.value = VisualizerRegistry.byId(store.visualizerId)
        val restoredPhrases = store.phrases
        val restoredPhraseIndex = store.activePhraseIndex.coerceIn(0, restoredPhrases.size - 1)
        val activePhrase = restoredPhrases[restoredPhraseIndex]
        val restoredIndex = store.queueIndex.coerceIn(0, activePhrase.bars.size - 1)
        val restoredSpec = activePhrase.bars[restoredIndex]
        _phrases.value = restoredPhrases
        _activePhraseIndex.value = restoredPhraseIndex
        _phraseQueueMode.value = store.phraseQueueMode
        _timeSignatureQueue.value = activePhrase.bars
        _queueIndex.value = restoredIndex
        _queueMode.value = activePhrase.barQueueMode
        _timeSignature.value = restoredSpec
        _state.value = BeatPhase.IDLE.copy(bpm = restoredSpec.bpm, beatsPerBar = restoredSpec.beatCount)
        // The active bar's own visualizer choice (if it's ever pinned one) wins over the plain
        // last-used global visualizer restored above.
        restoredSpec.visualizerId?.let { _visualizer.value = VisualizerRegistry.byId(it) }
        // Locks in a legacy-queue migration (see MetronomeSettings.phrases' kdoc) so future opens
        // read the canonical phrases format directly rather than re-deriving it every time.
        store.phrases = restoredPhrases
        store.activePhraseIndex = restoredPhraseIndex
        _clickEnabled.value = store.clickEnabled
        val restoredSpecs = ClickSound.entries.associateWith { store.clickSpec(it) }
        restoredSpecs.forEach { (sound, spec) ->
            clickPlayer.setSpec(sound, spec)
            streamingClickEngine.setSpec(sound, spec)
        }
        _clickSpecs.value = restoredSpecs
        _visualOffsetMs.value = store.visualOffsetMs
        _audioOffsetMs.value = store.audioOffsetMs
        _firstBeatCountInCapMs.value = store.firstBeatCountInCapMs
        _compactLandscape.value = store.compactLandscape
        _symbolicControlsEnabled.value = store.symbolicControlsEnabled
        _unitSymbolsEnabled.value = store.unitSymbolsEnabled
        _persistentModeEnabled.value = store.persistentModeEnabled
        _hasShownBpmHint.value = store.hasShownBpmHint
        _muteProbability.value = store.muteProbability
        _progressiveMuteEnabled.value = store.progressiveMuteEnabled
        _progressiveMuteRampBars.value = store.progressiveMuteRampBars
        _extendedBpmRangeEnabled.value = store.extendedBpmRangeEnabled
        _queueOverlayEnabled.value = store.queueOverlayEnabled
        _phraseIndicatorEnabled.value = store.phraseIndicatorEnabled
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
        if (wasPlaying) clock.start(clockScope, _state.value.bpm, ::onBeat)
    }

    fun setMatrixSize(size: Int) {
        if (size > 0) {
            matrixSize = size
            emitIdleFrame()
        }
    }

    /**
     * Writes whatever [timeSignatureQueue]/[queueMode] currently hold - the active phrase's own
     * "scratch" bar-queue state, mutated in place by every existing bar-level function exactly as
     * it always was - back into [_phrases] at [_activePhraseIndex]. Called at the end of every function
     * that mutates the active phrase's own bars/mode, so [phrases] (the source of truth for anything
     * about a phrase *other* than the currently-active one, and what's actually persisted) never
     * drifts out of sync with the scratch state. See [goToPhrase] for the reverse direction: loading
     * a *different* phrase's own bars/mode into that same scratch state. Kept separate from
     * [persistPhrases] so [setBpmImmediate]'s debounced disk write can update this in-memory state
     * immediately while still batching the disk write itself.
     */
    private fun syncActivePhraseMemory() {
        _phrases.update { phrases ->
            val index = _activePhraseIndex.value.coerceIn(0, phrases.lastIndex)
            phrases.toMutableList().apply {
                this[index] = this[index].copy(bars = _timeSignatureQueue.value, barQueueMode = _queueMode.value)
            }
        }
    }

    private fun persistPhrases() {
        settings?.phrases = _phrases.value
        settings?.activePhraseIndex = _activePhraseIndex.value
    }

    /** [syncActivePhraseMemory] plus an immediate [persistPhrases] - the common case for every bar-level
     * mutator except [setBpmImmediate], which debounces the disk write instead. */
    private fun syncActivePhraseMemoryAndPersist() {
        syncActivePhraseMemory()
        persistPhrases()
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
        syncActivePhraseMemory()
        persistBpmJob?.cancel()
        persistBpmJob = persistScope.launch {
            delay(BPM_PERSIST_DEBOUNCE_MS)
            settings?.bpm = clamped
            persistPhrases()
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
        syncActivePhraseMemoryAndPersist()
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
        syncActivePhraseMemoryAndPersist()
    }

    /** The accent tier authored for each non-downbeat position in the active bar - see
     * [TimeSignature.accentPattern]/[BeatAccent]. Always writes into whichever bar is currently
     * active in the queue, the same "always edits the active bar" pattern [setBeatsPerBar]/
     * [setUnitNoteValue] already follow. */
    fun setAccentPattern(pattern: List<BeatAccent>) {
        _timeSignature.value = _timeSignature.value.copy(accentPattern = pattern)
        _timeSignatureQueue.update { queue ->
            val index = _queueIndex.value
            if (index !in queue.indices) return@update queue
            queue.toMutableList().apply { this[index] = this[index].copy(accentPattern = pattern) }
        }
        syncActivePhraseMemoryAndPersist()
    }

    /**
     * Sets or clears (via `action = null`) a single beat's own MIDI override at an *explicit*
     * (phrase, bar, beat) location - see [TimeSignature.midiOverrides]/[resolveMidiActionForBeat].
     * Unlike [setAccentPattern] (which always edits whichever bar the engine currently has active),
     * this targets exactly the triple passed in, so an edit always lands on precisely the beat
     * selected in the UI - never silently "whichever bar happens to be active" - the same explicit-
     * index shape [setPhraseAction] already uses one level up. A no-op if either index is out of
     * range. Mutates [_phrases] directly (this is authoring a specific bar's own data, not the
     * "live active bar" scratch surface [setAccentPattern]/[setBpmImmediate] maintain), then also
     * refreshes [_timeSignature]/[_timeSignatureQueue] when [phraseIndex]/[barIndex] happen to
     * *be* the currently active phrase/bar, so anything reading those flows (the main screen, the
     * engine's own beat-resolution path) sees the change immediately rather than only after the
     * next navigation event reloads them from [_phrases].
     */
    fun setMidiOverride(phraseIndex: Int, barIndex: Int, beatIndex: Int, action: MidiBeatAction?) {
        val phrases = _phrases.value
        if (phraseIndex !in phrases.indices) return
        val phrase = phrases[phraseIndex]
        if (barIndex !in phrase.bars.indices) return
        val bar = phrase.bars[barIndex]
        val updatedOverrides = (bar.midiOverrides ?: emptyMap()).toMutableMap()
        if (action == null) updatedOverrides.remove(beatIndex) else updatedOverrides[beatIndex] = action
        val updatedBar = bar.copy(midiOverrides = updatedOverrides.ifEmpty { null })
        val updatedBars = phrase.bars.toMutableList().apply { this[barIndex] = updatedBar }
        val updatedPhrases = phrases.toMutableList().apply { this[phraseIndex] = phrase.copy(bars = updatedBars) }
        _phrases.value = updatedPhrases
        settings?.phrases = updatedPhrases
        if (phraseIndex == _activePhraseIndex.value) {
            _timeSignatureQueue.value = updatedBars
            if (barIndex == _queueIndex.value) {
                _timeSignature.value = updatedBar
            }
        }
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
        syncActivePhraseMemoryAndPersist()
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
        syncActivePhraseMemoryAndPersist()
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
        _queueMode.value = QueueMode.LOOP
        goToQueueBar(0)
        syncActivePhraseMemoryAndPersist()
    }

    fun setQueueMode(mode: QueueMode) {
        _queueMode.value = mode
        syncActivePhraseMemoryAndPersist()
    }

    fun setQueueOverlayEnabled(enabled: Boolean) {
        _queueOverlayEnabled.value = enabled
        settings?.queueOverlayEnabled = enabled
        emitIdleFrame()
    }

    fun setPhraseIndicatorEnabled(enabled: Boolean) {
        _phraseIndicatorEnabled.value = enabled
        settings?.phraseIndicatorEnabled = enabled
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
     * outright, since the performer decides when to actually stop). Applied twice by
     * [advanceQueueAtBarBoundary]: once for bars within the active phrase, once - identically - for
     * phrases within [phrases], since "advance to the next slot, wrap/stop/hold depending on mode" is
     * the same decision at both levels. Exposed (not private) so the decision is directly
     * unit-testable without driving a real beat loop.
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

    /**
     * Called at each bar boundary (see [onBeat]) once the *previous* bar has fully played out.
     * First tries to advance within the active phrase's own bars, exactly as before phrases existed. If
     * there's nothing further to advance to there *and* the phrase's own mode is [QueueMode.ONCE]
     * (i.e. this phrase just legitimately finished - not "MANUAL never advances" and not "a single
     * bar is inert either way", both of which also return null from [nextQueueIndexAfterBar] but
     * shouldn't hand off to another phrase), falls through to a phrase-level transition via the
     * identical [nextQueueIndexAfterBar] applied one level up. Deliberately no extra "more than
     * one bar" guard on that fall-through: a one-bar ONCE-mode phrase is the simplest, most natural
     * phrase shape (a whole section that's just one bar), and it must cascade on every single bar,
     * not be silently inert the way a bar-less guard would make it.
     */
    private fun advanceQueueAtBarBoundary() {
        val queue = _timeSignatureQueue.value
        val barNext = nextQueueIndexAfterBar(_queueIndex.value, queue.size, _queueMode.value)
        if (barNext != null) {
            goToQueueBar(barNext)
            return
        }
        if (_queueMode.value != QueueMode.ONCE) return
        val phrases = _phrases.value
        val phraseNext = nextQueueIndexAfterBar(_activePhraseIndex.value, phrases.size, _phraseQueueMode.value) ?: return
        goToPhrase(phraseNext)
    }

    /** Jumps directly to a phrase (clamped to a valid index), loading its own bars/mode into the
     * active bar-queue "scratch" state ([timeSignatureQueue]/[queueMode] - see
     * `syncActivePhraseMemory`'s kdoc for why that state, not [phrases] directly, is what every existing
     * bar-level function actually reads/writes) and landing on that phrase's first bar. Always resets
     * to bar 0 of the target phrase rather than remembering where a previous visit left off -
     * simplest and least surprising for song-form playback, where entering a section should start
     * at its own beat 1, whether you got there manually or via [advanceQueueAtBarBoundary]'s
     * automatic cascade. Fires the target [Phrase.action] every time this resolves to a phrase,
     * including a direct tap re-entering the already-active one - "fires whenever a phrase is
     * confirmed/entered" is the simpler mental model over "only on a genuine transition", and
     * matches [MidiBeatAction]'s own NONE-by-default silence for anyone who hasn't configured one.
     */
    fun goToPhrase(index: Int) {
        val phrases = _phrases.value
        if (phrases.isEmpty()) return
        val clampedIndex = index.coerceIn(0, phrases.size - 1)
        _activePhraseIndex.value = clampedIndex
        settings?.activePhraseIndex = clampedIndex
        val phrase = phrases[clampedIndex]
        _timeSignatureQueue.value = phrase.bars
        _queueMode.value = phrase.barQueueMode
        MidiActionSender.fire(phrase.action, System.nanoTime())
        goToQueueBar(0)
    }

    /** Manual navigation - one phrase at a time, clamped rather than wrapping (wrapping is
     * [phraseQueueMode]'s [QueueMode.LOOP] job at a phrase boundary) - the phrase-level counterpart to
     * [nextQueueBar]/[previousQueueBar]. */
    fun nextPhrase() = goToPhrase(_activePhraseIndex.value + 1)
    fun previousPhrase() = goToPhrase(_activePhraseIndex.value - 1)

    /** Appends a new phrase - a single default bar, [QueueMode.LOOP] - after the current last phrase and
     * jumps to it. Unlike [addBarToQueue] (which copies the active bar, since neighboring bars
     * within a phrase are often variations of each other), a new phrase deliberately starts fresh rather
     * than copying the active phrase's own bars - a new song-form section is a different thing, not a
     * variation of the one you were just on. This is also the single always-visible entry point
     * that makes every other phrase-management control appear in the first place (see
     * `BeatsPerBarControls`'s "+phrase" affordance) - before this is ever called, [phrases] holds exactly
     * one entry and nothing about phrases is visible on screen.
     */
    fun addPhrase() {
        val phrases = _phrases.value
        _phrases.value = phrases + Phrase()
        settings?.phrases = _phrases.value
        goToPhrase(phrases.size)
    }

    /**
     * Removes a specific phrase - a no-op if it's the only one left (phrases, like bars, can never be
     * empty) or [index] is out of range. Mirrors [removeBarFromQueue]'s behavior one level up:
     * removing a phrase *other* than the active one keeps the same one active (adjusting for the
     * shift); removing the active phrase itself lands on whichever phrase now occupies its old slot,
     * clamped to the new last phrase if it was the end.
     */
    fun removePhrase(index: Int) {
        val phrases = _phrases.value
        if (phrases.size <= 1 || index !in phrases.indices) return
        val updated = phrases.toMutableList().apply { removeAt(index) }
        _phrases.value = updated
        settings?.phrases = updated
        val activeIndex = _activePhraseIndex.value
        val newActiveIndex = if (index < activeIndex) activeIndex - 1 else activeIndex
        goToPhrase(newActiveIndex.coerceAtMost(updated.size - 1))
    }

    /** Removes the currently-active phrase - see [removePhrase]. */
    fun removeCurrentPhrase() = removePhrase(_activePhraseIndex.value)

    /** Collapses back to a single default phrase and [QueueMode.LOOP] phrase mode - the phrase-level
     * counterpart to [resetQueueToDefault], for starting a multi-phrase set over from scratch. Once
     * this runs, [phrases] is back to a single entry and the phrase-management strip disappears again -
     * symmetric with how [addPhrase] is what first made it appear. */
    fun resetPhrasesToDefault() {
        _phrases.value = listOf(Phrase())
        settings?.phrases = _phrases.value
        _phraseQueueMode.value = QueueMode.LOOP
        settings?.phraseQueueMode = QueueMode.LOOP
        goToPhrase(0)
    }

    fun setPhraseQueueMode(mode: QueueMode) {
        _phraseQueueMode.value = mode
        settings?.phraseQueueMode = mode
    }

    /** Sets a specific phrase's own [Phrase.action] - fired once via [MidiActionSender.fire]
     * every time [goToPhrase] resolves to that phrase (see its own kdoc). Unlike [setMidiOverride]/
     * [setAccentPattern], this doesn't need to sync into the "scratch" bar-queue state
     * ([timeSignatureQueue]/[queueMode]) - [Phrase.action] is only ever read directly off
     * [phrases] itself, at the moment [goToPhrase] navigates to it, not part of the live
     * single-bar editing surface those other setters maintain. */
    fun setPhraseAction(index: Int, action: MidiBeatAction) {
        val phrases = _phrases.value
        if (index !in phrases.indices) return
        val updated = phrases.toMutableList().apply { this[index] = this[index].copy(action = action) }
        _phrases.value = updated
        settings?.phrases = updated
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
        streamingClickEngine.setSpec(sound, spec)
        _clickSpecs.update { it + (sound to spec) }
        settings?.setClickSpec(sound, spec)
    }

    fun setVisualOffsetMs(ms: Float) {
        val clamped = ms.coerceIn(-500f, 500f)
        _visualOffsetMs.value = clamped
        settings?.visualOffsetMs = clamped
    }

    /** A non-positive value (negative, or exactly zero) leads the click via genuine lookahead
     * scheduling (see [startAudioScheduling]); a positive value delays it, scheduled off the
     * beat's own real timestamp (see [scheduleReactiveAudio]) rather than a naive relative
     * `delay()` that could drift under scheduling pressure. */
    fun setAudioOffsetMs(ms: Float) {
        val clamped = ms.coerceIn(-500f, 500f)
        _audioOffsetMs.value = clamped
        settings?.audioOffsetMs = clamped
    }

    /** See [beatZeroCountInNanos] for how this cap is actually used - 0 opts back out to today's
     * instant-but-unled first beat. */
    fun setFirstBeatCountInCapMs(ms: Float) {
        val clamped = ms.coerceIn(0f, 500f)
        _firstBeatCountInCapMs.value = clamped
        settings?.firstBeatCountInCapMs = clamped
    }

    fun setCompactLandscape(enabled: Boolean) {
        _compactLandscape.value = enabled
        settings?.compactLandscape = enabled
    }

    fun setSymbolicControlsEnabled(enabled: Boolean) {
        _symbolicControlsEnabled.value = enabled
        settings?.symbolicControlsEnabled = enabled
    }

    fun setUnitSymbolsEnabled(enabled: Boolean) {
        _unitSymbolsEnabled.value = enabled
        settings?.unitSymbolsEnabled = enabled
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

    /** For on-device benchmarking only - thin pass-through to
     * [StreamingClickEngine.setMixListenerForTesting], since [streamingClickEngine] itself isn't
     * visible outside this class. */
    fun setStreamingMixListenerForTesting(listener: ((totalBeats: Long, targetNanos: Long, actualNanos: Long) -> Unit)?) {
        streamingClickEngine.setMixListenerForTesting(listener)
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
        syncActivePhraseMemoryAndPersist()
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
     *
     * Actually beginning to tick ([beginTicking]) is itself deferred by [beatZeroCountInNanos] when
     * that's non-zero - see that function's kdoc for why beat 0's *audio* specifically needs a
     * real, if brief, head start to land as precisely as every later beat, and
     * [firstBeatCountInCapMs] for the user-facing dial on how much of a pause that's worth.
     * [_state]'s `isPlaying` flips true immediately either way (so the transport button responds
     * the instant it's pressed), independent of whether the flash/first tick themselves wait.
     *
     * That deferred window feeds beat 0 through the *exact same* predictive-scheduling loop
     * ([startAudioScheduling]/[refreshPredictedSchedule]) every later beat already uses, via a
     * synthetic [lastBeatNanos] that makes "beat 0 is due" land precisely [beatZeroCountInNanos]
     * nanoseconds from now - not a bespoke one-shot [StreamingClickEngine.scheduleBeat] call. An
     * earlier version of this fix used a one-shot call and measurably under-performed this: on
     * real hardware, steady-state beats (already going through the polling loop) carry their own
     * consistent baseline placement error - real, device-specific buffer/HAL behavior this
     * benchmark doesn't otherwise correct for - and a hand-rolled parallel computation reproduced
     * that same baseline *plus* its own extra error on top, rather than matching it. Reusing the
     * identical mechanism (same arithmetic, same repeated refinement right up to the deadline)
     * means beat 0 inherits the same baseline as every other beat instead of a second, larger one -
     * see `docs/timing-accuracy-benchmark.md`'s results table for the measured before/after.
     */
    fun start(primeVisualFlash: Boolean = true) {
        if (_state.value.isPlaying && renderJob?.isActive == true) return
        beatIndex = 0
        totalBeats = 0
        barsElapsedSincePlay = 0
        synchronized(audioResolutionLock) { resolvedBeatAudioCache.clear() }
        goToQueueBar(0)
        usingStreamingClickEngine = streamingClickEngine.start(audioWriterScope)
        // A no-op after the first successful session (see StreamingClickEngine.start's own
        // kdoc) - stop() below no longer tears the engine down, so this just re-confirms it's
        // still alive rather than paying warm-up again on every play press.
        streamingClickEngine.resetSchedule()

        countInJob?.cancel()
        val countInNanos = beatZeroCountInNanos(primeVisualFlash)
        if (countInNanos <= 0L) {
            beginTicking(primeVisualFlash)
            return
        }
        _state.update { it.copy(isPlaying = true) }
        val intervalNanos = (60_000_000_000.0 / _state.value.bpm).toLong()
        lastBeatNanos = System.nanoTime() + countInNanos - intervalNanos
        startAudioScheduling()
        countInJob = clockScope.launch {
            delay(countInNanos / 1_000_000)
            beginTicking(primeVisualFlash = true)
        }
    }

    /** The actual "make it tick" half of [start] - [primeVisualFlash]'s flash/[lastBeatNanos]
     * reset, then handing off to [clock]/[startRenderLoop]/[startAudioScheduling]. Split out so
     * [start] can defer calling this by [beatZeroCountInNanos] without duplicating any of it. */
    private fun beginTicking(primeVisualFlash: Boolean) {
        countInJob = null
        if (primeVisualFlash) {
            lastBeatNanos = System.nanoTime()
            _state.update { it.copy(isPlaying = true, beatIndex = 0, phase = 0f, isAccent = true) }
        } else {
            _state.update { it.copy(isPlaying = true) }
        }
        clock.start(clockScope, _state.value.bpm, ::onBeat)
        startRenderLoop()
        startAudioScheduling()
    }

    /**
     * How long [start] should hold beat 0 back, in nanoseconds - 0 for "don't, fire instantly"
     * (today's behavior: the flash and the real first [onBeat] land within microseconds of each
     * other, per [InternalClockSource]'s documented instant-first-tick behavior, but beat 0's
     * *audio* has no equivalent lead: [onBeat] resolves and schedules it reactively, with none of
     * the advance notice [startAudioScheduling]'s predictive loop gives beat 1 onward - a
     * structural gap, not a bug in any one function, confirmed by tracing the frame math in
     * [StreamingClickEngine.mixPendingBeatIfDue]: an already-past target there clamps to "the
     * earliest available frame," which is *not* "now," it's already
     * [StreamingClickEngine.leadMarginNanos] in the future, because the writer keeps its buffer
     * that far ahead of real playback).
     *
     * The only way to give beat 0 genuine lead is to defer its own nominal instant - this
     * function's return value - long enough that [start] can feed it through the *same*
     * [startAudioScheduling]/[refreshPredictedSchedule] polling loop every later beat already
     * uses (via a synthetic [lastBeatNanos] - see [start]'s own kdoc for why that, rather than a
     * one-shot [StreamingClickEngine.scheduleBeat] push, is what actually closes the gap on real
     * hardware). Bounded by three things, the smallest wins: the actual lead+offset this session needs
     * ([StreamingClickEngine.calibratedLeadMarginNanos] plus the configured [audioOffsetMs]'s own
     * magnitude - the *calibrated* margin, not the raw buffer estimate, since research and this
     * project's own measurement agree the raw estimate alone under-reports real hardware lead - see
     * `docs/timing-accuracy-benchmark.md`),
     * [firstBeatCountInCapMs] (the user's own tolerance for a pause before playback visibly
     * starts), and [MAX_STREAMING_LEAD_MARGIN_BEAT_FRACTION] of the current beat interval (so the
     * pause never reads as disproportionate to the tempo itself, the same guard
     * [startAudioScheduling] already applies to its own margin). Zero whenever there's nothing to
     * lead in the first place: no local "pressed play" instant to count in from
     * ([primeVisualFlash] false - a MIDI-driven start), the streaming engine isn't the active audio
     * path this session, the click is off, the configured offset is a deliberate *lag* (`> 0`,
     * strictly - a zero offset still wants the buffer's own lead margin, see
     * [startAudioScheduling]'s own kdoc for why zero moved to this side of the boundary as of this
     * round), or the user's own cap is 0.
     *
     * Exposed (not private) so this is directly unit-testable without driving a real beat loop -
     * the same "pure decision logic, testable in isolation" precedent
     * [rescaledBpmForUnitNoteValueChange]/[nextQueueIndexAfterBar] already establish.
     */
    fun beatZeroCountInNanos(primeVisualFlash: Boolean): Long {
        if (!primeVisualFlash || !usingStreamingClickEngine || !_clickEnabled.value || usingMidiClock) return 0L
        val offsetMs = _audioOffsetMs.value
        if (offsetMs > 0f) return 0L
        val capNanos = _firstBeatCountInCapMs.value * 1_000_000.0
        if (capNanos <= 0.0) return 0L
        val idealNanos = streamingClickEngine.calibratedLeadMarginNanos() + (-offsetMs * 1_000_000.0).toLong()
        val intervalNanos = 60_000_000_000.0 / _state.value.bpm
        return minOf(
            idealNanos.toDouble(),
            capNanos,
            intervalNanos * MAX_STREAMING_LEAD_MARGIN_BEAT_FRACTION,
        ).toLong().coerceAtLeast(0L)
    }

    /**
     * Stops ticking. Also force-clears any hold/latch in progress, flushing staged BPM and
     * committing any beats-per-bar change waiting on a bar boundary that will now never arrive -
     * staging only makes sense during a live, attended session, so a "stuck red" latch surviving
     * past the end of playback would be wrong, not just stale.
     */
    fun stop() {
        clock.stop()
        // Cancels a pending beat-0 count-in (see start()/beatZeroCountInNanos) if stop() lands
        // during that window - otherwise beginTicking() would still fire later and incorrectly
        // resume playback after the user already pressed stop.
        countInJob?.cancel()
        countInJob = null
        // Flipped first, before cancelling anything - playClickSafely's own isPlaying check is
        // what actually stops an in-flight reactive delay (scheduleReactiveAudio's one-off
        // coroutine isn't tracked/cancelled the way renderJob/audioSchedulingJob are) or a
        // scheduling-loop iteration that was already past its gating check from firing audio after
        // stop was pressed - flipping this as early as possible narrows that race to a minimum.
        _state.update { it.copy(isPlaying = false, phase = 0f) }
        renderJob?.cancel()
        renderJob = null
        audioSchedulingJob?.cancel()
        audioSchedulingJob = null
        // Not a real streamingClickEngine.stop() - the AudioTrack/writer stay warm (mixing
        // silence) across sessions now, so the *next* start() doesn't have to re-pay
        // AudioTrack.getTimestamp()'s warm-up wait. This just cancels anything scheduled-but-
        // not-yet-mixed and resets the consumed/pending high-water marks so a future session's
        // beat 0 isn't born already "consumed" by this session's totalBeats count - see
        // StreamingClickEngine.resetSchedule's own kdoc. A genuine teardown only happens in
        // release() or if the writer itself fails (see startAudioScheduling's hasFailedWarmup
        // check).
        streamingClickEngine.resetSchedule()
        synchronized(audioResolutionLock) { resolvedBeatAudioCache.clear() }
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

    /** Full teardown, including the streaming engine's `AudioTrack`/writer itself - [stop] alone
     * deliberately leaves that warm (see its own kdoc), so this is the one place that actually
     * releases it. Not called anywhere in production today (there's no process-lifecycle hook
     * that would call it), but should stay correct regardless. */
    fun release() {
        stop()
        streamingClickEngine.stop()
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
        streamingClickEngine.stop()
        usingStreamingClickEngine = false
        settings = null
        _visualizer.value = VisualizerRegistry.default
        _state.value = BeatPhase.IDLE
        _clockStatus.value = ClockStatus.Internal
        _clickEnabled.value = false
        ClickSound.entries.forEach {
            clickPlayer.setSpec(it, ClickSpec.defaultFor(it))
            streamingClickEngine.setSpec(it, ClickSpec.defaultFor(it))
        }
        _clickSpecs.value = ClickSound.entries.associateWith(ClickSpec::defaultFor)
        _visualOffsetMs.value = DEFAULT_VISUAL_OFFSET_MS
        _audioOffsetMs.value = DEFAULT_AUDIO_OFFSET_MS
        _firstBeatCountInCapMs.value = DEFAULT_FIRST_BEAT_COUNT_IN_CAP_MS
        synchronized(audioResolutionLock) { resolvedBeatAudioCache.clear() }
        clickListenerForTesting = null
        _compactLandscape.value = false
        _symbolicControlsEnabled.value = false
        _unitSymbolsEnabled.value = true
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
        _phrases.value = listOf(Phrase())
        _activePhraseIndex.value = 0
        _phraseQueueMode.value = QueueMode.LOOP
        _queueOverlayEnabled.value = true
        _phraseIndicatorEnabled.value = true
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
        MidiActionSender.resetForTesting()
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
        val index = beatIndex
        val count = totalBeats
        val beatType = beatTypeFor(index)
        _state.update {
            it.copy(
                bpm = measuredBpm?.coerceIn(effectiveMinBpm(), effectiveMaxBpm()) ?: it.bpm,
                beatIndex = index,
                totalBeats = count,
                isAccent = beatType != ClickSound.REGULAR,
                phase = 0f,
            )
        }
        if (usingMidiClock) {
            _clockStatus.value = ClockStatus.Midi(measuredBpm, MidiClockSource.activeSource?.name)
        }
        // Independent of clickEnabled/mute-probability (see beatTypeFor's own kdoc) and of the
        // audio-resolution branching below - MidiActionSender does its own enabled/action-type
        // gating internally, and onBeat fires exactly once per real beat, so there's no cache or
        // double-fire concern here the way resolveBeatAudio's caching exists to prevent. Resolves
        // through resolveMidiActionForBeat (not the thinner fireForBeat(beatType, ...)) so a
        // per-beat override (see TimeSignature.midiOverrides) wins over beatType's own type-level
        // default, the same single resolution path the manual Trigger button also goes through.
        MidiActionSender.fire(resolveMidiActionForBeat(index, knownSound = beatType), timestampNanos)
        // Advance the beat counters *before* resolving/dispatching this beat's audio, not after -
        // so a concurrent reader (the audio scheduling loop, running on its own dedicated thread -
        // see [startAudioScheduling]) never observes [totalBeats] still reading `count` while this
        // beat's audio is already mid-resolution below, which would make it redundantly resolve
        // (and, worse, re-cache) the very beat this call is already handling instead of the real
        // next one. [index]/[count] were already captured above for advanceQueueAtBarBoundary's/
        // this beat's own use, so the fields are free to move on.
        if (index == 0) barsElapsedSincePlay++
        totalBeats = count + 1
        beatIndex = (index + 1) % _state.value.beatsPerBar.coerceAtLeast(1)
        // Reuse the scheduling loop's resolution if it already got there first - otherwise resolve
        // fresh, right now. Either way this must resolve exactly once per beat - see
        // [ResolvedBeatAudio]. Only clear the cache slot when it actually matches `count`
        // (never unconditionally) - the scheduling loop may already have resolved *and cached* the
        // next beat by the time this runs (its target just advanced above), and blindly nulling the
        // slot would throw that valid resolution away, forcing a second, duplicate resolution (and
        // duplicate mute-probability roll) later. What happens next depends on which audio path is
        // active this session (see [usingStreamingClickEngine]):
        //  - streaming: always (re-)register this beat with the exact, authoritative timestamp -
        //    idempotent, and strictly more accurate than whatever a predictive poll may have
        //    already registered (see [refreshPredictedSchedule]), since this is the real beat, not
        //    a prediction of it.
        //  - fallback (ClickPlayer): unchanged from before this round - only fire reactively if the
        //    lookahead loop didn't already fire this beat early.
        val (resolved, alreadyResolved) = synchronized(audioResolutionLock) {
            val cached = resolvedBeatAudioCache.remove(count)
            if (cached != null) cached to true else resolveBeatAudio(count, index) to false
        }
        if (usingStreamingClickEngine) {
            if (!alreadyResolved) {
                resolved.sound?.let { clickListenerForTesting?.invoke(it, System.nanoTime()) }
            }
            if (_state.value.isPlaying) {
                val fireAtNanos = timestampNanos + (_audioOffsetMs.value * 1_000_000.0).toLong()
                streamingClickEngine.scheduleBeat(count, resolved.sound, fireAtNanos)
            }
        } else if (!alreadyResolved) {
            resolved.sound?.let { scheduleReactiveAudio(it, timestampNanos) }
        }
    }

    /** Resolves whether beat [totalBeatsForBeat] (at bar position [beatIndexForBeat]) should play
     * a click, and if so which [ClickSound] - the one place [effectiveMuteProbability]'s roll and
     * the beat-vs-accent-vs-regular sound choice happen, called by whichever of [onBeat] or the
     * audio scheduling loop ([startAudioScheduling]) gets there first for a given beat. Beat 0 is
     * unconditionally [ClickSound.BAR] regardless of a bar-queue advance that may not have
     * happened yet when this runs ahead of time; a non-zero beat's accent comes from the *current*
     * time signature, which by construction can't change before the next beat-0 boundary - both
     * are exactly why resolving ahead of the real beat is safe. */
    private fun resolveBeatAudio(totalBeatsForBeat: Long, beatIndexForBeat: Int): ResolvedBeatAudio {
        val probability = effectiveMuteProbability(barsElapsedSincePlay)
        val muted = probability > 0f && random.nextFloat() < probability
        val sound = if (!_clickEnabled.value || muted) null else beatTypeFor(beatIndexForBeat)
        return ResolvedBeatAudio(totalBeatsForBeat, sound)
    }

    /** The [ClickSound] beat type at [beatIndexForBeat] in the *active* bar - see
     * [TimeSignature.clickSoundAt], which this just delegates to for [_timeSignature]. Pure and
     * independent of [_clickEnabled]/mute-probability - [resolveBeatAudio] layers that
     * audio-specific gating on top for the audible click, while [onBeat] feeds this same, ungated
     * value straight to [media.quaternion.qmetronome.midi.MidiActionSender] - MIDI beat-actions
     * deliberately fire regardless of whether the audible click is muted/disabled, the same way the
     * visual flash already does (see [effectiveMuteProbability]'s own kdoc: muting is scoped to
     * "the audible click only"). Exposed (not private) so it's directly unit-testable without
     * driving a real beat loop, the same precedent [rescaledBpmForUnitNoteValueChange]/
     * [nextQueueIndexAfterBar] already establish. */
    fun beatTypeFor(beatIndexForBeat: Int): ClickSound = _timeSignature.value.clickSoundAt(beatIndexForBeat)

    /** The actually-configured [MidiBeatAction] for [beatIndexForBeat]: that beat's own override
     * (see [TimeSignature.midiOverrideAt]) if one has been authored, else [beatTypeFor]'s resolved
     * [ClickSound]'s own type-level default (see
     * [media.quaternion.qmetronome.midi.MidiActionSender.actions]), else [MidiBeatAction]'s own
     * NONE default if neither is configured. `onBeat` and the main screen's manual Trigger button
     * both resolve through this single function, so beat-type MIDI config and per-beat overrides
     * are one resolution path, not two independent ones a future
     * reader has to reconcile. [knownSound] lets a caller that already computed [beatTypeFor] for
     * this same beat (`onBeat` always has, for [BeatPhase.isAccent]) pass it straight in rather
     * than this function silently recomputing it - one fewer [_timeSignature] read and enum
     * `when` on `onBeat`'s own timing-critical clock thread. Passing nothing (the common case for
     * the Trigger button, which doesn't already have one lying around) resolves it fresh, exactly
     * as before this parameter existed. */
    fun resolveMidiActionForBeat(beatIndexForBeat: Int, knownSound: ClickSound? = null): MidiBeatAction {
        val sound = knownSound ?: beatTypeFor(beatIndexForBeat)
        return _timeSignature.value.midiOverrideAt(beatIndexForBeat)
            ?: MidiActionSender.actions.value[sound]
            ?: MidiBeatAction()
    }

    /** [ClickPlayer]-fallback-only: fires (or schedules) [sound] for a beat whose real timestamp
     * ([beatTimestampNanos]) has already arrived - the reactive path used whenever the audio
     * scheduling loop didn't already fire this beat early, which covers positive [audioOffsetMs]
     * as well as any beat the lookahead behavior is disabled for (click off, or following an
     * external MIDI clock - see [startAudioScheduling]; a non-positive offset is normally caught by
     * that loop first). Not called at all when [usingStreamingClickEngine] is true - [onBeat]
     * pushes straight to [StreamingClickEngine.scheduleBeat] instead, since sample-accurate
     * placement no longer needs this function's careful "schedule off the absolute timestamp, not
     * a naive relative delay" trick to avoid compounding latency. A negative offset reaching here
     * (MIDI-follow mode, where lookahead is intentionally skipped) simply computes a target already
     * in the past and fires immediately - documented no-op behavior, not a silent pretense of
     * leading a clock this engine doesn't control. */
    private fun scheduleReactiveAudio(sound: ClickSound, beatTimestampNanos: Long) {
        val fireAtNanos = beatTimestampNanos + (_audioOffsetMs.value * 1_000_000.0).toLong()
        val now = System.nanoTime()
        if (fireAtNanos <= now) {
            playClickSafely(sound)
        } else {
            audioScope.launch {
                delay((fireAtNanos - now) / 1_000_000)
                playClickSafely(sound)
            }
        }
    }

    /** [ClickPlayer]-fallback-only: the single point every click - lookahead or reactive - actually
     * fires through. Re-checking [_clickEnabled] and [BeatPhase.isPlaying] right here (not just at
     * the moment the fire was scheduled or resolved) is what stops a click from sounding after the
     * user has already pressed stop: [scheduleReactiveAudio]'s delayed coroutine isn't cancelled by
     * [stop] the way [renderJob]/[audioSchedulingJob] are, and even the scheduling loop's own
     * iteration can be mid-flight past its gating check at the exact moment [stop] runs. */
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
        renderJob = renderScope.launch {
            while (isActive) {
                renderFrame()
                delay(FRAME_INTERVAL_MS)
            }
        }
    }

    /**
     * Keeps the audio-trigger path fed for beats not yet reached by [onBeat] - gated on: only
     * while playing, [clickEnabled] is on, [audioOffsetMs] is *not positive* (negative or exactly
     * zero - see below for why zero belongs here now), and timing isn't coming from an external
     * MIDI clock (a followed clock is already reactive with nothing of its own to predict -
     * [scheduleReactiveAudio] fires immediately there instead, a documented no-op rather than a
     * silent pretense of leading a clock this engine doesn't control). A *positive* offset (a
     * deliberate lag, not a lead) stays reactive-only, unchanged - predictively resolving ahead of
     * [onBeat]'s own beat-counter update would fire [clickListenerForTesting] before `totalBeats`
     * advances, breaking the "click trails the real beat" contract a positive offset specifically
     * promises (see the reactive-delay test in `MetronomeEngineTest.kt`).
     *
     * **Why zero belongs on the "lead-scheduling" side, not the "reactive" side, as of this
     * round**: this condition used to be a strict `< 0f`, on the reasoning that a non-negative
     * offset means "no pre-roll wanted, so nothing to lead." That conflated two different things -
     * the user's own *perceptual* pre-roll preference ([audioOffsetMs] itself) with the streaming
     * engine's *structural* need for some lead time to place any click precisely at all (see
     * [StreamingClickEngine.mixPendingBeatIfDue]'s clamp-to-earliest-available behavior - a target
     * that arrives without enough notice lands late by roughly the buffer's own margin regardless
     * of what the configured offset is). Went unnoticed while the shipped default was negative
     * (`-30ms`, always satisfying the old `< 0f` check); became a real, measured regression the
     * moment [DEFAULT_AUDIO_OFFSET_MS] changed to `0f` (see `docs/timing-accuracy-benchmark.md`)
     * - a fresh install would have silently lost lead-scheduling for *every* beat, not just beat 0.
     * What "feeding" means depends on [usingStreamingClickEngine]:
     *  - **streaming**: refines the streaming engine's pending schedule for the *upcoming* beat
     *    ([refreshPredictedSchedule]), gated the same "only once we're within the offset's own lead
     *    window" way as the fallback path below - plus [StreamingClickEngine.leadMarginNanos]'s
     *    extra lead (capped at [MAX_STREAMING_LEAD_MARGIN_NANOS]), since a push that only beats the
     *    *offset* deadline can still arrive after the writer's own buffer-ahead has already
     *    committed that frame - see that function's kdoc. Deliberately *not* resolving the instant
     *    a beat becomes current (a full beat ahead) despite the writer's sample-frame placement not
     *    strictly needing tight timing from this loop otherwise - resolving (and rolling
     *    [effectiveMuteProbability]'s mute chance) only stays predictable at a bounded, consistent
     *    distance from the real beat, not "as early as possible."
     *  - **fallback ([ClickPlayer])**: unchanged from before this round - a real one-shot trigger
     *    call still needs the old genuine wait-then-fire behavior, since there's no sample-accurate
     *    placement underneath it to correct for imprecise firing, and no writer buffer to race
     *    against either.
     *
     * Also polls [StreamingClickEngine.hasFailedWarmup] once per iteration regardless of gating -
     * the one place a mid-session fallback to [ClickPlayer] gets noticed and applied, since this
     * loop already runs for as long as the engine is playing. Effectively a first-launch-only
     * concern now that [StreamingClickEngine] stays warm across [stop]/[start] cycles instead of
     * re-warming on every one: its internal readiness never resets to false again once a session
     * has warmed up successfully, so a later session can only hit this path if the very first one
     * already failed (or the writer itself dies - see [StreamingClickEngine.start]'s own liveness
     * check for that recovery path, which happens on the *next* [start] instead).
     *
     * Polls in slices bounded by [AUDIO_LOOKAHEAD_POLL_NANOS] (matching
     * `MidiClockSender.RESYNC_POLL_NANOS`'s precedent) while gated, and every iteration delays a
     * bounded slice unconditionally - without that, a fixed beat's resolved/scheduled state would
     * busy-spin one of the engine's dedicated timing threads at 100% CPU until [onBeat] eventually
     * clears it, up to a full `|audioOffsetMs|` window later. Measured as the actual cause of
     * audible timing trouble at high BPM before this loop's first fix; still the reason every
     * branch here ends in a `delay()`.
     *
     * Cancels and relaunches on every call, deliberately - unlike [StreamingClickEngine]'s
     * `AudioTrack`/writer (genuinely expensive to rebuild, hence kept warm across sessions), this
     * coroutine owns no resource worth preserving, only control flow. Tried keeping it warm too
     * (mirroring that fix), reasoning it would eliminate a same-shaped cold-dispatch tax - measured
     * on real hardware instead to be a net *regression* (see `docs/timing-accuracy-benchmark.md`'s
     * results table), because a warm-but-idle instance of this loop is asleep inside its own
     * [AUDIO_LOOKAHEAD_IDLE_POLL_MS] `delay()` while gated off, and a `delay()` already in flight
     * can't be woken early just because [start] flips `isPlaying` true - it only notices on its
     * *next* wake, up to a full [AUDIO_LOOKAHEAD_IDLE_POLL_MS] later. A fresh relaunch has no such
     * in-flight sleep to wait out: its first iteration runs immediately, already seeing the new
     * state. Reverted for that reason - this is the opposite lifecycle from the writer, not a
     * smaller version of the same fix.
     */
    private fun startAudioScheduling() {
        audioSchedulingJob?.cancel()
        audioSchedulingJob = audioScope.launch {
            while (isActive) {
                if (usingStreamingClickEngine && streamingClickEngine.hasFailedWarmup()) {
                    Log.w(TAG, "Streaming click engine failed to warm up in time; falling back to discrete retrigger playback")
                    streamingClickEngine.stop()
                    usingStreamingClickEngine = false
                }
                val offsetMs = _audioOffsetMs.value
                val gated = _state.value.isPlaying && _clickEnabled.value && offsetMs <= 0f && !usingMidiClock
                if (!gated) {
                    delay(AUDIO_LOOKAHEAD_IDLE_POLL_MS)
                    continue
                }
                // The streaming path needs to push a schedule earlier than the offset alone would
                // dictate - see leadMarginNanos()'s kdoc for why - the fallback path doesn't (a
                // discrete AudioTrack.play() call has no equivalent "already committed" state to
                // race against, so 0 margin reproduces this loop's exact pre-existing behavior for
                // it). Capped by *both* an absolute ceiling and a fraction of the current beat
                // interval - the absolute cap is what actually binds at slow tempos (where a
                // fraction of the interval would be huge), the fractional cap is what binds at fast
                // ones (where the absolute cap alone could still encroach into "resolving more than
                // one beat ahead", the exact class of bug this loop's own gating exists to avoid -
                // see refreshPredictedSchedule's kdoc). If a device's real buffer-ahead exceeds both
                // caps at the current tempo, genuinely-precise lead placement isn't achievable
                // there anyway - degrading gracefully (fire at the earliest frame the writer can
                // still place it - see StreamingClickEngine.mixPendingBeatIfDue) beats resolving
                // unpredictably far ahead to chase it.
                val intervalNanos = 60_000_000_000.0 / _state.value.bpm
                val leadMarginNanos = if (usingStreamingClickEngine) {
                    minOf(
                        streamingClickEngine.calibratedLeadMarginNanos().toDouble(),
                        MAX_STREAMING_LEAD_MARGIN_NANOS.toDouble(),
                        intervalNanos * MAX_STREAMING_LEAD_MARGIN_BEAT_FRACTION,
                    )
                } else {
                    0.0
                }
                val remainingNanos = nanosUntilNextBeat(System.nanoTime(), _state.value.bpm) + offsetMs * 1_000_000.0 - leadMarginNanos
                if (remainingNanos <= 0.0) {
                    if (usingStreamingClickEngine) refreshPredictedSchedule(offsetMs) else fireLookaheadAudioIfNeeded()
                }
                val sleepNanos = if (remainingNanos > 0.0) minOf(remainingNanos, AUDIO_LOOKAHEAD_POLL_NANOS.toDouble()) else AUDIO_LOOKAHEAD_POLL_NANOS.toDouble()
                delay((sleepNanos / 1_000_000.0).toLong().coerceAtLeast(1))
            }
        }
    }

    /** The streaming path's predictive step, for the upcoming beat identified by the *current*
     * [totalBeats]/[beatIndex] fields (not yet incremented - see [onBeat]'s own end-of-function
     * increment). Registers the resolved decision with [streamingClickEngine] every call - safe to
     * call repeatedly for the same beat (see [StreamingClickEngine.scheduleBeat]) - but only fires
     * the test-listener hook once, at first resolution, mirroring [fireLookaheadAudioIfNeeded]'s
     * exact "resolved once, observed once" semantics so existing timestamp-based tests observe the
     * same lead-vs-lag shape regardless of which audio path is active. */
    private fun refreshPredictedSchedule(offsetMs: Float) {
        val targetTotalBeats = totalBeats
        val targetBeatIndex = beatIndex
        val resolved = synchronized(audioResolutionLock) {
            resolvedBeatAudioCache[targetTotalBeats] ?: resolveBeatAudio(targetTotalBeats, targetBeatIndex).also {
                resolvedBeatAudioCache[targetTotalBeats] = it
                it.sound?.let { sound -> clickListenerForTesting?.invoke(sound, System.nanoTime()) }
            }
        }
        val predictedNanos = lastBeatNanos + (60_000_000_000.0 / _state.value.bpm).toLong()
        val fireAtNanos = predictedNanos + (offsetMs * 1_000_000.0).toLong()
        streamingClickEngine.scheduleBeat(targetTotalBeats, resolved.sound, fireAtNanos)
    }

    /** The fallback path's resolve-and-fire step - see [startAudioScheduling]. A no-op if this beat
     * was already resolved (by an earlier poll of this same loop - there is no other caller this
     * early), which is what stops a beat from firing more than once across repeated polls while its
     * offset window is still open. */
    private fun fireLookaheadAudioIfNeeded() {
        val targetTotalBeats = totalBeats
        val targetBeatIndex = beatIndex
        val resolved = synchronized(audioResolutionLock) {
            if (resolvedBeatAudioCache.containsKey(targetTotalBeats)) {
                null
            } else {
                resolveBeatAudio(targetTotalBeats, targetBeatIndex).also { resolvedBeatAudioCache[targetTotalBeats] = it }
            }
        }
        resolved?.sound?.let { playClickSafely(it) }
    }

    /** Nanoseconds remaining until the *next* beat is predicted to land, extrapolating from
     * [lastBeatNanos] at [bpm] - the one formula [renderFrame]'s phase calculation and the audio
     * scheduling loop ([startAudioScheduling]) both schedule from, instead of two independently-
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

    /** Bakes the ambient per-bar/per-beat background and/or the radial per-phrase indicator (see
     * [QueueOverlay]) into an already-rendered frame - each independently a no-op when there's
     * nothing to indicate or its own toggle is off, so the common case (single bar, single phrase)
     * leaves every visualizer's own frame completely untouched. [_queueOverlayEnabled] and
     * [_phraseIndicatorEnabled] are consumed here (not inside [QueueOverlay.apply] itself, which
     * has no notion of either toggle) by feeding a size-0 [queue]/`phraseCount` when a toggle is
     * off, reusing [QueueOverlay.apply]'s own per-section no-op guards rather than adding new
     * ones - `emptyList()` specifically (not e.g. `queue.take(1)`), since it's a cached Kotlin
     * singleton with no per-frame allocation, unlike building a new one-element list on this
     * ~100fps render path every time a toggle happens to be off.
     *
     * [QueueOverlay.apply]'s `minBpm`/`maxBpm` are [queue]'s own observed bpm range, not the
     * fixed [MIN_BPM]/[MAX_BPM] constants this used to pass - the same "scale relative to what's
     * actually queued, not a boundary most bars never approach" fix `BarQueueDots`'s row height
     * got in `MainScreen.kt` (see its own kdoc), for the same reason: any bar using the extended
     * BPM range used to silently clip to the same min/max row thickness as every other
     * extended-range bar, on this row's on-screen twin as well as the physical Glyph Matrix
     * itself. [queue] (not the size-0 substitution above) is always non-empty - the bar queue can
     * never be empty - so this is safe to compute unconditionally, even though it's only actually
     * used when [showRows] is true. */
    private fun withQueueOverlay(rendered: IntArray, beatIndex: Int, phase: Float): IntArray {
        val queue = _timeSignatureQueue.value
        val phrases = _phrases.value
        val showRows = queue.size > 1 && _queueOverlayEnabled.value
        val showPhraseIndicator = phrases.size > 1 && _phraseIndicatorEnabled.value
        if (!showRows && !showPhraseIndicator) return rendered
        return QueueOverlay.apply(
            rendered,
            matrixSize,
            if (showRows) queue else emptyList(),
            _queueIndex.value,
            beatIndex,
            phase,
            minBpm = queue.minOf { it.bpm },
            maxBpm = queue.maxOf { it.bpm },
            phraseCount = if (showPhraseIndicator) phrases.size else 1,
            activePhraseIndex = _activePhraseIndex.value,
        )
    }

    private const val TAG = "MetronomeEngine"
    private const val MAX_TAP_GAP_MS = 2000L
    private const val MAX_TAP_SAMPLES = 5

    /** Render loop tick interval (100fps) - tightened from 25ms/40fps this round: bounds the
     * worst-case gap between a beat firing and the render loop next sampling it for the visual
     * flash, independently of the audio path rewrite above (visuals were never routed through
     * [StreamingClickEngine] - they stay on [renderScope]'s own wall-clock polling). Runs on its
     * own dedicated, elevated-priority thread (see [renderScope]), so this doesn't cost anything
     * shared with the audio/clock roles - verify on-device this doesn't visibly cost battery or
     * frame drops before tightening further. */
    private const val FRAME_INTERVAL_MS = 10L
    private const val BPM_PERSIST_DEBOUNCE_MS = 250L

    /** Poll slice for [startAudioScheduling] while gated on (fallback: closing in on a target fire
     * time; streaming: refreshing the predicted schedule) - matches
     * `MidiClockSender.RESYNC_POLL_NANOS`'s precedent. */
    private const val AUDIO_LOOKAHEAD_POLL_NANOS = 3_000_000L

    /** Poll interval for [startAudioScheduling] while its gating conditions aren't met (click off,
     * non-negative offset, MIDI-follow, or not playing) - these rarely change faster than a frame,
     * so there's no need to re-check at [AUDIO_LOOKAHEAD_POLL_NANOS]'s much tighter cadence. */
    private const val AUDIO_LOOKAHEAD_IDLE_POLL_MS = 25L

    /** Absolute ceiling on [StreamingClickEngine.leadMarginNanos]'s contribution to
     * [startAudioScheduling]'s gating - real low-latency `AudioTrack` buffers are typically single-
     * digit-to-tens of milliseconds, so 100ms is generous headroom for an unusually large buffer on
     * some device/OEM audio stack. This is what actually binds at slow tempos, where
     * [MAX_STREAMING_LEAD_MARGIN_BEAT_FRACTION]'s share of the interval would otherwise be huge -
     * see that constant for the fast-tempo case this one doesn't cover by itself. If a device's
     * actual buffer exceeds both caps, sample-accurate leading placement degrades gracefully to
     * "fires at the earliest frame the writer can still place it" (see
     * `StreamingClickEngine.mixPendingBeatIfDue`) rather than being silently wrong. */
    private const val MAX_STREAMING_LEAD_MARGIN_NANOS = 100_000_000L

    /** Fractional ceiling on [StreamingClickEngine.leadMarginNanos]'s contribution to
     * [startAudioScheduling]'s gating, as a share of the *current* beat interval - what actually
     * binds at fast tempos, where [MAX_STREAMING_LEAD_MARGIN_NANOS]'s fixed 100ms alone could still
     * push resolution a confusingly long way ahead of the real beat (at 400 BPM's 150ms interval,
     * 100ms would be two-thirds of it - dangerously close to encroaching on the *previous* beat's
     * own resolution window, exactly the "resolving more than one beat ahead" failure mode
     * [refreshPredictedSchedule]'s own kdoc explains and this loop's gating exists to avoid). */
    private const val MAX_STREAMING_LEAD_MARGIN_BEAT_FRACTION = 0.25

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
