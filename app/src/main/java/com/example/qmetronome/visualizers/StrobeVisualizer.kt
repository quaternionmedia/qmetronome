package com.example.qmetronome.visualizers

import com.example.qmetronome.engine.BeatPhase

/** Full-matrix flash on every beat, dim breathing glow between beats. High visibility from across a room. */
class StrobeVisualizer : GlyphVisualizer {
    override val id = "strobe"
    override val displayName = "Strobe"

    override fun render(matrixSize: Int, beat: BeatPhase): IntArray {
        val canvas = GlyphCanvas(matrixSize)
        // A full-matrix fill is already at maximum size, so size can't be the accent cue here
        // the way it is for the other visualizers - peak brightness itself has to carry it: bar
        // 1 flashes fully white, regular beats flash slightly short of it.
        val peak = if (beat.isAccent) 255 else 200
        val flash = (peak * decayEase(beat.phase * 3f)).toInt()
        val breathing = 6 + (4 * (1f - decayEase(beat.phase))).toInt()
        canvas.fill(flash.coerceAtLeast(breathing))
        return canvas.toIntArray()
    }
}
