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
         * weightier downbeat versus a light tick, the way a kick reads against a hi-hat. */
        val DEFAULT_BAR = ClickSpec(ClickWaveform.SQUARE, frequencyHz = 220f, durationMs = 90, gain = 0.8f)
        val DEFAULT_ACCENT = ClickSpec(ClickWaveform.SINE, frequencyHz = 440f, durationMs = 60, gain = 0.8f)
        val DEFAULT_REGULAR = ClickSpec(ClickWaveform.SINE, frequencyHz = 880f, durationMs = 35, gain = 0.8f)

        fun defaultFor(sound: ClickSound): ClickSpec = when (sound) {
            ClickSound.BAR -> DEFAULT_BAR
            ClickSound.ACCENT -> DEFAULT_ACCENT
            ClickSound.REGULAR -> DEFAULT_REGULAR
        }
    }
}
