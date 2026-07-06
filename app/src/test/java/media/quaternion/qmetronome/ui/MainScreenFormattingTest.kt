package media.quaternion.qmetronome.ui

import media.quaternion.qmetronome.engine.MetronomeEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MainScreenFormattingTest {

    @Test
    fun `bpm inside the normal range displays as a rounded whole number in BPM`() {
        assertEquals("120", bpmDisplayValue(120f))
        assertEquals("BPM", bpmDisplayUnit(120f))
        assertEquals("96", bpmDisplayValue(95.6f))
        assertEquals("BPM", bpmDisplayUnit(1f))
        assertEquals("BPM", bpmDisplayUnit(400f))
    }

    @Test
    fun `bpm below the normal range displays as beats per hour`() {
        assertEquals("30.00", bpmDisplayValue(0.5f))
        assertEquals("BPH", bpmDisplayUnit(0.5f))
        assertEquals("6.00", bpmDisplayValue(0.1f))
        assertEquals("BPH", bpmDisplayUnit(0.1f))
    }

    @Test
    fun `bpm above the normal range displays as beats per second`() {
        assertEquals("10.00", bpmDisplayValue(600f))
        assertEquals("BPS", bpmDisplayUnit(600f))
        assertEquals("50.00", bpmDisplayValue(3000f))
        assertEquals("BPS", bpmDisplayUnit(3000f))
    }

    @Test
    fun `steppedBpm inside the normal range matches today's flat, additive stepping exactly`() {
        assertEquals(121f, steppedBpm(120f, 1f), 0.001f)
        assertEquals(119f, steppedBpm(120f, -1f), 0.001f)
        assertEquals(125f, steppedBpm(120f, 5f), 0.001f)
        // Boundary values themselves are still "in range" - still flat stepping there.
        assertEquals(2f, steppedBpm(MetronomeEngine.MIN_BPM, 1f), 0.001f)
        assertEquals(399f, steppedBpm(MetronomeEngine.MAX_BPM, -1f), 0.001f)
    }

    @Test
    fun `steppedBpm outside the normal range steps multiplicatively, not by a flat amount`() {
        val belowRange = 0.5f
        val oneStepUp = steppedBpm(belowRange, 1f)
        val twoStepsUp = steppedBpm(belowRange, 2f)
        // A flat +1f step would give 1.5 then 2.5 - multiplicative stepping instead keeps the
        // *ratio* between steps constant, so both increments should be well under 1f.
        assertTrue("expected a small multiplicative step, got a jump to $oneStepUp", oneStepUp - belowRange < 0.5f)
        assertTrue(
            "expected steppedBpm(x, 2) to equal two compounded single steps",
            abs(twoStepsUp - steppedBpm(oneStepUp, 1f)) < 0.001f,
        )

        val aboveRange = 600f
        val stepped = steppedBpm(aboveRange, 1f)
        assertTrue("expected a proportional step above range, got $stepped", stepped > aboveRange)
        assertTrue("expected a proportional (not flat +1) step, got $stepped", stepped - aboveRange > 1f)
    }

    @Test
    fun `steppedBpm round-trips - N steps up then N steps down returns arithmetically close to the start`() {
        val start = 0.2f // deep in extended/log territory
        var bpm = start
        repeat(10) { bpm = steppedBpm(bpm, 1f) }
        repeat(10) { bpm = steppedBpm(bpm, -1f) }
        assertEquals(start, bpm, start * 0.001f)
    }
}
