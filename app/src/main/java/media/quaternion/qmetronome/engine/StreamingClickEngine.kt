package media.quaternion.qmetronome.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Sample-clocked, continuously-running click engine - the real-time rewrite `ClickPlayer`'s
 * discrete `MODE_STATIC` retrigger model can't reach. Owns one `AudioTrack` in `MODE_STREAM`,
 * built once and played continuously for as long as the metronome is running; a dedicated writer
 * thread mixes each click's waveform into the stream at an exact sample-frame offset, computed
 * from `AudioTrack`'s own hardware-sampled frame<->nanoTime mapping ([AudioTrack.getTimestamp])
 * rather than from a coroutine waking up at approximately the right wall-clock moment. The
 * audible result is timed by the audio hardware's own sample clock - the same mechanism every
 * professional audio scheduler (DAWs, Web Audio API, Ableton Link) relies on for this.
 *
 * [MetronomeEngine] still owns *when a beat's click is due* (`resolveBeatAudio`,
 * `nanosUntilNextBeat`, the resolve-once-per-beat cache) exactly as it did before this engine
 * existed - this class only owns *placing* an already-decided click at an exact sample position.
 * [scheduleBeat] is the entire handoff between the two: "beat N resolves to sound S, due at
 * nanoTime T" - idempotent and safe to call repeatedly for the same beat (e.g. as bpm changes
 * before it's actually written into the stream), and a no-op once that beat has already been
 * consumed by the writer.
 *
 * The frame<->nanoTime mapping ([frameForNanoTime]) is self-calibrated against [System.nanoTime]
 * on every writer-loop iteration rather than trusting a documented timebase for
 * [AudioTrack.getTimestamp] - the overload that lets a caller request a specific, named timebase
 * (`AudioTimestamp.TIMEBASE_MONOTONIC`) is `@SystemApi`, not present in the public SDK a normal app
 * can compile against. See [start]'s writer loop for the calibration itself.
 *
 * If `MODE_STREAM` construction fails, or [AudioTrack.getTimestamp] never becomes valid within a
 * short warm-up window, [MetronomeEngine] falls back to [ClickPlayer]'s discrete-retrigger path for
 * the rest of that session - see [hasFailedWarmup]. Real Android hardware/OEM variability is a
 * genuine risk for a change this deep in the audio stack, and "no click at all" is worse than "the
 * old, already-shipped mechanism."
 */
class StreamingClickEngine {

    // Lazy for the same reason as ClickPlayer's: constructed well before the first real click is
    // needed, no reason to touch the audio subsystem that early.
    private val sampleRateHz by lazy {
        AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC).takeIf { it > 0 } ?: 44_100
    }

    private val specs = mutableMapOf<ClickSound, ClickSpec>().apply {
        ClickSound.entries.forEach { put(it, ClickSpec.defaultFor(it)) }
    }

    // Rendered lazily per-sound and cached - re-rendering a click's waveform on every mix-in
    // would be wasted work on the one thread that can least afford it.
    private val renderedSamples = mutableMapOf<ClickSound, ShortArray>()

    @Volatile private var track: AudioTrack? = null
    private var writerJob: Job? = null
    @Volatile private var framesWritten = 0L
    @Volatile private var ready = false
    @Volatile private var warmupDeadlineNanos = Long.MAX_VALUE

    // The AudioTrack's own buffer capacity, in frames - see leadMarginNanos().
    @Volatile private var bufferFrames = 0

    // The writer loop's self-calibrated frame<->nanoTime reference point - see frameForNanoTime.
    // Both only ever written from the writer's own single dedicated thread, but read from there
    // too (mixPendingBeatIfDue runs on the same thread) - @Volatile only for visibility, not
    // cross-thread synchronization, since there's exactly one writer.
    @Volatile private var referenceFramePosition = 0L
    @Volatile private var referenceNanos = 0L

    // Guards the single pending (not-yet-written) scheduled beat, plus the highest totalBeats
    // already consumed - together, since scheduleBeat() (called from MetronomeEngine's audio
    // scope) and the writer loop (its own dedicated thread) touch both without any other lock.
    private val scheduleLock = Any()
    private var pendingTotalBeats = -1L
    private var pendingSound: ClickSound? = null
    private var pendingTargetNanos = 0L
    private var consumedTotalBeats = -1L

    private data class PendingBeat(val totalBeats: Long, val sound: ClickSound?, val targetNanos: Long)

    fun setSpec(sound: ClickSound, spec: ClickSpec) {
        specs[sound] = spec
        renderedSamples.remove(sound)
    }

    fun getSpec(sound: ClickSound): ClickSpec = specs.getValue(sound)

    /**
     * Registers (or refines an already-pending) beat's click. Safe to call repeatedly for the
     * same [totalBeats] - each call simply overwrites the pending [targetNanos]/[sound], so a
     * predictive early call (see `MetronomeEngine.refreshPredictedSchedule`) followed by a later,
     * more-authoritative call once the real beat lands (`MetronomeEngine.onBeat`) naturally
     * refines toward the accurate value. Also safe to call after this beat's window has already
     * been consumed by the writer - a no-op then, not a late double-fire.
     */
    fun scheduleBeat(totalBeats: Long, sound: ClickSound?, targetNanos: Long) {
        synchronized(scheduleLock) {
            if (totalBeats <= consumedTotalBeats) return
            pendingTotalBeats = totalBeats
            pendingSound = sound
            pendingTargetNanos = targetNanos
        }
    }

    /** True once [AudioTrack.getTimestamp] has never returned valid data within [WARMUP_TIMEOUT_NANOS]
     * of [start] - the signal for [MetronomeEngine] to give up on this engine for the session and
     * fall back to [ClickPlayer]. Always false before that deadline, even if warm-up hasn't
     * finished yet - a slow-but-still-arriving warm-up isn't a failure. */
    fun hasFailedWarmup(): Boolean = !ready && System.nanoTime() > warmupDeadlineNanos

    /**
     * How far ahead of "now" (in nanoseconds) the writer keeps itself, once running steadily -
     * since each `AudioTrack.write()` call blocks until buffer space frees up, and the writer
     * re-fills that space immediately, its own `framesWritten` cursor stays roughly this far ahead
     * of what's actually audible at any given moment. A [scheduleBeat] call needs to arrive at
     * least this much *before* its own target - on top of whatever lead the configured
     * `audioOffsetMs` itself already asks for - or the writer may have already committed that
     * frame by the time the schedule arrives, in which case [mixPendingBeatIfDue] still places the
     * click (at the earliest available frame instead of silently dropping it), just without the
     * full precision a genuinely-early push would have gotten. See
     * `MetronomeEngine.startAudioScheduling`, the one caller that needs this. Zero before [start]
     * has run once (nothing to measure yet).
     */
    fun leadMarginNanos(): Long {
        if (bufferFrames <= 0) return 0L
        return (bufferFrames.toDouble() / sampleRateHz * 1_000_000_000.0).toLong()
    }

    /**
     * Builds and starts the continuous stream, and launches its writer coroutine on [scope] (a
     * dedicated, elevated-priority single-thread dispatcher - see `newTimingDispatcher` - distinct
     * from every other role's scope, since the writer's `AudioTrack.write()` calls block for real
     * and must never share a thread with anything else that has its own timing requirements).
     * Returns false (having cleaned up after itself) if `MODE_STREAM` construction or the initial
     * `play()` call fails, telling the caller to use the [ClickPlayer] fallback instead.
     */
    fun start(scope: CoroutineScope): Boolean {
        if (track != null) return true
        val builtTrack = try {
            buildTrack()
        } catch (e: Exception) {
            Log.w(TAG, "MODE_STREAM AudioTrack construction failed; falling back to discrete retrigger playback", e)
            return false
        }
        framesWritten = 0L
        ready = false
        warmupDeadlineNanos = System.nanoTime() + WARMUP_TIMEOUT_NANOS
        synchronized(scheduleLock) {
            pendingTotalBeats = -1L
            consumedTotalBeats = -1L
        }
        try {
            builtTrack.play()
        } catch (e: Exception) {
            Log.w(TAG, "AudioTrack.play() failed; falling back to discrete retrigger playback", e)
            builtTrack.release()
            return false
        }
        track = builtTrack
        writerJob = scope.launch {
            val timestamp = AudioTimestamp()
            val chunk = ShortArray(chunkFrames())
            while (isActive) {
                // Only the legacy one-arg getTimestamp() is public SDK API - the two-arg overload
                // that lets a caller request AudioTimestamp.TIMEBASE_MONOTONIC explicitly is
                // @SystemApi, not callable by a normal app (confirmed against the actual
                // android-35/36 SDK stub jars, which declare no such overload). Rather than assume
                // an undocumented clock base for the one that *is* available, self-calibrate: the
                // instant this call returns is treated as "now" in *our* clock ([System.nanoTime])
                // corresponding to [timestamp.framePosition] frames already played - see
                // [referenceNanos]/[referenceFramePosition] and [frameForNanoTime]. The only
                // assumption this leans on is that the gap between the HAL internally stamping
                // [AudioTimestamp.nanoTime] and this line capturing [System.nanoTime] is negligible
                // next to a chunk's duration - true for a fast, non-blocking native call.
                val gotTimestamp = try {
                    builtTrack.getTimestamp(timestamp)
                } catch (e: Exception) {
                    false
                }
                if (gotTimestamp && timestamp.framePosition > 0) {
                    referenceFramePosition = timestamp.framePosition
                    referenceNanos = System.nanoTime()
                    ready = true
                }
                chunk.fill(0)
                if (ready) mixPendingBeatIfDue(chunk, framesWritten)
                val written = try {
                    builtTrack.write(chunk, 0, chunk.size)
                } catch (e: Exception) {
                    Log.e(TAG, "AudioTrack.write() failed; stopping streaming writer", e)
                    break
                }
                if (written <= 0) {
                    Log.e(TAG, "AudioTrack.write() returned $written; stopping streaming writer")
                    break
                }
                framesWritten += written
            }
        }
        return true
    }

    private fun mixPendingBeatIfDue(chunk: ShortArray, chunkStartFrame: Long) {
        val pending = synchronized(scheduleLock) {
            if (pendingTotalBeats <= consumedTotalBeats) return
            PendingBeat(pendingTotalBeats, pendingSound, pendingTargetNanos)
        }
        val targetFrame = frameForNanoTime(pending.targetNanos)
        // A target already in the past (e.g. a MIDI-follow beat, or any non-negative offset,
        // where nothing schedules ahead of time - see MetronomeEngine.onBeat) fires at the very
        // start of this chunk, the earliest the writer can still place it - not silently dropped.
        val offsetInChunk = if (targetFrame <= chunkStartFrame) 0 else (targetFrame - chunkStartFrame)
        if (offsetInChunk >= chunk.size) return // not due yet
        pending.sound?.let { mixInto(chunk, offsetInChunk.toInt(), it) }
        synchronized(scheduleLock) { consumedTotalBeats = pending.totalBeats }
    }

    private fun mixInto(chunk: ShortArray, startFrame: Int, sound: ClickSound) {
        val samples = renderedSamples.getOrPut(sound) { ClickSynth.render(specs.getValue(sound), sampleRateHz) }
        var index = startFrame
        for (sample in samples) {
            if (index >= chunk.size) break
            val mixed = chunk[index] + sample
            chunk[index] = mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            index++
        }
    }

    /** `frameForNanoTime(t) = referenceFramePosition + (t - referenceNanos) * sampleRate / 1e9` -
     * the one piece of arithmetic this whole class exists to get right, mapping a target
     * [System.nanoTime]-based instant to the exact sample frame that will be playing at that
     * instant. See [start]'s writer loop for how [referenceFramePosition]/[referenceNanos] get
     * calibrated. */
    private fun frameForNanoTime(nanos: Long): Long {
        val deltaNanos = nanos - referenceNanos
        val deltaFrames = deltaNanos.toDouble() * sampleRateHz / 1_000_000_000.0
        return referenceFramePosition + deltaFrames.toLong()
    }

    private fun chunkFrames(): Int = (sampleRateHz * CHUNK_DURATION_MS / 1000).toInt().coerceAtLeast(1)

    fun stop() {
        writerJob?.cancel()
        writerJob = null
        track?.let {
            try {
                it.stop()
            } catch (_: Exception) {
                // Already stopped/dead - release() below still needs to run.
            }
            it.release()
        }
        track = null
        ready = false
        bufferFrames = 0
    }

    private fun buildTrack(): AudioTrack {
        val bufferBytes = AudioTrack.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(chunkFrames() * 2)
        bufferFrames = bufferBytes / 2 // 16-bit mono => 2 bytes/frame - see leadMarginNanos()
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
    }

    private companion object {
        const val TAG = "StreamingClickEngine"

        /** Size of each chunk the writer mixes and writes at a time - small enough to keep sample-
         * placement precision within a fraction of this many milliseconds' worth of frames. */
        const val CHUNK_DURATION_MS = 5L

        /** How long [start] gives [AudioTrack.getTimestamp] to return valid data before
         * [hasFailedWarmup] reports true - generous (real hardware warms up in a handful of
         * chunks), so a slow-but-genuine warm-up is never mistaken for a real failure. */
        const val WARMUP_TIMEOUT_NANOS = 1_000_000_000L
    }
}
