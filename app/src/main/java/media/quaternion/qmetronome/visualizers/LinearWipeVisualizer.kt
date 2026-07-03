package media.quaternion.qmetronome.visualizers

import media.quaternion.qmetronome.engine.BeatPhase
import kotlin.math.roundToInt

/**
 * A bright column that wipes left-to-right across the matrix once per *beat* (not once per bar,
 * like [SweepVisualizer]'s circular hand) - it resets to the left edge every beat, trailing a
 * short dim tail of fixed length so the tail never adds more total light as it travels (which
 * would otherwise fight the phase=0 flash below).
 */
class LinearWipeVisualizer : GlyphVisualizer {
    override val id = "linear_wipe"
    override val displayName = "Linear Wipe"

    override fun render(matrixSize: Int, beat: BeatPhase): IntArray {
        val canvas = GlyphCanvas(matrixSize)
        val leadX = beat.phase * (matrixSize - 1)
        val bottomY = (matrixSize - 1).toFloat()
        val flash = decayEase(beat.phase)
        val accentScale = if (beat.isAccent) 1.6f else 1f

        for (i in TAIL_STEPS downTo 1) {
            val tailX = leadX - i * TAIL_SPACING_PX
            if (tailX < 0f) continue
            val brightness = (TAIL_BASE_BRIGHTNESS * (1f - i.toFloat() / (TAIL_STEPS + 1))).toInt()
            canvas.line(tailX, 0f, tailX, bottomY, brightness)
        }

        // The leading edge's own brightness/width flash is the actual beat cue - the tail is
        // scenery showing direction of travel.
        val leadBrightness = (LEAD_BASE_BRIGHTNESS + LEAD_FLASH_RANGE * flash).toInt()
        val leadWidth = (1 + flash * accentScale).roundToInt().coerceAtLeast(1)
        for (dx in 0 until leadWidth) {
            canvas.line(leadX + dx, 0f, leadX + dx, bottomY, leadBrightness)
        }
        return canvas.toIntArray()
    }

    private companion object {
        const val TAIL_STEPS = 4
        const val TAIL_SPACING_PX = 1.5f
        const val TAIL_BASE_BRIGHTNESS = 60
        const val LEAD_BASE_BRIGHTNESS = 150
        const val LEAD_FLASH_RANGE = 105f
    }
}
