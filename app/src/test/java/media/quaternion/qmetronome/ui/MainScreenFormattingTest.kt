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
    fun `steppedBpm below range increases for positive steps, decreases for negative`() {
        // Closes a gap the existing "steps multiplicatively" test above didn't cover: it checked
        // the *magnitude* of a below-range step but never explicitly asserted its *direction*.
        val belowRange = 0.01f
        val up = steppedBpm(belowRange, 1f)
        val down = steppedBpm(belowRange, -1f)
        assertTrue("expected a positive step to increase bpm below range, got $up from $belowRange", up > belowRange)
        assertTrue("expected a negative step to decrease bpm below range, got $down from $belowRange", down < belowRange)
    }

    @Test
    fun `steppedBpm round-trips - N steps up then N steps down returns arithmetically close to the start`() {
        val start = 0.2f // deep in extended/log territory
        var bpm = start
        repeat(10) { bpm = steppedBpm(bpm, 1f) }
        repeat(10) { bpm = steppedBpm(bpm, -1f) }
        assertEquals(start, bpm, start * 0.001f)
    }

    @Test
    fun `bpmUnitFor matches bpmDisplayUnit's own thresholds exactly`() {
        assertEquals(BpmUnit.BPH, bpmUnitFor(0.5f))
        assertEquals(BpmUnit.BPM, bpmUnitFor(120f))
        assertEquals(BpmUnit.BPM, bpmUnitFor(MetronomeEngine.MIN_BPM))
        assertEquals(BpmUnit.BPM, bpmUnitFor(MetronomeEngine.MAX_BPM))
        assertEquals(BpmUnit.BPS, bpmUnitFor(600f))
    }

    @Test
    fun `bpmToUnitValue and bpmFromUnitValue are inverses`() {
        for (unit in BpmUnit.entries) {
            val original = bpmDefaultUnitValue(unit)
            val raw = bpmFromUnitValue(original, unit)
            val roundTripped = bpmToUnitValue(raw, unit)
            assertEquals("round-trip failed for $unit", original, roundTripped, 0.001f)
        }
    }

    @Test
    fun `bpmToUnitValue matches bpmDisplayValue's own conversion factors`() {
        assertEquals(30f, bpmToUnitValue(0.5f, BpmUnit.BPH), 0.001f)
        assertEquals(10f, bpmToUnitValue(600f, BpmUnit.BPS), 0.001f)
        assertEquals(120f, bpmToUnitValue(120f, BpmUnit.BPM), 0.001f)
    }

    @Test
    fun `bpmRangeFor covers exactly the span each unit is displayed for, no gaps or overlaps`() {
        val bphRange = bpmRangeFor(BpmUnit.BPH)
        val bpmRange = bpmRangeFor(BpmUnit.BPM)
        val bpsRange = bpmRangeFor(BpmUnit.BPS)

        assertEquals(MetronomeEngine.MIN_BPH, bphRange.start, 0.0001f)
        assertEquals(60f, bphRange.endInclusive, 0.001f) // bpmToUnitValue(MIN_BPM, BPH)
        assertEquals(MetronomeEngine.MIN_BPM, bpmRange.start, 0.001f)
        assertEquals(MetronomeEngine.MAX_BPM, bpmRange.endInclusive, 0.001f)
        assertEquals(400f / 60f, bpsRange.start, 0.001f) // bpmToUnitValue(MAX_BPM, BPS)
        assertEquals(bpmToUnitValue(MetronomeEngine.EXTENDED_MAX_BPM, BpmUnit.BPS), bpsRange.endInclusive, 0.001f)
    }

    @Test
    fun `bpmDefaultUnitValue lands inside that unit's own valid range`() {
        for (unit in BpmUnit.entries) {
            val default = bpmDefaultUnitValue(unit)
            val range = bpmRangeFor(unit)
            assertTrue("default $default for $unit is outside its own range $range", default in range)
        }
    }
}
