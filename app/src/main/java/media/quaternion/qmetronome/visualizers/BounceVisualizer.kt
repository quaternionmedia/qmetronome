package media.quaternion.qmetronome.visualizers

import media.quaternion.qmetronome.engine.BeatPhase
import kotlin.math.abs

/** A dot that bounces side to side, landing on one extreme at every beat - like a VU needle. */
class BounceVisualizer : GlyphVisualizer {
    override val id = "bounce"
    override val displayName = "Bounce"

    override fun render(matrixSize: Int, beat: BeatPhase): IntArray {
        val canvas = GlyphCanvas(matrixSize)
        val beatsPerBar = beat.beatsPerBar.coerceAtLeast(1)
        val barProgress = (beat.beatIndex + beat.phase) / beatsPerBar
        // Triangle wave across the bar width: 0 -> 1 -> 0 -> 1 ...
        val triangle = abs(((barProgress * 2f) % 2f) - 1f)
        val margin = matrixSize * 0.15f
        val x = margin + triangle * (matrixSize - 1 - 2 * margin)
        val y = canvas.center

        // Flash the dot itself (not just the secondary ring) so the beat reads clearly even at a
        // glance, without relying on the secondary ring as the only cue.
        val flash = decayEase(beat.phase)
        // Bar 1's dot (and ring) are bigger/brighter, not just the regular beat flash - radius is
        // what stays distinguishable once brightness saturates near phase=0.
        val accentScale = if (beat.isAccent) 1.4f else 1f
        val dotBrightness = (170 + 85 * flash).toInt()
        val dotRadius = matrixSize * (0.13f + 0.05f * flash) * accentScale
        canvas.filledCircle(x, y, dotRadius, dotBrightness)

        val ringBrightness = (140 * flash * accentScale).toInt()
        if (ringBrightness > 0) canvas.ring(canvas.center, canvas.center, canvas.center * 0.95f, 1f, ringBrightness)
        return canvas.toIntArray()
    }
}
