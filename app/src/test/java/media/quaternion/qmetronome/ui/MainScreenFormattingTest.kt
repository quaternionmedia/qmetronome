package media.quaternion.qmetronome.ui

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
