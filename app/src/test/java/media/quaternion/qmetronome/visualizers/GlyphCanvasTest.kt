package media.quaternion.qmetronome.visualizers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlyphCanvasTest {

    @Test
    fun `out of bounds pixels are silently ignored`() {
        val canvas = GlyphCanvas(13)
        canvas.set(-1, 0, 255)
        canvas.set(0, -1, 255)
        canvas.set(13, 0, 255)
        canvas.set(0, 13, 255)
        // Should not throw, and nothing should have been written.
        assertTrue(canvas.pixels.all { it == 0 })
    }

    @Test
    fun `brightness is clamped to the 0 to 255 range`() {
        val canvas = GlyphCanvas(5)
        canvas.set(2, 2, 999)
        assertEquals(255, canvas.pixels[2 * 5 + 2])

        canvas.set(2, 2, -50)
        assertEquals(0, canvas.pixels[2 * 5 + 2])
    }

    @Test
    fun `add caps at 255 instead of overflowing`() {
        val canvas = GlyphCanvas(5)
        canvas.add(1, 1, 200)
        canvas.add(1, 1, 200)
        assertEquals(255, canvas.pixels[1 * 5 + 1])
    }

    @Test
    fun `filledCircle and ring tolerate zero, negative and huge radii without throwing`() {
        val canvas = GlyphCanvas(13)
        canvas.filledCircle(6f, 6f, 0f, 255)
        canvas.filledCircle(6f, 6f, -5f, 255)
        canvas.filledCircle(6f, 6f, 1000f, 255)
        canvas.ring(6f, 6f, 0f, 1f, 255)
        canvas.ring(6f, 6f, -5f, 1f, 255)
        canvas.ring(6f, 6f, 1000f, 1f, 255)
        // No assertion beyond "didn't throw" - out-of-range geometry is a real input from
        // visualizer math (e.g. phase-driven radii), not just a theoretical edge case.
    }

    @Test
    fun `toIntArray has exactly size squared elements`() {
        val canvas = GlyphCanvas(25)
        assertEquals(25 * 25, canvas.toIntArray().size)
    }

    @Test
    fun `line lights both endpoints and tolerates a zero-length line without throwing`() {
        val canvas = GlyphCanvas(13)
        canvas.line(2f, 2f, 9f, 9f, 200)
        assertTrue("start point should be lit", canvas.pixels[2 * 13 + 2] > 0)
        assertTrue("end point should be lit", canvas.pixels[9 * 13 + 9] > 0)

        canvas.line(5f, 5f, 5f, 5f, 200) // zero-length - must not divide by zero or throw
    }

    @Test
    fun `line out of bounds does not throw`() {
        val canvas = GlyphCanvas(13)
        canvas.line(-5f, -5f, 50f, 50f, 200)
    }
}
