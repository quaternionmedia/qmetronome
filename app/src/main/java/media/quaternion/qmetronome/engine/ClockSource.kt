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
 * is due and only delays the remaining gap each iteration.
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
            while (isActive && running) {
                val intervalNanos = (60_000_000_000.0 / this@InternalClockSource.bpm).toLong()
                val now = System.nanoTime()

                // A tempo change made mid-wait otherwise has no effect until this wait finishes -
                // which can be tens of seconds at a slow tempo, and looks exactly like the beat
                // (and therefore the animation, which derives its phase from time-since-last-
                // beat) has frozen solid until the metronome is stopped and restarted. If the
                // *current* bpm's interval has already elapsed since the last beat, resync to
                // now instead of waiting out a schedule computed from a stale, since-changed bpm
                // - the same treatment a process-suspension gap already needed below.
                if (now - lastFireNanos >= intervalNanos) {
                    nextTickNanos = now
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
                // Re-read bpm fresh rather than reusing intervalNanos from above - onBeat() may
                // have just changed the tempo itself (e.g. the bar queue advancing to a bar with
                // a different bpm), and that change must govern the very next beat. Reusing the
                // pre-beat interval here would silently apply the new tempo one beat late.
                val postBeatIntervalNanos = (60_000_000_000.0 / this@InternalClockSource.bpm).toLong()
                nextTickNanos += postBeatIntervalNanos
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
