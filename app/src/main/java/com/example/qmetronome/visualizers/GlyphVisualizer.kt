package com.example.qmetronome.visualizers

import com.example.qmetronome.engine.BeatPhase

/**
 * A single tempo-reactive Glyph Matrix animation algorithm.
 *
 * To contribute a new one: create a class implementing this interface (see [PulseVisualizer]
 * for the simplest example), then add it to [VisualizerRegistry.all]. That's it - the engine
 * calls [render] roughly 40 times a second while the metronome is playing and pushes whatever
 * you return straight to the Glyph Matrix (and to the in-app preview), so you don't need to
 * touch any service, threading or SDK code.
 *
 * **Requirement: the beat must be perceptible without audio.** [render] must put out more total
 * light at [BeatPhase.phase] `== 0` than partway through the beat (e.g. `phase == 0.5`) - a
 * constantly-bright moving element (see `Sweep`/`Bounce` for examples that actually do this) is
 * not enough on its own, since its *position* changing doesn't read as "the beat" to someone
 * who can't hear the click. This is what makes the toy usable as a visual metronome for
 * deaf/hard-of-hearing musicians or in a silent room - see
 * [VisualizerRenderTest][com.example.qmetronome.visualizers.VisualizerRenderTest] for the test
 * that enforces it on every built-in visualizer.
 */
interface GlyphVisualizer {
    /** Stable id, used for persistence - keep it constant once shipped. */
    val id: String

    /** Shown in the picker UI. */
    val displayName: String

    /**
     * Returns a brightness array of size [matrixSize] * [matrixSize], row-major, 0..255 per
     * pixel, for the given instant in the beat.
     */
    fun render(matrixSize: Int, beat: BeatPhase): IntArray
}
