package com.example.qmetronome.visualizers

import com.example.qmetronome.engine.BeatPhase

/** Full-matrix flash on every beat, dim breathing glow between beats. High visibility from across a room. */
class StrobeVisualizer : GlyphVisualizer {
    override val id = "strobe"
    override val displayName = "Strobe"

    override fun render(matrixSize: Int, beat: BeatPhase): IntArray {
        val canvas = GlyphCanvas(matrixSize)
        val flash = (255 * decayEase(beat.phase * 3f)).toInt()
        val breathing = 6 + (4 * (1f - decayEase(beat.phase))).toInt()
        canvas.fill(flash.coerceAtLeast(breathing))
        return canvas.toIntArray()
    }
}
