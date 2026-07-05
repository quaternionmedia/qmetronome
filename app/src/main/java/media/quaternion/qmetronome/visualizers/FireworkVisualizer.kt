package media.quaternion.qmetronome.visualizers

import media.quaternion.qmetronome.engine.BeatPhase
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A shell burst of thin radiating trails from the center, each with its own angle/reach/flicker -
 * distinct from [SplashVisualizer]'s solid blob-and-droplets: nothing here ever fills in, it's all
 * thin sparks, closer to a firework shell than a liquid splash.
 */
class FireworkVisualizer : GlyphVisualizer {
    override val id = "firework"
    override val displayName = "Firework"

    override fun render(matrixSize: Int, beat: BeatPhase): IntArray {
        val canvas = GlyphCanvas(matrixSize)
        val flash = decayEase(beat.phase)
        val accentScale = if (beat.isAccent) 1.6f else 1f
        val trailCount = if (beat.isAccent) 14 else 8
        val maxReach = canvas.center * 0.95f * accentScale

        // Deterministic per-beat randomness, seeded from the beat's own identity - the burst
        // shape stays fixed while it plays out across this beat's frames (only its reach/
        // brightness evolve with phase), but the *next* beat gets a fresh-looking burst.
        val random = Random(beat.beatIndex * 31 + beat.totalBeats.toInt())

        // Bright core at the moment of ignition, matching every other visualizer's flash pulse.
        val coreRadius = matrixSize * 0.04f * accentScale
        canvas.filledCircle(canvas.center, canvas.center, coreRadius, (255 * flash).toInt())

        repeat(trailCount) { i ->
            val baseAngle = (i.toFloat() / trailCount) * 2f * Math.PI.toFloat()
            val jitter = (random.nextFloat() - 0.5f) * 0.4f
            val angle = baseAngle + jitter
            val sparkle = 0.6f + random.nextFloat() * 0.4f

            // A shell bursts to a good chunk of its reach immediately, then keeps drifting
            // outward a little further as it fades - never collapsed to a single point at
            // ignition (that would bunch every trail onto the same handful of pixels and, after
            // GlyphCanvas's per-pixel brightness cap, actually put out *less* total light at
            // onset than mid-decay - backwards for a beat that needs to read as a flash).
            val reach = maxReach * (0.5f + 0.5f * (1f - flash)) * sparkle
            val tipX = canvas.center + reach * cos(angle)
            val tipY = canvas.center + reach * sin(angle)
            val brightness = (220 * flash * sparkle).toInt()
            canvas.line(canvas.center, canvas.center, tipX, tipY, brightness)
        }
        return canvas.toIntArray()
    }
}
