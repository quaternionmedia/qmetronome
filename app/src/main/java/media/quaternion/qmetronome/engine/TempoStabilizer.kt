package media.quaternion.qmetronome.engine

/**
 * Exponential-moving-average smoother for a BPM value that drives an *output* timing schedule
 * (see [media.quaternion.qmetronome.midi.MidiClockSender]). [media.quaternion.qmetronome.midi.MidiClockSource]
 * already smooths its own tempo *measurement* (a rolling window of received tick intervals), but
 * that measurement can still legitimately shift by a fraction of a BPM beat to beat - a real,
 * faithful reading of a real, slightly-imperfect external clock. Re-deriving a precise 24-way
 * tick subdivision from that value every single beat means the outgoing clock re-quantizes every
 * one of those tiny fluctuations into an actual timing change, which is a second, avoidable
 * source of instability on top of whatever the followed clock already has.
 *
 * [smoothingFactor] is the fraction of the gap to the previous smoothed value closed on each
 * [update] - `1.0` is an unsmoothed passthrough, smaller values damp more but take longer to
 * settle on a genuine, sustained tempo change.
 */
class TempoStabilizer(private val smoothingFactor: Float = DEFAULT_SMOOTHING_FACTOR) {

    private var smoothed: Float? = null

    /** The most recently smoothed value, or `null` before the first [update]. */
    val current: Float? get() = smoothed

    /**
     * Feeds one new raw measurement and returns the smoothed value. Call this only when the raw
     * value actually changed (e.g. once per beat, not once per MIDI clock tick) - repeatedly
     * feeding an unchanged input converges [current] all the way to it, defeating the smoothing
     * on whatever the next real change turns out to be.
     */
    fun update(rawBpm: Float): Float {
        val previous = smoothed
        val next = if (previous == null) rawBpm else previous + smoothingFactor * (rawBpm - previous)
        smoothed = next
        return next
    }

    /** Drops any smoothing history - the next [update] starts fresh from its raw input instead of
     * easing in from a stale prior value. Call this whenever the source of truth changes out from
     * under this instance (e.g. switching which clock is being followed/relayed). */
    fun reset() {
        smoothed = null
    }

    companion object {
        const val DEFAULT_SMOOTHING_FACTOR = 0.25f
    }
}
