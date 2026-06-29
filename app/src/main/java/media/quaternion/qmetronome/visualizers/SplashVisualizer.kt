package media.quaternion.qmetronome.visualizers

import media.quaternion.qmetronome.engine.BeatPhase
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A solid blob that blooms at the center and scatters droplets outward, fading as they travel -
 * distinct from [RingExpandVisualizer]'s hollow expanding ring: this one stays filled/concentrated
 * rather than thinning out into a ring as it grows.
 */
class SplashVisualizer : GlyphVisualizer {
    override val id = "splash"
    override val displayName = "Splash"

    override fun render(matrixSize: Int, beat: BeatPhase): IntArray {
        val canvas = GlyphCanvas(matrixSize)
        val flash = decayEase(beat.phase)
        val accentScale = if (beat.isAccent) 1.5f else 1f

        val coreRadius = matrixSize * (0.05f + 0.16f * flash) * accentScale
        val coreBrightness = (255 * flash).toInt().coerceAtLeast(15)
        canvas.filledCircle(canvas.center, canvas.center, coreRadius, coreBrightness)

        // Droplets scatter outward from the core as the beat decays - more of them, on bar 1.
        val dropletCount = if (beat.isAccent) 8 else 5
        val travel = (1f - flash) * canvas.center * 0.9f
        val dropletBrightness = (180 * flash).toInt()
        for (i in 0 until dropletCount) {
            val angle = (i.toFloat() / dropletCount) * 2f * Math.PI.toFloat()
            val dx = canvas.center + travel * cos(angle)
            val dy = canvas.center + travel * sin(angle)
            canvas.add(dx.roundToInt(), dy.roundToInt(), dropletBrightness)
        }
        return canvas.toIntArray()
    }
}
