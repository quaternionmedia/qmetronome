package media.quaternion.qmetronome.visualizers

import media.quaternion.qmetronome.engine.BeatPhase
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** A literal swinging pendulum arm, like a mechanical metronome ticking side to side. */
class ClassicMetronomeVisualizer : GlyphVisualizer {
    override val id = "classic"
    override val displayName = "Classic"

    override fun render(matrixSize: Int, beat: BeatPhase): IntArray {
        val canvas = GlyphCanvas(matrixSize)
        val beatsPerBar = beat.beatsPerBar.coerceAtLeast(1)
        val barProgress = (beat.beatIndex + beat.phase) / beatsPerBar
        // Triangle wave swinging the arm between -1 and +1.
        val swing = abs(((barProgress * 2f) % 2f) - 1f) * 2f - 1f
        val maxAngle = (Math.PI / 4.0).toFloat() // 45 degrees each way
        val angle = swing * maxAngle

        val pivotX = canvas.center
        val pivotY = matrixSize * 0.12f
        // Short enough that the tip stays on-canvas at full 45-degree deflection
        // (armLength * sin(45°) must clear canvas.center horizontally) - it was previously long
        // enough to swing the weight off the edge at full swing, clipping away exactly the
        // moment that's supposed to be brightest.
        val armLength = matrixSize * 0.55f
        val tipX = pivotX + armLength * sin(angle)
        val tipY = pivotY + armLength * cos(angle)

        // Faint and constant - just a guide showing the arm's position. Deliberately dim: at
        // full brightness this constant-per-frame line diluted the weight's beat/accent flash in
        // the *total* light measurement (worse: overlapping the bright weight circle near the
        // tip wasted some of the line's contribution to brightness clamping, which paradoxically
        // made a dimmer frame measure as "brighter" overall). The weight swinging is the actual
        // beat cue; the arm is scenery.
        canvas.line(pivotX, pivotY, tipX, tipY, 25)

        val flash = decayEase(beat.phase)
        val accentScale = if (beat.isAccent) 1.6f else 1f
        val weightRadius = matrixSize * (0.07f + 0.05f * flash) * accentScale
        val weightBrightness = (180 + 75 * flash).toInt()
        canvas.filledCircle(tipX, tipY, weightRadius, weightBrightness)
        canvas.filledCircle(pivotX, pivotY, matrixSize * 0.04f, 40)
        return canvas.toIntArray()
    }
}
