package media.quaternion.qmetronome.engine

/**
 * A tunable definition of one [ClickSound]'s audible click - everything [ClickSynth] needs to
 * generate it, and everything [MetronomeSettings] persists per [ClickSound]. No sample/asset
 * reference on purpose - see [ClickWaveform].
 */
data class ClickSpec(
    val waveform: ClickWaveform,
    /** Pitch in Hz for tonal waveforms, or the noise low-pass cutoff for [ClickWaveform.NOISE]. */
    val frequencyHz: Float,
    val durationMs: Int,
    /** Peak linear amplitude, 0..1. */
    val gain: Float,
) {
    companion object {
        /** Defaults chosen to echo the feel of the previous fixed `ToneGenerator` clicks: the bar
         * click lower-pitched and longer, the regular beat higher-pitched and shorter - a bigger,
         * weightier downbeat versus a light tick, the way a kick reads against a hi-hat.
         * [DEFAULT_STRONG_ACCENT] sits between [DEFAULT_BAR] and [DEFAULT_ACCENT] in pitch but
         * louder and harder-edged (square, full gain) - audibly stronger than a plain accent
         * without being mistaken for the downbeat. [DEFAULT_CUSTOM] uses [ClickWaveform.NOISE] -
         * the one timbre none of the other three defaults use - so a freshly-authored custom beat
         * is audibly distinct out of the box, before anyone's tuned it. */
        val DEFAULT_BAR = ClickSpec(ClickWaveform.SQUARE, frequencyHz = 220f, durationMs = 90, gain = 0.8f)
        val DEFAULT_ACCENT = ClickSpec(ClickWaveform.SINE, frequencyHz = 440f, durationMs = 60, gain = 0.8f)
        val DEFAULT_STRONG_ACCENT = ClickSpec(ClickWaveform.SQUARE, frequencyHz = 330f, durationMs = 70, gain = 1f)
        val DEFAULT_REGULAR = ClickSpec(ClickWaveform.SINE, frequencyHz = 880f, durationMs = 35, gain = 0.8f)
        val DEFAULT_CUSTOM = ClickSpec(ClickWaveform.NOISE, frequencyHz = 1200f, durationMs = 40, gain = 0.8f)

        fun defaultFor(sound: ClickSound): ClickSpec = when (sound) {
            ClickSound.BAR -> DEFAULT_BAR
            ClickSound.ACCENT -> DEFAULT_ACCENT
            ClickSound.STRONG_ACCENT -> DEFAULT_STRONG_ACCENT
            ClickSound.REGULAR -> DEFAULT_REGULAR
            ClickSound.CUSTOM -> DEFAULT_CUSTOM
        }
    }
}
