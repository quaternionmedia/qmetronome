package com.example.qmetronome.visualizers

import com.example.qmetronome.engine.BeatPhase

/** A solid disc at the center that flashes bright on each beat and decays until the next one. */
class PulseVisualizer : GlyphVisualizer {
    override val id = "pulse"
    override val displayName = "Pulse"

    override fun render(matrixSize: Int, beat: BeatPhase): IntArray {
        val canvas = GlyphCanvas(matrixSize)
        val peak = if (beat.isAccent) 255 else 190
        val brightness = (peak * decayEase(beat.phase)).toInt()
        val maxRadius = matrixSize * (if (beat.isAccent) 0.42f else 0.34f)
        val radius = maxRadius * (1f - 0.5f * decayEase(beat.phase))
        canvas.filledCircle(canvas.center, canvas.center, radius, brightness.coerceAtLeast(8))
        return canvas.toIntArray()
    }
}
