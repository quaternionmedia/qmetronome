package com.example.qmetronome.visualizers

import com.example.qmetronome.engine.BeatPhase

/** A ring that expands outward from the center on every beat, like a ripple in water. */
class RingExpandVisualizer : GlyphVisualizer {
    override val id = "ring_expand"
    override val displayName = "Ripple"

    override fun render(matrixSize: Int, beat: BeatPhase): IntArray {
        val canvas = GlyphCanvas(matrixSize)
        val maxRadius = canvas.center
        val radius = beat.phase * maxRadius
        val brightness = (255 * decayEase(beat.phase * 0.8f)).toInt().coerceAtLeast(20)
        val thickness = if (beat.isAccent) 2.2f else 1.4f
        canvas.ring(canvas.center, canvas.center, radius, thickness, brightness)

        // The expanding ring alone isn't a reliable beat cue: its circumference (and so its
        // total light) keeps growing as it spreads, so a glance at "how much is lit up" peaks
        // mid-decay, not at the beat. A real splash is brightest and most concentrated at the
        // moment of impact, fading as the ripple spreads - model that explicitly.
        val splashBrightness = (255 * decayEase(beat.phase)).toInt()
        // The splash is bigger on bar 1, not just brighter - brightness alone is already
        // saturated near phase=0, so radius is what stays distinguishable.
        val accentScale = if (beat.isAccent) 1.7f else 1f
        val splashRadius = matrixSize * (0.06f + 0.1f * decayEase(beat.phase)) * accentScale
        canvas.filledCircle(canvas.center, canvas.center, splashRadius, splashBrightness)
        return canvas.toIntArray()
    }
}
