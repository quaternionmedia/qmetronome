package com.example.qmetronome.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Produces beat ticks for the metronome. [InternalClockSource] drives ticks from a BPM value;
 * an external implementation (e.g. [com.example.qmetronome.midi.MidiClockSource]) measures
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
            var nextTickNanos = System.nanoTime()
            while (isActive && running) {
                val now = System.nanoTime()
                val delayMillis = (nextTickNanos - now) / 1_000_000
                if (delayMillis > 0) delay(delayMillis)
                onBeat(nextTickNanos, null)
                val intervalNanos = (60_000_000_000.0 / this@InternalClockSource.bpm).toLong()
                nextTickNanos += intervalNanos
                // If we fell badly behind (e.g. process was suspended), resync instead of
                // firing a burst of catch-up ticks.
                val behindBy = System.nanoTime() - nextTickNanos
                if (behindBy > intervalNanos) {
                    nextTickNanos = System.nanoTime() + intervalNanos
                }
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
}
