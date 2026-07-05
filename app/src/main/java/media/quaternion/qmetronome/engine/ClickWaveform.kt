package media.quaternion.qmetronome.engine

/** A generative waveform shape [ClickSynth] can render for a [ClickSound] - no audio samples,
 * everything here is computed from [ClickSpec.frequencyHz] at render time. */
enum class ClickWaveform {
    SINE,
    SQUARE,
    TRIANGLE,

    /** A short burst of filtered noise - a percussive "click" distinct from the tonal shapes,
     * still driven by [ClickSpec.frequencyHz] (as the noise's low-pass cutoff) so it stays
     * consistent with the rest of the tuning UI rather than needing a separate control. */
    NOISE,
}
