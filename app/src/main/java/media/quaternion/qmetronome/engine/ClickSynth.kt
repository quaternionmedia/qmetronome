package media.quaternion.qmetronome.engine

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Renders a [ClickSpec] into a one-shot mono PCM16 buffer - the only place this app generates
 * audio math, so [ClickPlayer] can stay a thin `AudioTrack` wrapper. Every render bakes in a fixed,
 * non-tunable envelope (a few milliseconds of linear attack, exponential decay to the buffer's
 * end) so a click never starts or ends on a discontinuity - that's what would otherwise produce an
 * audible pop, independent of whatever [ClickSpec.waveform]/[ClickSpec.frequencyHz] the user picks.
 */
object ClickSynth {

    fun render(spec: ClickSpec, sampleRateHz: Int): ShortArray {
        val sampleCount = ((spec.durationMs / 1000f) * sampleRateHz).roundToInt().coerceAtLeast(1)
        val attackSamples = ((ATTACK_MS / 1000f) * sampleRateHz).roundToInt().coerceIn(1, sampleCount)
        val frequencyHz = spec.frequencyHz.coerceAtLeast(1f)
        val samples = ShortArray(sampleCount)

        // Only used by NOISE, but cheap enough to always set up.
        val noiseAlpha = lowPassAlpha(frequencyHz, sampleRateHz)
        var noiseState = 0f
        val random = Random(spec.hashCode())

        for (i in 0 until sampleCount) {
            val raw = when (spec.waveform) {
                ClickWaveform.SINE -> sin(2.0 * PI * frequencyHz * i / sampleRateHz).toFloat()
                ClickWaveform.SQUARE -> if (sin(2.0 * PI * frequencyHz * i / sampleRateHz) >= 0.0) 1f else -1f
                ClickWaveform.TRIANGLE ->
                    (2.0 / PI * asin(sin(2.0 * PI * frequencyHz * i / sampleRateHz))).toFloat()
                ClickWaveform.NOISE -> {
                    val white = random.nextFloat() * 2f - 1f
                    noiseState += noiseAlpha * (white - noiseState)
                    noiseState
                }
            }
            val envelope = envelopeAt(i, sampleCount, attackSamples)
            val amplitude = (raw * envelope * spec.gain).coerceIn(-1f, 1f)
            samples[i] = (amplitude * Short.MAX_VALUE).roundToInt().toShort()
        }
        return samples
    }

    private fun envelopeAt(index: Int, sampleCount: Int, attackSamples: Int): Float {
        val attack = if (index < attackSamples) index / attackSamples.toFloat() else 1f
        val decaySamples = (sampleCount - attackSamples).coerceAtLeast(1)
        val decayProgress = (index - attackSamples).coerceAtLeast(0) / decaySamples.toFloat()
        val decay = exp(-DECAY_RATE * decayProgress)
        return attack * decay
    }

    /** One-pole low-pass filter coefficient for a given cutoff, standard RC/dt derivation. */
    private fun lowPassAlpha(cutoffHz: Float, sampleRateHz: Int): Float {
        val dt = 1f / sampleRateHz
        val rc = 1f / (2f * PI.toFloat() * cutoffHz)
        return dt / (rc + dt)
    }

    private const val ATTACK_MS = 3f
    private const val DECAY_RATE = 5.0f
}
