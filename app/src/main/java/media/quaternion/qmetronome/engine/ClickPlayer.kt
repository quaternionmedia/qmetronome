package media.quaternion.qmetronome.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

/**
 * Discrete-retrigger click playback - each [ClickSound] gets [TRACKS_PER_SOUND] `AudioTrack`s
 * built once (in `MODE_STATIC`) from [ClickSynth]'s rendering of that sound's [ClickSpec] - a
 * generated waveform, not a sample/asset - and replayed per beat via the standard static-track
 * retrigger idiom (`stop()` + `reloadStaticData()` + `play()`), round-robining between the pooled
 * instances rather than always retriggering the same one. That's the difference between "a fast
 * tempo retriggering a click before its previous decay finished starts cleanly from the top" (true
 * either way) and "starts cleanly from the top *without first having to interrupt audio that's
 * still actively playing*" - retriggering an already-idle track is a strictly simpler, more
 * consistent operation than stopping one mid-decay, so round-robining is cheap insurance against
 * that difference ever becoming audible as inconsistent click timing (mirrors
 * `GlyphCanvas.BufferPool`'s round-robin precedent for the same "never make a caller interrupt its
 * own predecessor" reasoning).
 *
 * **No longer the primary playback path** - `StreamingClickEngine`'s continuously-running,
 * sample-clocked `MODE_STREAM` stream is, since a discrete per-beat retrigger can only ever be
 * timed by a coroutine waking up at approximately the right wall-clock moment, not by the audio
 * hardware's own sample clock. This class is kept as [MetronomeEngine]'s automatic fallback
 * (`MetronomeEngine.usingStreamingClickEngine`) for whatever device/OEM audio stack doesn't
 * cooperate with `MODE_STREAM` construction or `AudioTrack.getTimestamp()` warm-up - see
 * `StreamingClickEngine.hasFailedWarmup`.
 */
class ClickPlayer {

    // Lazy rather than an eager property: this class is constructed as part of MetronomeEngine's
    // singleton init, well before the first real click is needed, and there's no reason to touch
    // the audio subsystem that early.
    private val sampleRateHz by lazy {
        AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC).takeIf { it > 0 } ?: 44_100
    }

    private val specs = mutableMapOf<ClickSound, ClickSpec>().apply {
        ClickSound.entries.forEach { put(it, ClickSpec.defaultFor(it)) }
    }
    private val tracks = mutableMapOf<ClickSound, Array<AudioTrack>>()
    private val nextTrackIndex = mutableMapOf<ClickSound, Int>()

    /** Changes a sound's spec - takes effect on its next trigger; the old tracks (if already
     * built) are released rather than mutated in place, since `AudioTrack`'s buffer is fixed at
     * construction time. */
    fun setSpec(sound: ClickSound, spec: ClickSpec) {
        specs[sound] = spec
        tracks.remove(sound)?.forEach { it.release() }
    }

    fun getSpec(sound: ClickSound): ClickSpec = specs.getValue(sound)

    fun playClick(sound: ClickSound) {
        val pool = tracks.getOrPut(sound) { Array(TRACKS_PER_SOUND) { buildTrack(specs.getValue(sound)) } }
        val index = nextTrackIndex.getOrDefault(sound, 0)
        nextTrackIndex[sound] = (index + 1) % pool.size
        val track = pool[index]
        track.stop()
        track.reloadStaticData()
        track.play()
    }

    fun release() {
        tracks.values.forEach { pool -> pool.forEach { it.release() } }
        tracks.clear()
    }

    private fun buildTrack(spec: ClickSpec): AudioTrack {
        val samples = ClickSynth.render(spec, sampleRateHz)
        val bytes = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val sample = samples[i].toInt()
            bytes[i * 2] = (sample and 0xFF).toByte()
            bytes[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
        }
        val track = AudioTrack.Builder()
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
            .setBufferSizeInBytes(bytes.size)
            .setTransferMode(AudioTrack.MODE_STATIC)
            // The standard platform API for exactly this use case (a short, latency-sensitive
            // one-shot trigger) - without it, the click rides whatever output latency the
            // platform's default audio path happens to have, undermining how precisely timed
            // everything upstream (ClockSource, the audio offset lookahead) already is.
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        track.write(bytes, 0, bytes.size)
        return track
    }

    private companion object {
        /** How many alternating `AudioTrack` instances each [ClickSound] gets - see the class kdoc. */
        const val TRACKS_PER_SOUND = 2
    }
}
