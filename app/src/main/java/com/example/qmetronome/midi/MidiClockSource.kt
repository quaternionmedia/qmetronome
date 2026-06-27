package com.example.qmetronome.midi

import android.media.midi.MidiReceiver
import android.util.Log
import com.example.qmetronome.engine.ClockSource
import kotlinx.coroutines.CoroutineScope

/**
 * A [ClockSource] driven by MIDI Clock (System Real-Time `0xF8`, 24 pulses per quarter note).
 * Any number of transports can feed it bytes via [receiverFor] - [VirtualMidiClockService] for
 * inter-app MIDI, [UsbMidiConnector] for USB devices, and so on for future transports (BLE, etc).
 * See `docs/external-midi-clock.md` for how to plug in a new one.
 *
 * Only one transport's clock actually drives timing at a time ([activeSource]): the first byte
 * from a source claims it, and a different source can only take over after the current one has
 * gone quiet for [SOURCE_TAKEOVER_SILENCE_MS]. Without this, two simultaneously-connected
 * sources (e.g. a virtual cable *and* a USB device both sending clock) would both feed the same
 * tick counter and fire beats at whatever the combined rate works out to - which is exactly the
 * "fires way faster than the displayed BPM" symptom this guards against.
 */
object MidiClockSource : ClockSource {

    enum class Source { VIRTUAL, USB }

    private val receiversBySource = mutableMapOf<Source, MidiReceiver>()

    /** Gets (or lazily creates) the [MidiReceiver] a given transport should feed raw bytes into. */
    fun receiverFor(source: Source): MidiReceiver = receiversBySource.getOrPut(source) {
        object : MidiReceiver() {
            override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                for (i in 0 until count) {
                    handleByte(source, msg[offset + i].toInt() and 0xFF, timestamp)
                }
            }
        }
    }

    /** Which transport is currently driving the clock, if any. */
    @Volatile
    var activeSource: Source? = null
        private set

    /** Fires on every accepted real-time byte - lets the engine auto-switch to MIDI the moment clock starts arriving. */
    var onExternalActivity: (() -> Unit)? = null

    /** Fires on MIDI Start/Continue (`0xFA`/`0xFB`) from the active source. */
    var onTransportStart: (() -> Unit)? = null

    /** Fires on MIDI Stop (`0xFC`) from the active source. */
    var onTransportStop: (() -> Unit)? = null

    @Volatile
    private var onBeatCallback: ((timestampNanos: Long, measuredBpm: Float?) -> Unit)? = null

    private var lastTickNanos = 0L
    private var lastBeatFiredNanos = 0L
    private var lastByteWallClockMillis = 0L
    private var tickCount = 0
    private val recentTickIntervalsNanos = ArrayDeque<Long>()

    override fun start(scope: CoroutineScope, bpm: Float, onBeat: (Long, Float?) -> Unit) {
        onBeatCallback = onBeat
    }

    override fun setBpm(bpm: Float) {
        // Tempo is measured from incoming clock ticks, not set externally.
    }

    override fun stop() {
        onBeatCallback = null
    }

    private fun handleByte(source: Source, byte: Int, timestampNanos: Long) {
        val nowMillis = System.currentTimeMillis()
        if (activeSource != source) {
            val silentForMillis = nowMillis - lastByteWallClockMillis
            if (activeSource != null && silentForMillis < SOURCE_TAKEOVER_SILENCE_MS) {
                // Another source is still actively driving the clock - ignore this one rather
                // than double-counting both into the same tick counter.
                return
            }
            Log.d(TAG, "clock source switching to $source (was $activeSource)")
            activeSource = source
            resetTiming()
        }
        lastByteWallClockMillis = nowMillis

        onExternalActivity?.invoke()
        when (byte) {
            CLOCK_TICK -> onClockTick(timestampNanos)
            START, CONTINUE -> {
                resetTiming()
                onTransportStart?.invoke()
            }
            STOP -> onTransportStop?.invoke()
        }
    }

    private fun onClockTick(timestampNanos: Long) {
        val previous = lastTickNanos
        lastTickNanos = timestampNanos
        if (previous != 0L) {
            val interval = timestampNanos - previous
            if (interval > 0) {
                recentTickIntervalsNanos.addLast(interval)
                if (recentTickIntervalsNanos.size > SMOOTHING_WINDOW) {
                    recentTickIntervalsNanos.removeFirst()
                }
            }
        }
        tickCount++
        if (tickCount >= TICKS_PER_BEAT) {
            val ticksThisBeat = tickCount
            tickCount = 0
            val measuredBpm = measuredBpmOrNull()
            val sinceLastBeatMs = if (lastBeatFiredNanos == 0L) null else (timestampNanos - lastBeatFiredNanos) / 1_000_000
            lastBeatFiredNanos = timestampNanos
            Log.d(TAG, "beat fired: source=$activeSource ticks=$ticksThisBeat measuredBpm=$measuredBpm sinceLastBeatMs=$sinceLastBeatMs")
            onBeatCallback?.invoke(timestampNanos, measuredBpm)
        }
    }

    private fun measuredBpmOrNull(): Float? {
        if (recentTickIntervalsNanos.isEmpty()) return null
        val avgTickIntervalNanos = recentTickIntervalsNanos.average()
        val beatIntervalNanos = avgTickIntervalNanos * TICKS_PER_BEAT
        if (beatIntervalNanos <= 0.0) return null
        return (60_000_000_000.0 / beatIntervalNanos).toFloat()
    }

    private fun resetTiming() {
        tickCount = 0
        lastTickNanos = 0L
        lastBeatFiredNanos = 0L
        recentTickIntervalsNanos.clear()
    }

    /** Clears all state, including which source is active. For tests only - this is a singleton, so state otherwise leaks between test cases. */
    fun resetForTesting() {
        activeSource = null
        onExternalActivity = null
        onTransportStart = null
        onTransportStop = null
        onBeatCallback = null
        lastByteWallClockMillis = 0L
        resetTiming()
    }

    private const val TAG = "MidiClockSource"
    private const val TICKS_PER_BEAT = 24
    private const val SMOOTHING_WINDOW = 24

    /** How long a source can go quiet before a different source is allowed to take over the clock. */
    private const val SOURCE_TAKEOVER_SILENCE_MS = 500L

    // MIDI 1.0 System Real-Time messages.
    private const val CLOCK_TICK = 0xF8
    private const val START = 0xFA
    private const val CONTINUE = 0xFB
    private const val STOP = 0xFC
}
