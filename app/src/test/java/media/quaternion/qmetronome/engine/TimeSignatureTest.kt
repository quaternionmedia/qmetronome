package media.quaternion.qmetronome.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeSignatureTest {

    @Test
    fun `with no custom accent pattern, every beat reads as unaccented`() {
        val timeSignature = TimeSignature(beatCount = 4)

        assertEquals(BeatAccent.NONE, timeSignature.accentAt(0))
        assertEquals(BeatAccent.NONE, timeSignature.accentAt(1))
        assertEquals(BeatAccent.NONE, timeSignature.accentAt(2))
        assertEquals(BeatAccent.NONE, timeSignature.accentAt(3))
    }

    @Test
    fun `a custom accent pattern is read back per beat`() {
        val timeSignature = TimeSignature(
            beatCount = 4,
            accentPattern = listOf(BeatAccent.NONE, BeatAccent.ACCENT, BeatAccent.STRONG_ACCENT, BeatAccent.CUSTOM),
        )

        assertEquals(BeatAccent.NONE, timeSignature.accentAt(0))
        assertEquals(BeatAccent.ACCENT, timeSignature.accentAt(1))
        assertEquals(BeatAccent.STRONG_ACCENT, timeSignature.accentAt(2))
        assertEquals(BeatAccent.CUSTOM, timeSignature.accentAt(3))
    }

    @Test
    fun `a beat index outside a custom pattern's range falls back to NONE`() {
        val timeSignature = TimeSignature(beatCount = 4, accentPattern = listOf(BeatAccent.ACCENT))

        assertEquals(BeatAccent.NONE, timeSignature.accentAt(2))
        assertEquals(BeatAccent.NONE, timeSignature.accentAt(3))
    }

    @Test
    fun `DEFAULT is a plain 4-4 bar at 120bpm with no custom accent pattern`() {
        assertEquals(4, TimeSignature.DEFAULT.beatCount)
        assertEquals(4, TimeSignature.DEFAULT.unitNoteValue)
        assertEquals(120f, TimeSignature.DEFAULT.bpm, 0.01f)
        assertEquals(BeatAccent.NONE, TimeSignature.DEFAULT.accentAt(0))
    }

    @Test
    fun `beatCount and bpm are independent fields`() {
        val timeSignature = TimeSignature(beatCount = 7, unitNoteValue = 8, bpm = 90f)

        assertEquals(7, timeSignature.beatCount)
        assertEquals(8, timeSignature.unitNoteValue)
        assertEquals(90f, timeSignature.bpm, 0.01f)
    }

    @Test
    fun `with no midi overrides, every beat reads as null - no override`() {
        val timeSignature = TimeSignature(beatCount = 4)

        assertNull(timeSignature.midiOverrideAt(0))
        assertNull(timeSignature.midiOverrideAt(2))
    }

    @Test
    fun `a beat's own midi override is read back by index, others stay null`() {
        val override = MidiBeatAction(type = MidiActionType.NOTE, channel = 9, number = 72, value = 110, durationMs = 15)
        val timeSignature = TimeSignature(beatCount = 4, midiOverrides = mapOf(2 to override))

        assertEquals(override, timeSignature.midiOverrideAt(2))
        assertNull(timeSignature.midiOverrideAt(0))
        assertNull(timeSignature.midiOverrideAt(1))
        assertNull(timeSignature.midiOverrideAt(3))
    }
}
