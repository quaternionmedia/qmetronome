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
 *
 * [accentPattern] marks non-downbeat positions with a [BeatAccent] tier - `null`, a missing
 * index, or [BeatAccent.NONE] all mean "no accent" ([ClickSound.REGULAR]). Beat 0 is always
 * [ClickSound.BAR] regardless of what (if anything) this holds for index 0 - see
 * [MetronomeEngine.beatTypeFor], the single place that mapping happens.
 *
 * [midiOverrides] lets a *specific* beat index carry its own [MidiBeatAction] that wins over
 * whatever its resolved [ClickSound] type would otherwise send (see
 * [MetronomeEngine.resolveMidiActionForBeat]) - a beat's accent tier still drives its click tone
 * and the type-level MIDI default, but a beat can additionally be singled out for its own MIDI
 * output without changing its accent. A sparse `Map`, not a padded `List` like [accentPattern]:
 * overrides are typically 0-2 beats out of up to 24, unlike accent tiers where every beat
 * conceptually has one.
 */
data class TimeSignature(
    val beatCount: Int,
    val unitNoteValue: Int = 4,
    val bpm: Float = 120f,
    val accentPattern: List<BeatAccent>? = null,
    val visualizerId: String? = null,
    val midiOverrides: Map<Int, MidiBeatAction>? = null,
) {
    /** The accent tier marking [beatIndex], defaulting to [BeatAccent.NONE] when no custom
     * pattern is set or this index isn't covered by it. */
    fun accentAt(beatIndex: Int): BeatAccent = accentPattern?.getOrNull(beatIndex) ?: BeatAccent.NONE

    /** This beat's own MIDI override, if one has been authored for it - `null` when [beatIndex]
     * has no override, meaning its resolved [ClickSound] type's own default action applies instead
     * (see [MetronomeEngine.resolveMidiActionForBeat]). */
    fun midiOverrideAt(beatIndex: Int): MidiBeatAction? = midiOverrides?.get(beatIndex)

    companion object {
        val DEFAULT = TimeSignature(beatCount = 4)
    }
}
