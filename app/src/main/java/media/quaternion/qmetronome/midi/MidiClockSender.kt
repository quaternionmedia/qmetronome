package media.quaternion.qmetronome.midi

import android.content.Context
import android.media.midi.MidiReceiver
import android.util.Log
import java.util.concurrent.CopyOnWriteArraySet
import media.quaternion.qmetronome.engine.ClockTimingMode
import media.quaternion.qmetronome.engine.MetronomeEngine
import media.quaternion.qmetronome.engine.MetronomeSettings
import media.quaternion.qmetronome.engine.TempoStabilizer
import media.quaternion.qmetronome.engine.newTimingDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Sends this app's tempo out as MIDI Clock, the mirror image of [MidiClockSource]: instead of
 * following an external clock, other apps (or, once a destination is registered for it, USB
 * gear) can follow *us*. Always derives from [MetronomeEngine.state] regardless of where that
 * tempo actually came from - if the engine is currently following an external clock itself, this
 * naturally becomes a repeater for it, with no special-casing needed.
 *
 * Destinations are anything that can receive raw MIDI bytes - [VirtualMidiClockService] registers
 * its declared output port's receiver here in `onCreate()`/unregisters in `onClose()`, and a USB
 * destination would be exactly the same shape (a `MidiInputPort`, which is also a `MidiReceiver`).
 *
 * [timingMode] governs how hard this class corrects for its own natural timing imperfection - see
 * [ClockTimingMode], [effectiveBpm], and the resync logic in [startLoop].
 */
object MidiClockSender {

    @Volatile
    private var settings: MetronomeSettings? = null

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Clock-out loop failed", throwable)
    }
    private val scope = CoroutineScope(SupervisorJob() + exceptionHandler + newTimingDispatcher("midi-clock-out"))

    // addDestination()/removeDestination() are called from the main thread (UI button presses,
    // service onCreate()/onClose()), while sendByte() iterates this from the tick loop's
    // background dispatcher - a plain MutableSet would risk a ConcurrentModificationException if
    // a destination connects/disconnects mid-tick. CopyOnWriteArraySet is built for exactly this
    // shape: rare writes, frequent lock-free reads.
    private val destinations = CopyOnWriteArraySet<MidiReceiver>()

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _timingMode = MutableStateFlow(ClockTimingMode.MECHANICAL)
    val timingMode: StateFlow<ClockTimingMode> = _timingMode.asStateFlow()

    private var tickJob: Job? = null

    // Written from the tick loop's background dispatcher every iteration, but also read/written
    // from the main thread in stopLoop() (setEnabled(false) can race a still-cancelling tick
    // job) - @Volatile for cross-thread visibility, same convention MetronomeEngine uses for its
    // own cross-thread flags.
    @Volatile
    private var wasPlaying = false

    // Smooths MetronomeEngine.state.bpm before it drives the outgoing tick schedule - see
    // TempoStabilizer's own doc for why. Only engaged in ClockTimingMode.MECHANICAL while actually
    // following an external clock (see effectiveBpm()); Organic mode, and any manually-set/
    // internal-clock tempo, pass straight through unsmoothed.
    private val tempoStabilizer = TempoStabilizer()
    private var wasStabilizing = false
    private var lastRawBpm: Float? = null

    /** Loads the persisted on/off state. Safe to call multiple times. */
    fun attach(context: Context) {
        if (settings != null) return
        val store = MetronomeSettings(context.applicationContext)
        settings = store
        _timingMode.value = store.clockOutTimingMode
        if (store.clockOutEnabled) setEnabled(true)
    }

    fun setTimingMode(mode: ClockTimingMode) {
        _timingMode.value = mode
        settings?.clockOutTimingMode = mode
    }

    fun addDestination(receiver: MidiReceiver) {
        destinations.add(receiver)
    }

    fun removeDestination(receiver: MidiReceiver) {
        destinations.remove(receiver)
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled == _enabled.value) return
        _enabled.value = enabled
        settings?.clockOutEnabled = enabled
        if (enabled) startLoop() else stopLoop()
    }

    private fun startLoop() {
        wasPlaying = false
        wasStabilizing = false
        lastRawBpm = null
        tempoStabilizer.reset()
        tickJob?.cancel()
        tickJob = scope.launch {
            var nextTickNanos = System.nanoTime()
            // Tracks MetronomeEngine's own beat counter so Mechanical mode can notice, once per
            // real beat, that a new one has fired - see the resync block below.
            var lastSeenTotalBeats = -1L
            while (isActive && _enabled.value) {
                val state = MetronomeEngine.state.value
                if (state.isPlaying != wasPlaying) {
                    sendByte(if (state.isPlaying) START else STOP)
                    wasPlaying = state.isPlaying
                    nextTickNanos = System.nanoTime()
                    lastSeenTotalBeats = state.totalBeats
                }
                if (state.isPlaying) {
                    // This loop's own 24-ticks-per-beat schedule is otherwise entirely free-
                    // running, seeded only at Start - it and MetronomeEngine's actual beat firing
                    // are two independent timers nominally at the same rate but with no shared
                    // phase reference, so they can gradually drift apart over a long session even
                    // though the *tempo* matches. In Mechanical mode, re-anchor to "now" the
                    // moment a new real beat is noticed, so the outgoing clock's own beat boundary
                    // never drifts far from the engine's actual one.
                    if (_timingMode.value == ClockTimingMode.MECHANICAL) {
                        // Poll in small, tempo-independent slices instead of a single delay() up
                        // to the next scheduled tick - checking only once per outgoing tick (as
                        // Organic mode still does below, where this resync doesn't apply anyway)
                        // means the detection latency scales with the tick interval, i.e. gets
                        // *worse* at slower tempos (~42ms at 60 BPM vs ~10ms at 240 BPM) - backwards
                        // for a mode whose whole point is the tightest possible phase lock
                        // regardless of BPM.
                        while (true) {
                            val remainingNanos = nextTickNanos - System.nanoTime()
                            if (remainingNanos <= 0) break
                            delay((minOf(remainingNanos, RESYNC_POLL_NANOS) / 1_000_000L).coerceAtLeast(1))
                            val polledBeats = MetronomeEngine.state.value.totalBeats
                            if (polledBeats != lastSeenTotalBeats) {
                                nextTickNanos = System.nanoTime()
                                lastSeenTotalBeats = polledBeats
                                break
                            }
                        }
                    } else {
                        // Organic mode never resyncs mid-beat - let this free-running schedule be.
                        val delayMillis = (nextTickNanos - System.nanoTime()) / 1_000_000
                        if (delayMillis > 0) delay(delayMillis)
                    }
                    val tickState = MetronomeEngine.state.value
                    lastSeenTotalBeats = tickState.totalBeats
                    sendByte(CLOCK_TICK)
                    val intervalNanos = (60_000_000_000.0 / (effectiveBpm(tickState.bpm) * TICKS_PER_BEAT)).toLong()
                    nextTickNanos += intervalNanos
                    // If we fell badly behind (e.g. process was suspended), resync instead of
                    // firing a burst of catch-up ticks - same approach as InternalClockSource.
                    val behindBy = System.nanoTime() - nextTickNanos
                    if (behindBy > intervalNanos) {
                        nextTickNanos = System.nanoTime() + intervalNanos
                    }
                } else {
                    // Not playing - just wait for a state change rather than ticking.
                    delay(IDLE_POLL_MS)
                }
            }
        }
    }

    /**
     * The BPM actually used to derive the outgoing tick interval. While following an external
     * clock in [ClockTimingMode.MECHANICAL], [rawBpm] is [MidiClockSource]'s measured tempo -
     * already smoothed once over its own received ticks, but still a real measurement that can
     * legitimately shift a little beat to beat. Passing that straight through would re-quantize
     * every one of those shifts into an actual timing change in *our* outgoing stream, on top of
     * whatever instability the followed clock already had - so [tempoStabilizer] damps it further
     * here. [ClockTimingMode.ORGANIC], and any manually-set or internal-clock tempo, pass through
     * unsmoothed instead - the former by choice (let real variance through), the latter because a
     * deliberate, discrete tempo change the user made isn't measurement noise and should reach
     * anything following us immediately.
     */
    private fun effectiveBpm(rawBpm: Float): Float {
        val stabilize = _timingMode.value == ClockTimingMode.MECHANICAL &&
            MetronomeEngine.clockStatus.value is MetronomeEngine.ClockStatus.Midi
        if (stabilize && !wasStabilizing) {
            tempoStabilizer.reset()
            lastRawBpm = null
        }
        wasStabilizing = stabilize

        if (!stabilize) return rawBpm.coerceAtLeast(1f)

        if (rawBpm != lastRawBpm) {
            lastRawBpm = rawBpm
            tempoStabilizer.update(rawBpm)
        }
        return (tempoStabilizer.current ?: rawBpm).coerceAtLeast(1f)
    }

    private fun stopLoop() {
        tickJob?.cancel()
        tickJob = null
        if (wasPlaying) sendByte(STOP)
        wasPlaying = false
    }

    private fun sendByte(byte: Int) {
        if (destinations.isEmpty()) return
        val message = byteArrayOf(byte.toByte())
        val timestamp = System.nanoTime()
        for (destination in destinations) {
            try {
                destination.send(message, 0, 1, timestamp)
            } catch (e: Exception) {
                Log.w(TAG, "Failed sending to a clock-out destination", e)
            }
        }
    }

    private const val TAG = "MidiClockSender"
    private const val TICKS_PER_BEAT = 24
    private const val IDLE_POLL_MS = 50L

    /** Max slice while polling for a Mechanical-mode resync - bounds detection latency to this
     * regardless of tempo, rather than to the (tempo-dependent) outgoing tick interval. */
    private const val RESYNC_POLL_NANOS = 3_000_000L

    // MIDI 1.0 System Real-Time messages.
    private const val CLOCK_TICK = 0xF8
    private const val START = 0xFA
    private const val STOP = 0xFC
}
