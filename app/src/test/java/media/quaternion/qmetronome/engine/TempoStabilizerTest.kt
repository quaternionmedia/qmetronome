package media.quaternion.qmetronome.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TempoStabilizerTest {

    @Test
    fun `first update passes the raw value straight through`() {
        val stabilizer = TempoStabilizer()
        assertEquals(120f, stabilizer.update(120f), 0.001f)
    }

    @Test
    fun `current is null before the first update`() {
        assertEquals(null, TempoStabilizer().current)
    }

    @Test
    fun `damps a single noisy outlier instead of passing it straight through`() {
        val stabilizer = TempoStabilizer(smoothingFactor = 0.25f)
        stabilizer.update(120f)
        stabilizer.update(120f)
        val afterOutlier = stabilizer.update(130f) // a single noisy spike

        // A raw passthrough would jump the full 10 BPM; smoothing should only move part way.
        assertTrue("expected the outlier to be damped, moved to $afterOutlier", afterOutlier < 125f)
        assertTrue(afterOutlier > 120f)
    }

    @Test
    fun `converges close to a sustained new tempo within a handful of updates`() {
        val stabilizer = TempoStabilizer(smoothingFactor = 0.25f)
        stabilizer.update(120f)
        var last = 120f
        repeat(20) { last = stabilizer.update(140f) }
        assertEquals(140f, last, 0.5f)
    }

    @Test
    fun `reset drops history so the next update is an unsmoothed passthrough`() {
        val stabilizer = TempoStabilizer()
        stabilizer.update(120f)
        stabilizer.reset()
        assertEquals(90f, stabilizer.update(90f), 0.001f)
    }

    @Test
    fun `smooths a jittery sequence to lower variance than the raw input`() {
        val stabilizer = TempoStabilizer(smoothingFactor = 0.2f)
        val raw = listOf(120f, 121.5f, 118.7f, 122.1f, 119.3f, 120.8f, 117.9f, 121.2f)
        val smoothedSeq = raw.map { stabilizer.update(it) }

        assertTrue(
            "expected smoothed variance (${variance(smoothedSeq)}) to be lower than raw " +
                "variance (${variance(raw)})",
            variance(smoothedSeq) < variance(raw),
        )
    }

    private fun variance(values: List<Float>): Double {
        val mean = values.average()
        return values.sumOf { (it - mean) * (it - mean) } / values.size
    }
}
