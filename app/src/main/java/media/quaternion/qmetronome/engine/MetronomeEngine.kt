package media.quaternion.qmetronome.engine

import android.content.Context
import android.util.Log
import media.quaternion.qmetronome.midi.MidiClockSource
import media.quaternion.qmetronome.visualizers.GlyphVisualizer
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

    /** Where beat timing is currently coming from. */
    sealed interface ClockStatus {
        data object Internal : ClockStatus
        data class Midi(val measuredBpm: Float?, val source: String?) : ClockStatus
    }

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

    @Volatile private var matrixSize = 25
    @Volatile private var lastBeatNanos = System.nanoTime()
    @Volatile private var beatIndex = 0
    @Volatile private var totalBeats = 0L
    @Volatile private var usingMidiClock = false

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
        _state.value = BeatPhase.IDLE.copy(bpm = store.bpm, beatsPerBar = store.beatsPerBar)
        _clickEnabled.value = store.clickEnabled
        _visualOffsetMs.value = store.visualOffsetMs
        _compactLandscape.value = store.compactLandscape

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
     */
    fun setBpm(bpm: Float) {
        val clamped = bpm.coerceIn(MIN_BPM, MAX_BPM)
        clock.setBpm(clamped)
        _state.update { it.copy(bpm = clamped) }
        persistBpmJob?.cancel()
        persistBpmJob = scope.launch {
            delay(BPM_PERSIST_DEBOUNCE_MS)
            settings?.bpm = clamped
        }
    }

    fun setBeatsPerBar(count: Int) {
        val clamped = count.coerceIn(1, 12)
        _state.update { it.copy(beatsPerBar = clamped) }
        settings?.beatsPerBar = clamped
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

    fun setVisualizer(visualizer: GlyphVisualizer) {
        _visualizer.value = visualizer
        settings?.visualizerId = visualizer.id
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
        lastBeatNanos = System.nanoTime()
        _state.update { it.copy(isPlaying = true, beatIndex = 0, phase = 0f, isAccent = true) }
        clock.start(scope, _state.value.bpm, ::onBeat)
        startRenderLoop()
    }

    fun stop() {
        clock.stop()
        renderJob?.cancel()
        renderJob = null
        _state.update { it.copy(isPlaying = false, phase = 0f) }
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
     * brightness, so the preview glows faintly on AMOLED when the metronome isn't running. */
    private fun emitIdleFrame() {
        if (_state.value.isPlaying) return
        val idleBeat = _state.value.copy(phase = 0f, isAccent = true, isPlaying = false)
        val rendered = try {
            _visualizer.value.render(matrixSize, idleBeat)
        } catch (_: Exception) {
            IntArray(matrixSize * matrixSize) { 255 }
        }
        _frame.value = IntArray(rendered.size) { i ->
            (rendered[i] * IDLE_GLYPH_SCALE).toInt().coerceIn(0, 255)
        }
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
        lastTapNanos = 0L
        tapIntervalsMs.clear()
        MidiClockSource.resetForTesting()
    }

    private fun onBeat(timestampNanos: Long, measuredBpm: Float?) {
        lastBeatNanos = timestampNanos
        val isAccent = beatIndex == 0
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
        if (_clickEnabled.value) {
            try {
                clickPlayer.playClick(isAccent)
            } catch (e: Exception) {
                Log.e(TAG, "ClickPlayer failed; continuing without audio for this beat", e)
            }
        }
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
            _frame.value = _visualizer.value.render(matrixSize, phaseState)
        } catch (e: Exception) {
            Log.e(TAG, "Visualizer '${_visualizer.value.id}' threw during render(); skipping frame", e)
        }
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
}
