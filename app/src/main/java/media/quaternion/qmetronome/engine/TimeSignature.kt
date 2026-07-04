package media.quaternion.qmetronome.engine

/**
 * One entry in [MetronomeEngine]'s bar queue - "beats/bar" plus, now, its own tempo, so a queued
 * sequence of bars can each play at a different speed, not just a different meter. [beatCount]
 * (the numerator) and [unitNoteValue] (the denominator, e.g. the "4" in 4/4) are edited
 * independently, kept alongside each other so a real "N/D" time signature can be typed and read
 * back. Changing [unitNoteValue] rescales [bpm] to preserve the underlying tempo (see
 * [MetronomeEngine.rescaledBpmForUnitNoteValueChange]) - e.g. switching a bar from 6/4 to 3/2
 * redistributes the same bar duration into 3 clicks instead of 6, rather than silently doubling
 * the felt tempo - with [accentPattern] reserved for custom per-beat accents later.
 *
 * [visualizerId] is null until a visualizer is explicitly picked while this bar is active -
 * `null` means "no per-bar override, follow whatever's currently selected," so a queue nobody has
 * ever touched this way behaves exactly as if visualizer choice were still global. Once set, that
 * bar always recalls its own visualizer on [MetronomeEngine.goToQueueBar], the same way it
 * recalls its own [bpm] and [beatCount].
 */
data class TimeSignature(
    val beatCount: Int,
    val unitNoteValue: Int = 4,
    val bpm: Float = 120f,
    val accentPattern: List<Boolean>? = null,
    val visualizerId: String? = null,
) {
    /** Which beats are accented. Defaults to "beat 0 only" when no custom pattern is set. */
    fun isAccented(beatIndex: Int): Boolean = accentPattern?.getOrNull(beatIndex) ?: (beatIndex == 0)

    companion object {
        val DEFAULT = TimeSignature(beatCount = 4)
    }
}
