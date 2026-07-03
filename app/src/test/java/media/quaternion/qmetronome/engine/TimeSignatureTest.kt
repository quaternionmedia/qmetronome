package media.quaternion.qmetronome.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeSignatureTest {

    @Test
    fun `with no custom accent pattern, only beat 0 is accented`() {
        val timeSignature = TimeSignature(beatCount = 4)

        assertTrue(timeSignature.isAccented(0))
        assertFalse(timeSignature.isAccented(1))
        assertFalse(timeSignature.isAccented(2))
        assertFalse(timeSignature.isAccented(3))
    }

    @Test
    fun `a custom accent pattern overrides the beat-0-only default`() {
        val timeSignature = TimeSignature(beatCount = 4, accentPattern = listOf(true, false, true, false))

        assertTrue(timeSignature.isAccented(0))
        assertFalse(timeSignature.isAccented(1))
        assertTrue(timeSignature.isAccented(2))
        assertFalse(timeSignature.isAccented(3))
    }

    @Test
    fun `a beat index outside a custom pattern's range falls back to the beat-0-only default`() {
        val timeSignature = TimeSignature(beatCount = 4, accentPattern = listOf(true, false))

        assertFalse(timeSignature.isAccented(2))
        assertFalse(timeSignature.isAccented(3))
    }

    @Test
    fun `DEFAULT is a plain 4-4 bar at 120bpm with no custom accent pattern`() {
        assertEquals(4, TimeSignature.DEFAULT.beatCount)
        assertEquals(4, TimeSignature.DEFAULT.unitNoteValue)
        assertEquals(120f, TimeSignature.DEFAULT.bpm, 0.01f)
        assertTrue(TimeSignature.DEFAULT.isAccented(0))
    }

    @Test
    fun `beatCount and bpm are independent fields`() {
        val timeSignature = TimeSignature(beatCount = 7, unitNoteValue = 8, bpm = 90f)

        assertEquals(7, timeSignature.beatCount)
        assertEquals(8, timeSignature.unitNoteValue)
        assertEquals(90f, timeSignature.bpm, 0.01f)
    }
}
