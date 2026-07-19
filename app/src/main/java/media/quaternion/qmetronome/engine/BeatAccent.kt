package media.quaternion.qmetronome.engine

/**
 * How a single non-downbeat position in [TimeSignature.accentPattern] is marked - independent of
 * beat 0, which is always [ClickSound.BAR] regardless of this (see
 * [MetronomeEngine.beatTypeFor]). Three markable tiers plus [NONE] rather than a single boolean,
 * so a bar can distinguish an ordinary accent from a stronger one or an entirely custom cue - the
 * same distinction [ClickSound] carries on the audio/MIDI side ([ClickSound.ACCENT],
 * [ClickSound.STRONG_ACCENT], [ClickSound.CUSTOM]).
 */
enum class BeatAccent {
    NONE,
    ACCENT,
    STRONG_ACCENT,
    CUSTOM,
}
