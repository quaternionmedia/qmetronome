package media.quaternion.qmetronome.engine

/**
 * Self-calibrating correction for [StreamingClickEngine.leadMarginNanos]'s buffer-derived
 * estimate. Both the Java (`AudioTrack.getMinBufferSize`) and native (AAudio's
 * `PROPERTY_OUTPUT_FRAMES_PER_BUFFER`) APIs for querying a device's audio buffer are documented as
 * estimates/hints, not guarantees of the real output-pipeline lead - and neither the Java nor
 * native Android audio API exposes a way to query that real value directly. See
 * `docs/timing-accuracy-benchmark.md`'s research notes for the sources. The only way to close that
 * gap from user-space is to measure it: every beat [StreamingClickEngine] actually mixes already
 * reveals, for free, whether it needed to clamp to "the earliest available frame" - a beat that had
 * sufficient lead lands within microseconds of its target by construction (see
 * [StreamingClickEngine.frameForNanoTime]'s own precision), so a positive, non-negligible
 * `actualNanos - targetNanos` *is* the direct signal that more lead was needed, no separate
 * measurement mechanism required.
 *
 * An exponential moving average, not the raw latest sample, both smooths ordinary measurement
 * noise (one slow chunk, one GC pause) and - per the general clock-discipline literature's own
 * "multi-step compensation" precedent (many small steps rather than one large jump) - converges
 * without a single noisy beat swinging the correction. State lives for the calibrator's entire
 * lifetime, not reset per session: it's modeling a property of the physical device, not this
 * particular play/stop cycle, so a later session's beat 0 benefits from what earlier sessions'
 * steady-state beats already learned.
 */
class LeadMarginCalibrator {
    @Volatile private var correctionNanos = 0.0

    /**
     * Call once for every beat actually mixed, with `actualNanos - targetNanos` (see
     * [StreamingClickEngine.mixPendingBeatIfDue]). Negative or ~zero (landed on or before target)
     * pulls the correction back toward zero; positive (landed late - clamped to the earliest
     * available frame) pulls it up toward the observed lateness. Never allowed to go negative
     * itself - asking for more lead than the raw buffer estimate provides is harmless (see
     * [correctionNanos]'s own kdoc), but a *negative* correction would mean asking for less lead
     * than the raw estimate already gives, eroding precision for no benefit.
     */
    fun recordPlacementError(errorNanos: Long) {
        val observed = errorNanos.toDouble().coerceAtLeast(0.0)
        correctionNanos = (correctionNanos + ALPHA * (observed - correctionNanos)).coerceAtLeast(0.0)
    }

    /**
     * The current correction, in nanoseconds - added on top of [StreamingClickEngine.leadMarginNanos]'s
     * raw buffer estimate by callers (see `MetronomeEngine.calibratedLeadMarginNanos`'s call
     * sites), themselves already bounded by [MetronomeEngine]'s own existing lead-margin caps, so a
     * runaway correction still can't push scheduling further ahead than those caps already allow.
     */
    fun correctionNanos(): Long = correctionNanos.toLong()

    private companion object {
        /** How much weight the newest sample gets - small enough that no single beat's
         * measurement can swing the correction, large enough to converge within a session's first
         * several beats. See `docs/timing-accuracy-benchmark.md` for the measured convergence. */
        const val ALPHA = 0.15
    }
}
