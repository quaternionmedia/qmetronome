package com.example.qmetronome.visualizers

import com.example.qmetronome.engine.BeatPhase
import kotlin.math.cos
import kotlin.math.sin

/** A bright dot that sweeps one full lap of the matrix per bar, like a clock hand keeping time. */
class SweepVisualizer : GlyphVisualizer {
    override val id = "sweep"
    override val displayName = "Sweep"

    override fun render(matrixSize: Int, beat: BeatPhase): IntArray {
        val canvas = GlyphCanvas(matrixSize)
        val beatsPerBar = beat.beatsPerBar.coerceAtLeast(1)
        val barProgress = (beat.beatIndex + beat.phase) / beatsPerBar
        val angle = barProgress * 2f * Math.PI.toFloat() - (Math.PI.toFloat() / 2f)
        val radius = canvas.center * 0.85f
        val x = canvas.center + radius * cos(angle)
        val y = canvas.center + radius * sin(angle)

        // The sweep position alone isn't a usable beat cue without audio - it moves
        // continuously, so there's no single instant that reads as "the beat". Flash the dot
        // itself brighter and bigger right on the beat so there's always one.
        val flash = decayEase(beat.phase)
        val dotBrightness = (160 + 95 * flash).toInt()
        val dotRadius = matrixSize * (0.09f + 0.05f * flash)

        canvas.ring(canvas.center, canvas.center, radius, 1.2f, 30)
        canvas.filledCircle(x, y, dotRadius, dotBrightness)
        // Mark beat 1 so the bar boundary is visible while the dot has swept elsewhere - but not
        // during beat 1 itself, where the dot already sits right on top of this marker; drawing
        // both there just clips together under brightness clamping and dampens the beat flash
        // exactly where it matters most (the downbeat).
        if (beat.beatIndex != 0) {
            canvas.filledCircle(canvas.center, canvas.center - radius, matrixSize * 0.05f, 90)
        }
        return canvas.toIntArray()
    }
}
