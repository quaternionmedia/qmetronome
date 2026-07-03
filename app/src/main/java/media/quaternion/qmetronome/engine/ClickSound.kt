package media.quaternion.qmetronome.engine

/**
 * Which click to play for a given beat - a small, deliberately open-ended registry (see
 * [ClickPlayer]'s tone table) so a new sound is one enum entry and one row, not a rewrite of
 * playback plumbing. Starts with the one distinction the engine already made (the bar's first
 * beat vs. every other beat); a probability-muted beat's own sound or a future subdivision click
 * are natural next entries.
 */
enum class ClickSound {
    REGULAR,
    BAR,
}
