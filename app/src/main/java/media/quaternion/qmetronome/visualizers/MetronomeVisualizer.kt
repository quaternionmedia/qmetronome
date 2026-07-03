package media.quaternion.qmetronome.visualizers

import media.quaternion.qmetronome.engine.BeatPhase
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * A traditional wooden triangle metronome, echoing the app's own launcher icon: a static body
 * silhouette with an arm that pivots near its *base* and swings upward, like a wiper - not a
 * clock pendulum hanging from a fixed point at the top (see [PendulumVisualizer] for that one).
 */
class MetronomeVisualizer : GlyphVisualizer {
    override val id = "metronome"
    override val displayName = "Metronome"

    override fun render(matrixSize: Int, beat: BeatPhase): IntArray {
        val canvas = GlyphCanvas(matrixSize)
        val beatsPerBar = beat.beatsPerBar.coerceAtLeast(1)
        val barProgress = (beat.beatIndex + beat.phase) / beatsPerBar
        // Triangle wave swinging the arm between -1 and +1.
        val swing = abs(((barProgress * 2f) % 2f) - 1f) * 2f - 1f
        val maxAngle = (Math.PI / 4.0).toFloat() // 45 degrees each way
        val angle = swing * maxAngle

        val pivotX = canvas.center
        val pivotY = matrixSize * 0.85f

        // Static body silhouette - a tapering trapezoid echoing ic_launcher_foreground's
        // triangle, at low constant brightness so it reads as scenery behind the moving arm.
        val bodyTopY = matrixSize * 0.30f
        val bodyTopHalfWidth = matrixSize * 0.06f
        val bodyBottomHalfWidth = matrixSize * 0.20f
        canvas.line(pivotX - bodyTopHalfWidth, bodyTopY, pivotX - bodyBottomHalfWidth, pivotY, BODY_BRIGHTNESS)
        canvas.line(pivotX + bodyTopHalfWidth, bodyTopY, pivotX + bodyBottomHalfWidth, pivotY, BODY_BRIGHTNESS)
        canvas.line(pivotX - bodyBottomHalfWidth, pivotY, pivotX + bodyBottomHalfWidth, pivotY, BODY_BRIGHTNESS)

        // Short enough that the tip stays on-canvas at full 45-degree deflection, matching the
        // margin reasoning in PendulumVisualizer - just measured from a base pivot instead of a
        // top one, so the arm swings up out of the body rather than hanging below it.
        val armLength = matrixSize * 0.62f
        val tipX = pivotX + armLength * sin(angle)
        val tipY = pivotY - armLength * cos(angle)

        // Faint guide line for the arm's position - the weight swinging is the actual beat cue,
        // this is scenery (see PendulumVisualizer for why it stays dim).
        canvas.line(pivotX, pivotY, tipX, tipY, 25)

        val flash = decayEase(beat.phase)
        val accentScale = if (beat.isAccent) 1.6f else 1f
        val weightRadius = matrixSize * (0.07f + 0.05f * flash) * accentScale
        val weightBrightness = (180 + 75 * flash).toInt()
        canvas.filledCircle(tipX, tipY, weightRadius, weightBrightness)
        canvas.filledCircle(pivotX, pivotY, matrixSize * 0.04f, 40)
        return canvas.toIntArray()
    }

    private companion object {
        const val BODY_BRIGHTNESS = 20
    }
}
