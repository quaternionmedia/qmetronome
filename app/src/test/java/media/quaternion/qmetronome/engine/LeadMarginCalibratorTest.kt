package media.quaternion.qmetronome.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure math, no Robolectric/`AudioTrack` involvement needed - the same "pure decision logic,
 * testable in isolation" precedent [MetronomeEngine.beatZeroCountInNanos] already establishes.
 */
class LeadMarginCalibratorTest {

    @Test
    fun `starts with no correction`() {
        val calibrator = LeadMarginCalibrator()
        assertEquals(0L, calibrator.correctionNanos())
    }

    @Test
    fun `a single late placement pulls the correction up, not all the way to the observed error`() {
        val calibrator = LeadMarginCalibrator()
        calibrator.recordPlacementError(47_000_000L) // 47ms late, this project's own measured baseline
        val correction = calibrator.correctionNanos()
        assertTrue("expected some upward correction, got $correction", correction > 0L)
        assertTrue("expected a damped step, not the full 47ms in one sample, got $correction", correction < 47_000_000L)
    }

    @Test
    fun `repeated identical late placements converge toward the observed error`() {
        val calibrator = LeadMarginCalibrator()
        repeat(50) { calibrator.recordPlacementError(47_000_000L) }
        val correction = calibrator.correctionNanos()
        assertTrue(
            "expected convergence close to 47ms after 50 samples, got $correction",
            correction in 46_000_000L..47_000_000L,
        )
    }

    @Test
    fun `an on-time or early placement does not push the correction negative`() {
        val calibrator = LeadMarginCalibrator()
        calibrator.recordPlacementError(-5_000_000L) // landed 5ms early - not an error worth correcting for
        assertEquals(0L, calibrator.correctionNanos())
    }

    @Test
    fun `a large correction relaxes back down once placements start landing on time`() {
        val calibrator = LeadMarginCalibrator()
        repeat(50) { calibrator.recordPlacementError(47_000_000L) }
        val beforeRelax = calibrator.correctionNanos()
        repeat(50) { calibrator.recordPlacementError(0L) }
        val afterRelax = calibrator.correctionNanos()
        assertTrue("expected the correction to relax down once errors stop, $afterRelax vs $beforeRelax", afterRelax < beforeRelax)
        // An EMA approaches zero asymptotically, never exactly reaching it in finite steps -
        // "negligible" (well under a sample period's worth of nanoseconds) is the right bar here.
        assertTrue("expected the correction to have relaxed to near-zero, got $afterRelax", afterRelax < 100_000L)
    }
}
