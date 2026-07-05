package media.quaternion.qmetronome.engine

/**
 * Which click to play for a given beat - a small, deliberately open-ended registry (see
 * [ClickSpec]/[ClickSynth]) so a new sound is one enum entry and one [ClickSpec], not a rewrite of
 * playback plumbing. [ACCENT] is for any beat marked accented by [TimeSignature.accentPattern]
 * other than the bar's own downbeat ([BAR]) - wired end-to-end in the audio engine, but currently
 * unreachable in the UI since nothing yet authors a custom accent pattern (every bar still reads
 * back as "beat 0 only" accented - see [MetronomeSettings]'s `queue` doc comment). A probability-
 * muted beat's own sound or a future subdivision click are natural next entries.
 */
enum class ClickSound {
    REGULAR,
    ACCENT,
    BAR,
}
