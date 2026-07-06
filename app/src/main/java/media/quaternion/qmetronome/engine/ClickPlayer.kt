package media.quaternion.qmetronome.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

/**
 * Plays the audible click so the engine works as a real metronome, not just a light show. Each
 * [ClickSound] gets its own `AudioTrack` built once (in `MODE_STATIC`) from [ClickSynth]'s
 * rendering of that sound's [ClickSpec] - a generated waveform, not a sample/asset - and replayed
 * per beat via the standard static-track retrigger idiom (`stop()` + `reloadStaticData()` +
 * `play()`), so a fast tempo retriggering a click before its previous decay finished still starts
 * cleanly from the top rather than glitching or silently no-opping.
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
    private val tracks = mutableMapOf<ClickSound, AudioTrack>()

    /** Changes a sound's spec - takes effect on its next trigger; the old track (if already
     * built) is released rather than mutated in place, since `AudioTrack`'s buffer is fixed at
     * construction time. */
    fun setSpec(sound: ClickSound, spec: ClickSpec) {
        specs[sound] = spec
        tracks.remove(sound)?.release()
    }

    fun getSpec(sound: ClickSound): ClickSpec = specs.getValue(sound)

    fun playClick(sound: ClickSound) {
        val track = tracks.getOrPut(sound) { buildTrack(specs.getValue(sound)) }
        track.stop()
        track.reloadStaticData()
        track.play()
    }

    fun release() {
        tracks.values.forEach { it.release() }
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
}
