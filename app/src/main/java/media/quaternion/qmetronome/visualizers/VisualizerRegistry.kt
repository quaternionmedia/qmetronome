package media.quaternion.qmetronome.visualizers

/**
 * Lists every available [GlyphVisualizer]. To add your own, implement the interface in its own
 * file and add an instance here - it will automatically show up in the in-app picker and become
 * selectable via long-press on the Glyph Button.
 */
object VisualizerRegistry {

    val all: List<GlyphVisualizer> = listOf(
        PulseVisualizer(),
        SweepVisualizer(),
        RingExpandVisualizer(),
        BounceVisualizer(),
        StrobeVisualizer(),
        SpiralVisualizer(),
        SplashVisualizer(),
        PendulumVisualizer(),
        DoublePendulumVisualizer(),
        ChaosVisualizer(),
        MetronomeVisualizer(),
        LinearWipeVisualizer(),
        FireworkVisualizer(),
    )

    /** The traditional triangle-metronome visualizer, not [all]'s first entry - keeps the
     * picker's order independent of which one loads on a fresh install. */
    val default: GlyphVisualizer get() = all.first { it.id == "metronome" }

    fun byId(id: String?): GlyphVisualizer = all.firstOrNull { it.id == id } ?: default

    fun next(current: GlyphVisualizer): GlyphVisualizer {
        val index = all.indexOf(current)
        return all[(index + 1) % all.size]
    }

    fun previous(current: GlyphVisualizer): GlyphVisualizer {
        val index = all.indexOf(current)
        return all[(index - 1 + all.size) % all.size]
    }
}
