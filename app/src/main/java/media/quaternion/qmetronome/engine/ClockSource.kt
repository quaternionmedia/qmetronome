package media.quaternion.qmetronome.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Produces beat ticks for the metronome. [InternalClockSource] drives ticks from a BPM value;
 * an external implementation (e.g. [media.quaternion.qmetronome.midi.MidiClockSource]) measures
 * tempo instead of being told it, and can be swapped in without touching [MetronomeEngine] or
 * any visualizer.
 */
interface ClockSource {
    /**
     * Starts ticking, invoking [onBeat] on every beat with its nanoTime timestamp and, for
     * sources that measure tempo externally, the freshly measured BPM (null if the source
     * doesn't measure tempo itself, e.g. [InternalClockSource], in which case the engine keeps
     * using whatever BPM it already had).
     */
    fun start(scope: CoroutineScope, bpm: Float, onBeat: (timestampNanos: Long, measuredBpm: Float?) -> Unit)

    /** Sets the tempo to tick at. No-op for sources that derive tempo externally. */
    fun setBpm(bpm: Float)

    fun stop()
}

/**
 * Drift-corrected internal clock: instead of repeatedly delaying by a fixed interval (which
 * accumulates rounding/scheduling error over time), it tracks the absolute time the next beat
 * is due and only delays the remaining gap each iteration. A tempo change - however drastic -
 * always governs the very next beat, in both directions (speeding up *and* slowing down); see the
 * recalculation inside [start].
 */
class InternalClockSource : ClockSource {

    @Volatile
    private var bpm: Float = 120f

    @Volatile
    private var running = false

    private var tickJob: Job? = null

    /** Cancels any previously launched tick loop before starting a new one, so calling [start] twice (e.g. during a self-heal restart) can never result in two coroutines ticking at once. */
    override fun start(scope: CoroutineScope, bpm: Float, onBeat: (Long, Float?) -> Unit) {
        tickJob?.cancel()
        this.bpm = bpm
        running = true
        tickJob = scope.launch {
            var lastFireNanos = System.nanoTime()
            var nextTickNanos = lastFireNanos
            // The very first beat always fires instantly (nextTickNanos starts equal to
            // lastFireNanos, i.e. zero wait) - MidiClockSender's priming-flash logic, the Glyph
            // Toy's instant-feedback-on-select, and this class's own render loop all lean on that.
            // Recalculating nextTickNanos below is only meaningful once there's a *real* previous
            // beat to measure the next interval from.
            var hasFiredOnce = false
            while (isActive && running) {
                val now = System.nanoTime()

                if (hasFiredOnce) {
                    // The pending beat's timing is continuously re-derived from the *current* bpm
                    // relative to when the last real beat fired, every iteration - not reused from
                    // whatever bpm was in effect when that beat fired. A tempo change made mid-
                    // wait otherwise has no effect until the beat *after* next (or, for a
                    // slowdown specifically, never even shortens/extends the current wait at all)
                    // - drastic manual tempo changes need to land on the very next beat in both
                    // directions, the same way a bar-queue-driven change already correctly does
                    // (see MetronomeEngineTest's "a queue advance's tempo change governs the very
                    // next beat" regression test). If that recalculated target is so far in the
                    // past it'd otherwise take a burst of catch-up ticks to reach it (e.g. the
                    // process was suspended), resync to now instead, same as before.
                    val intervalNanos = (60_000_000_000.0 / this@InternalClockSource.bpm).toLong()
                    val recalculatedNextTick = lastFireNanos + intervalNanos
                    nextTickNanos = if (now - recalculatedNextTick > intervalNanos) now else recalculatedNextTick
                }

                val delayMillis = (nextTickNanos - now) / 1_000_000
                if (delayMillis > POLL_SLICE_MS) {
                    // Don't commit to a single long sleep sized for the bpm at this instant -
                    // sleep in short slices and re-check against the live bpm each time, so a
                    // tempo change is noticed within one slice instead of only once whatever the
                    // wait was originally sized for finishes.
                    delay(POLL_SLICE_MS)
                    continue
                }
                if (delayMillis > 0) delay(delayMillis)
                onBeat(nextTickNanos, null)
                lastFireNanos = nextTickNanos
                hasFiredOnce = true
            }
        }
    }

    override fun setBpm(bpm: Float) {
        this.bpm = bpm
    }

    override fun stop() {
        running = false
        tickJob?.cancel()
        tickJob = null
    }

    private companion object {
        /** How often the tick loop re-checks the live bpm while waiting out a long interval. */
        const val POLL_SLICE_MS = 30L
    }
}
