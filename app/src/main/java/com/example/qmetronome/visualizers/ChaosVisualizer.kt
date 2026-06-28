package com.example.qmetronome.visualizers

import com.example.qmetronome.engine.BeatPhase

/**
 * Particles scattered by the logistic map (the textbook chaotic dynamical system: `x = r*x*(1-x)`
 * with `r` in the chaotic regime) seeded from the running beat count. Deterministic and
 * repeatable for the same beat (a requirement of [GlyphVisualizer.render] being a pure function),
 * but looks scattered and unpredictable from one beat to the next, which is the point.
 */
class ChaosVisualizer : GlyphVisualizer {
    override val id = "chaos"
    override val displayName = "Chaos"

    override fun render(matrixSize: Int, beat: BeatPhase): IntArray {
        val canvas = GlyphCanvas(matrixSize)
        val flash = decayEase(beat.phase)
        val accentScale = if (beat.isAccent) 1.5f else 1f
        val particleCount = if (beat.isAccent) 9 else 6
        val brightness = (90 + 165 * flash).toInt()
        val particleRadius = matrixSize * 0.05f * accentScale

        var x = 0.1f + (beat.totalBeats % 97L) / 97f * 0.8f
        repeat(particleCount) {
            x = 3.97f * x * (1f - x)
            var y = (x * 13f) % 1f
            y = 3.91f * y * (1f - y)

            val px = x * (matrixSize - 1)
            val py = y * (matrixSize - 1)
            canvas.filledCircle(px, py, particleRadius, brightness)
        }
        return canvas.toIntArray()
    }
}
