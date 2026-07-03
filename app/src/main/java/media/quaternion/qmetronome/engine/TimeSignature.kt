package media.quaternion.qmetronome.engine

/**
 * One entry in [MetronomeEngine]'s bar queue - "beats/bar" plus, now, its own tempo, so a queued
 * sequence of bars can each play at a different speed, not just a different meter. [beatCount]
 * (the numerator) and [unitNoteValue] (the denominator, e.g. the "4" in 4/4) are edited
 * independently - [unitNoteValue] is presentational only today (nothing in the beat loop
 * subdivides by it yet), kept alongside [beatCount] so a real "N/D" time signature can be typed
 * and read back, with [accentPattern] reserved for custom per-beat accents later.
 */
data class TimeSignature(
    val beatCount: Int,
    val unitNoteValue: Int = 4,
    val bpm: Float = 120f,
    val accentPattern: List<Boolean>? = null,
) {
    /** Which beats are accented. Defaults to "beat 0 only" when no custom pattern is set. */
    fun isAccented(beatIndex: Int): Boolean = accentPattern?.getOrNull(beatIndex) ?: (beatIndex == 0)

    companion object {
        val DEFAULT = TimeSignature(beatCount = 4)
    }
}
