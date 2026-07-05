package media.quaternion.qmetronome.engine

/**
 * How aggressively a clock-related subsystem corrects for its own natural timing imperfection -
 * currently drives [media.quaternion.qmetronome.midi.MidiClockSender]'s outgoing tick schedule
 * (see its own doc for specifics), named generically so another timing-sensitive path can adopt
 * the same choice later without a new enum.
 *
 * [ORGANIC] is deliberately *not* simulated with programmed randomness anywhere - it means "skip
 * the correction," relying on whatever variance genuinely falls out of real coroutine scheduling,
 * measurement noise, etc. Faking that feel with a `Random` would be a fundamentally different
 * (and less honest) thing than what this mode is for.
 */
enum class ClockTimingMode {
    /** Actively corrects for drift/measurement noise - the truest, most locked-in beat this app
     * can produce. */
    MECHANICAL,

    /** Skips that correction - natural async timing variance flows through unfiltered instead of
     * being smoothed/resynced away. */
    ORGANIC,
}
