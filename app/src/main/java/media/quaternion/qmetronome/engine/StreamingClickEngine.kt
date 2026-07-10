package media.quaternion.qmetronome.engine

import android.content.Context
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
 * built on the first [start] call and then kept running (mixing silence between beats, or
 * between metronome sessions entirely) rather than being torn down and rebuilt on every
 * `MetronomeEngine.stop()`/`start()` toggle - see [resetSchedule] for the lightweight per-session
 * reset `MetronomeEngine` uses instead of a real [stop] between sessions, and that function's own
 * kdoc for why: rebuilding on every play press meant re-paying [AudioTrack.getTimestamp]'s warm-up
 * wait (gating [ready], and with it every mixed beat) on every single press, not once per app
 * session - the dominant cause of a reported lag on a session's first beat. A dedicated writer
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

    // Set once, best-effort, from configureFromDevice() - see that function's own kdoc for why
    // this exists and what happens when it's never called (falls back to getMinBufferSize()).
    @Volatile private var lowLatencyBufferFrames: Int? = null

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

    // Not reset by resetSchedule() or stop() - see LeadMarginCalibrator's own kdoc for why this
    // models the physical device, not a single play/stop session.
    private val leadMarginCalibrator = LeadMarginCalibrator()

    private data class PendingBeat(val totalBeats: Long, val sound: ClickSound?, val targetNanos: Long)

    /** For on-device benchmarking only (see `FirstBeatTimingBenchmarkTest` in `androidTest`) - lets
     * a test observe, for every beat actually mixed into the stream, its intended target nanoTime
     * against the real nanoTime (via the same [frameForNanoTime]/[nanoTimeForFrame] calibration,
     * running against real `AudioTrack`/HAL behavior - not Robolectric's shadow, which doesn't
     * model this) it actually landed at. The delta between the two *is* this engine's real
     * placement error, without needing a microphone/acoustic loopback to observe it - the mixing
     * decision itself already carries everything a test needs. */
    @Volatile private var mixListenerForTesting: ((totalBeats: Long, targetNanos: Long, actualNanos: Long) -> Unit)? = null

    fun setMixListenerForTesting(listener: ((totalBeats: Long, targetNanos: Long, actualNanos: Long) -> Unit)?) {
        mixListenerForTesting = listener
    }

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

    /**
     * Clears whatever's pending/consumed so far, without touching the `AudioTrack`/writer -
     * `MetronomeEngine.stop()`'s counterpart to a full [stop] now that this engine stays warm
     * across play/stop cycles (see that function's own kdoc for why). Cancels anything
     * scheduled-but-not-yet-mixed (no stale click after the user presses stop) and resets the
     * high-water mark so the *next* session's beat 0 isn't silently born already "consumed" by a
     * previous session's totalBeats count.
     */
    fun resetSchedule() {
        synchronized(scheduleLock) {
            pendingTotalBeats = -1L
            consumedTotalBeats = -1L
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
     * [leadMarginNanos] corrected by [leadMarginCalibrator]'s running measurement of this device's
     * *actual* placement error - see that class's own kdoc for why the raw buffer-derived estimate
     * alone isn't reliable, and `docs/timing-accuracy-benchmark.md` for the measured before/after.
     * Callers should use this instead of [leadMarginNanos] directly for any scheduling decision;
     * [leadMarginNanos] itself stays as the uncorrected raw estimate, still meaningful on its own
     * (e.g. for tests asserting against the buffer's raw configured size).
     */
    fun calibratedLeadMarginNanos(): Long = leadMarginNanos() + leadMarginCalibrator.correctionNanos()

    /**
     * Builds and starts the continuous stream, and launches its writer coroutine on [scope] (a
     * dedicated, elevated-priority single-thread dispatcher - see `newTimingDispatcher` - distinct
     * from every other role's scope, since the writer's `AudioTrack.write()` calls block for real
     * and must never share a thread with anything else that has its own timing requirements).
     * Returns false (having cleaned up after itself) if `MODE_STREAM` construction or the initial
     * `play()` call fails, telling the caller to use the [ClickPlayer] fallback instead.
     *
     * Safe to call repeatedly - `MetronomeEngine` now calls this on every `start()` (not just the
     * first one) so it stays the single source of truth for whether the streaming path is usable
     * this session, but a real rebuild only happens the first time, or after a genuine failure.
     * The liveness check below is what makes that safe: `track != null` alone isn't proof the
     * writer is still running - if it died mid-session (a real `write()` failure below `break`s
     * the loop without nulling `track`), trusting the stale reference would silently leave the
     * engine reporting "already running" forever while nothing is ever mixed in again. Noticing
     * that and falling through to a real rebuild is what keeps that scenario recoverable instead
     * of permanently silent.
     */
    fun start(scope: CoroutineScope): Boolean {
        if (track != null) {
            if (writerJob?.isActive == true) return true
            stop() // writer died after warm-up; stale track, do a genuine rebuild below
        }
        val builtTrack = try {
            buildTrack()
        } catch (e: Exception) {
            Log.w(TAG, "MODE_STREAM AudioTrack construction failed; falling back to discrete retrigger playback", e)
            return false
        }
        framesWritten = 0L
        ready = false
        warmupDeadlineNanos = System.nanoTime() + WARMUP_TIMEOUT_NANOS
        resetSchedule()
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
        val actualNanos = nanoTimeForFrame(chunkStartFrame + offsetInChunk)
        leadMarginCalibrator.recordPlacementError(actualNanos - pending.targetNanos)
        mixListenerForTesting?.invoke(pending.totalBeats, pending.targetNanos, actualNanos)
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

    /** The inverse of [frameForNanoTime] - for [mixListenerForTesting] only, to report back which
     * real nanoTime a given frame (where a beat actually got mixed) corresponds to. */
    private fun nanoTimeForFrame(frame: Long): Long {
        val deltaFrames = frame - referenceFramePosition
        val deltaNanos = deltaFrames.toDouble() * 1_000_000_000.0 / sampleRateHz
        return referenceNanos + deltaNanos.toLong()
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

    /**
     * Queries the device's real low-latency output burst size ([AudioManager]'s
     * `PROPERTY_OUTPUT_FRAMES_PER_BUFFER`) so [buildTrack] can size its buffer close to what
     * Android's *fast* mixer path actually expects, instead of [AudioTrack.getMinBufferSize] -
     * which has no `PERFORMANCE_MODE_LOW_LATENCY` parameter and reports a minimum sized for the
     * ordinary (non-fast) mixer path, a real, measured problem for this engine specifically: on
     * the device this was diagnosed against, `getMinBufferSize()` returned a buffer worth ~120ms
     * (thousands of frames) - two orders of magnitude larger than a real low-latency burst
     * (typically a few hundred frames, a handful of ms) - and a temporary diagnostic in
     * [mixPendingBeatIfDue] confirmed the writer's own `AudioTrack.write()` calls were, in
     * practice, blocking for ~40-45ms per call rather than this class's intended
     * [CHUNK_DURATION_MS] (5ms), consistent with AudioFlinger routing a track requesting *that*
     * large a buffer through its ordinary mixer (a documented ~20-40ms period) rather than the
     * fast mixer (a documented 2-3ms period, SCHED_FIFO) - see `docs/timing-accuracy-
     * benchmark.md`'s research notes for the sources. A smaller, burst-sized buffer is exactly
     * what Android's own low-latency guidance (and the Oboe library, which automates this same
     * choice) recommends requesting instead.
     *
     * Best-effort and safe to skip: if the property is missing or unparseable (older/unusual
     * devices), [buildTrack] falls back to its original [AudioTrack.getMinBufferSize]-based sizing
     * unchanged - this never blocks the streaming path from working, only tries to make it faster.
     * Call once, before the first [start] - typically from `MetronomeEngine.attach`.
     */
    fun configureFromDevice(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val framesPerBurst = audioManager
            .getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: return
        // Twice the burst size (double buffering) - the same sizing Oboe itself automates for a
        // low-latency stream, per its own buffer-terminology guidance.
        lowLatencyBufferFrames = framesPerBurst * 2
    }

    private fun buildTrack(): AudioTrack {
        val bytesPerFrame = 2 // 16-bit mono => 2 bytes/frame - see leadMarginNanos()
        val bufferBytes = (lowLatencyBufferFrames?.let { it * bytesPerFrame } ?: AudioTrack.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )).coerceAtLeast(chunkFrames() * 2)
        bufferFrames = bufferBytes / bytesPerFrame
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
