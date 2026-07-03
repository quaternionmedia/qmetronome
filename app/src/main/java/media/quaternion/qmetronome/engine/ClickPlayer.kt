package media.quaternion.qmetronome.engine

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Plays the audible click so the engine works as a real metronome, not just a light show. Uses
 * [ToneGenerator] so no sound assets are needed. [ClickSound] -> tone/duration is a small table
 * ([soundSpecs]) rather than an inline branch, so a new sound is a new [ClickSound] entry and a
 * new row here, not a rewrite of this class.
 */
class ClickPlayer {

    private var toneGenerator: ToneGenerator? = null

    fun playClick(sound: ClickSound) {
        val generator = toneGenerator ?: ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME)
            .also { toneGenerator = it }
        val spec = soundSpecs.getValue(sound)
        generator.startTone(spec.tone, spec.durationMs)
    }

    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }

    private data class ToneSpec(val tone: Int, val durationMs: Int)

    private companion object {
        // BAR is deliberately longer *and* lower-pitched than REGULAR - a bigger, weightier
        // downbeat versus a light tick, the way a kick reads against a hi-hat. TONE_PROP_BEEP vs
        // TONE_PROP_BEEP2 turned out to be nearly indistinguishable on-device (same OEM tone bank
        // on many devices); DTMF tones have well-defined, guaranteed-distinct dual frequencies -
        // TONE_DTMF_1 is 697+1209 Hz, TONE_DTMF_9 is 852+1477 Hz - so the pitch difference is
        // audible everywhere, not just on hardware that happens to differentiate the PROP tones.
        val soundSpecs = mapOf(
            ClickSound.REGULAR to ToneSpec(ToneGenerator.TONE_DTMF_9, 35),
            ClickSound.BAR to ToneSpec(ToneGenerator.TONE_DTMF_1, 90),
        )
    }
}
