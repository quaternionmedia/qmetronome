package media.quaternion.qmetronome.engine

/**
 * A small, deliberately minimal seam for future meter work. Today only [beatCount] has any UI
 * or persisted state - [unitNoteValue] and [accentPattern] exist so a future milestone can add
 * compound meters or custom per-beat accents without another rework of [MetronomeEngine]'s beat
 * loop, not because either is exposed anywhere yet.
 */
data class TimeSignature(
    val beatCount: Int,
    val unitNoteValue: Int = 4,
    val accentPattern: List<Boolean>? = null,
) {
    /** Which beats are accented. Defaults to "beat 0 only" when no custom pattern is set. */
    fun isAccented(beatIndex: Int): Boolean = accentPattern?.getOrNull(beatIndex) ?: (beatIndex == 0)

    companion object {
        val DEFAULT = TimeSignature(beatCount = 4)
    }
}
