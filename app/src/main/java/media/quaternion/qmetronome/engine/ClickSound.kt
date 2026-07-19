package media.quaternion.qmetronome.engine

/**
 * Which click to play - and, independently, which MIDI action to fire (see
 * [media.quaternion.qmetronome.midi.MidiActionSender]) - for a given beat. A small, deliberately
 * open-ended registry (see [ClickSpec]/[ClickSynth]) so a new sound is one enum entry and one
 * [ClickSpec], not a rewrite of playback plumbing. [BAR] is always beat 0; [ACCENT]/
 * [STRONG_ACCENT]/[CUSTOM] mark any other beat via [TimeSignature.accentPattern]'s [BeatAccent]
 * tiers (authored via the accent chips in [media.quaternion.qmetronome.ui.TimeSignatureEntryDialog]).
 * See [MetronomeEngine.beatTypeFor] for the single place that mapping happens. A probability-muted
 * beat's own sound or a future subdivision click are natural next entries.
 */
enum class ClickSound {
    REGULAR,
    ACCENT,
    STRONG_ACCENT,
    BAR,
    CUSTOM,
}
