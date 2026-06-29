package media.quaternion.qmetronome.visualizers

import media.quaternion.qmetronome.engine.BeatPhase
import kotlin.math.cos
import kotlin.math.sin

/** A dot that spirals outward from the center across the bar, snapping back in on beat 1. */
class SpiralVisualizer : GlyphVisualizer {
    override val id = "spiral"
    override val displayName = "Spiral"

    override fun render(matrixSize: Int, beat: BeatPhase): IntArray {
        val canvas = GlyphCanvas(matrixSize)
        val beatsPerBar = beat.beatsPerBar.coerceAtLeast(1)
        val barProgress = (beat.beatIndex + beat.phase) / beatsPerBar
        val loops = 2.5f
        val angle = barProgress * loops * 2f * Math.PI.toFloat()
        val radius = barProgress * canvas.center * 0.9f
        val x = canvas.center + radius * cos(angle)
        val y = canvas.center + radius * sin(angle)

        val flash = decayEase(beat.phase)
        val accentScale = if (beat.isAccent) 1.6f else 1f
        val dotRadius = matrixSize * (0.07f + 0.06f * flash) * accentScale
        val dotBrightness = (170 + 85 * flash).toInt()

        canvas.filledCircle(x, y, dotRadius, dotBrightness)
        // A faint center glow shows where the spiral restarts at the top of every bar.
        canvas.filledCircle(canvas.center, canvas.center, matrixSize * 0.04f, 40)
        return canvas.toIntArray()
    }
}
