package media.quaternion.qmetronome.visualizers

import media.quaternion.qmetronome.engine.BeatPhase
import kotlin.math.cos
import kotlin.math.sin

/**
 * A two-arm pendulum that looks chaotic but isn't actually simulated: [render] is a pure function
 * of [BeatPhase] with no persisted state between frames (every [GlyphVisualizer] is), so this
 * combines two incommensurate-looking sinusoids instead of integrating real double-pendulum
 * physics. It still repeats deterministically bar to bar, which a real chaotic simulation
 * wouldn't.
 */
class DoublePendulumVisualizer : GlyphVisualizer {
    override val id = "double_pendulum"
    override val displayName = "Double Pendulum"

    override fun render(matrixSize: Int, beat: BeatPhase): IntArray {
        val canvas = GlyphCanvas(matrixSize)
        val beatsPerBar = beat.beatsPerBar.coerceAtLeast(1)
        val barProgress = (beat.beatIndex + beat.phase) / beatsPerBar
        val t = barProgress * 2f * Math.PI.toFloat()

        val pivotX = canvas.center
        val pivotY = canvas.center * 0.5f
        val armLength = matrixSize * 0.32f

        val angle1 = sin(t) * 1.4f
        val angle2 = sin(t * 2.41f + 1.1f) * 2.1f + angle1

        val jointX = pivotX + armLength * sin(angle1)
        val jointY = pivotY + armLength * cos(angle1)
        val tipX = jointX + armLength * sin(angle2)
        val tipY = jointY + armLength * cos(angle2)

        canvas.line(pivotX, pivotY, jointX, jointY, 45)
        canvas.line(jointX, jointY, tipX, tipY, 45)
        canvas.filledCircle(jointX, jointY, matrixSize * 0.035f, 70)
        canvas.filledCircle(pivotX, pivotY, matrixSize * 0.03f, 60)

        val flash = decayEase(beat.phase)
        val accentScale = if (beat.isAccent) 1.6f else 1f
        canvas.filledCircle(
            tipX,
            tipY,
            matrixSize * (0.08f + 0.05f * flash) * accentScale,
            (170 + 85 * flash).toInt(),
        )
        return canvas.toIntArray()
    }
}
