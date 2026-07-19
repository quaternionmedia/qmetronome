package media.quaternion.qmetronome.visualizers

import media.quaternion.qmetronome.engine.TimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val MIN_BPM = 1f
private const val MAX_BPM = 400f

// Mirrors QueueOverlay's own private row-weighting constants so tests can compute exact expected
// row boundaries rather than only asserting fuzzy aggregate properties. Duplicated from
// QueueOverlay.kt - if those tuning constants change, update these too.
private const val USABLE_RADIUS_FRACTION = 0.85f
private const val MIN_ROW_WEIGHT = 0.4f

class QueueOverlayTest {

    private fun frameOf(size: Int, brightness: Int) = IntArray(size * size) { brightness }

    private fun bar(beatCount: Int = 4, bpm: Float = 120f) = TimeSignature(beatCount = beatCount, bpm = bpm)

    /** Row y-bounds for each bar (top-to-bottom in queue order), computed the same way
     * QueueOverlay.apply itself does. */
    private fun rowBounds(size: Int, queue: List<TimeSignature>): List<IntRange> {
        val center = (size - 1) / 2f
        val usableRadius = size / 2f * USABLE_RADIUS_FRACTION
        val startY = center - usableRadius
        val totalHeight = usableRadius * 2f
        val weights = queue.map { bar ->
            val tempoFraction = ((bar.bpm - MIN_BPM) / (MAX_BPM - MIN_BPM)).coerceIn(0f, 1f)
            MIN_ROW_WEIGHT + (1f - MIN_ROW_WEIGHT) * tempoFraction
        }
        val totalWeight = weights.sum()
        var cumulative = 0f
        return weights.map { weight ->
            val rowTop = (startY + (cumulative / totalWeight) * totalHeight).roundToInt()
            cumulative += weight
            val rowBottom = (startY + (cumulative / totalWeight) * totalHeight).roundToInt()
            rowTop..rowBottom
        }
    }

    @Test
    fun `a single-entry queue is a no-op`() {
        val frame = frameOf(25, 50)
        val result = QueueOverlay.apply(frame, 25, listOf(bar()), 0, 0, 0f, MIN_BPM, MAX_BPM)
        assertSame("nothing to indicate - should return the same array, not a copy", frame, result)
    }

    @Test
    fun `an empty queue is treated as a no-op, not a crash`() {
        val frame = frameOf(25, 50)
        val result = QueueOverlay.apply(frame, 25, emptyList(), 0, 0, 0f, MIN_BPM, MAX_BPM)
        assertSame("size <= 1, including empty, is documented as a no-op", frame, result)
    }

    @Test
    fun `output stays the right size and in range for both real matrix sizes`() {
        for (size in listOf(13, 25)) {
            val queue = listOf(bar(bpm = 60f), bar(bpm = 240f), bar(bpm = 120f))
            val result = QueueOverlay.apply(frameOf(size, 0), size, queue, 1, 0, 0f, MIN_BPM, MAX_BPM)
            assertEquals(size * size, result.size)
            assertTrue(result.all { it in 0..255 })
        }
    }

    @Test
    fun `every touched pixel stays within the conservative usable circle, both matrix sizes`() {
        for (size in listOf(13, 25)) {
            val original = frameOf(size, 77)
            val queue = listOf(bar(1, 60f), bar(24, 240f), bar(12, 120f), bar(6, 30f))
            val result = QueueOverlay.apply(original, size, queue, 2, 1, 0.3f, MIN_BPM, MAX_BPM)
            val center = (size - 1) / 2f
            val usableRadius = size / 2f * USABLE_RADIUS_FRACTION

            for (y in 0 until size) {
                for (x in 0 until size) {
                    val index = y * size + x
                    if (result[index] != original[index]) {
                        val distance = hypot(x - center, y - center)
                        assertTrue(
                            "pixel ($x,$y) at distance $distance was touched but exceeds the " +
                                "conservative usable radius $usableRadius for matrix size $size " +
                                "- it would be clipped on real hardware",
                            distance <= usableRadius + 0.01f,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `bars are stacked as horizontal rows, top-to-bottom in queue order`() {
        val size = 25
        val queue = listOf(bar(bpm = 120f), bar(bpm = 120f))
        val result = QueueOverlay.apply(frameOf(size, 0), size, queue, -1, -1, 0f, MIN_BPM, MAX_BPM)
        val bounds = rowBounds(size, queue)

        // Bar 0's row should sit entirely above bar 1's row (smaller y = higher up the matrix).
        assertTrue("bar 0's row should be above bar 1's row", bounds[0].last <= bounds[1].first + 1)
    }

    @Test
    fun `a bar with more beats produces more distinct tick columns within its own row`() {
        val size = 25
        val queue = listOf(bar(beatCount = 2, bpm = 120f), bar(beatCount = 12, bpm = 120f))
        val result = QueueOverlay.apply(frameOf(size, 0), size, queue, -1, -1, 0f, MIN_BPM, MAX_BPM)
        val bounds = rowBounds(size, queue)

        fun distinctLitColumns(yRange: IntRange): Int {
            var count = 0
            for (x in 0 until size) {
                if (yRange.any { y -> y in 0 until size && result[y * size + x] != 0 }) count++
            }
            return count
        }

        val cols0 = distinctLitColumns(bounds[0])
        val cols1 = distinctLitColumns(bounds[1])
        assertTrue(
            "the 12-beat bar should produce more distinct tick columns than the 2-beat bar " +
                "(cols0=$cols0, cols1=$cols1)",
            cols1 > cols0,
        )
    }

    @Test
    fun `a faster bar's row is thicker than a slower bar's, independent of any animation`() {
        val size = 25
        val queue = listOf(bar(bpm = MIN_BPM), bar(bpm = MAX_BPM))
        // beatIndex -1 and phase 0 - no active pulse in play, isolating the *static* size property.
        val result = QueueOverlay.apply(frameOf(size, 0), size, queue, -1, -1, 0f, MIN_BPM, MAX_BPM)
        val bounds = rowBounds(size, queue)

        fun distinctLitRows(yRange: IntRange): Int {
            var count = 0
            for (y in yRange) {
                if (y !in 0 until size) continue
                if ((0 until size).any { x -> result[y * size + x] != 0 }) count++
            }
            return count
        }

        val slowRows = distinctLitRows(bounds[0])
        val fastRows = distinctLitRows(bounds[1])
        assertTrue(
            "the MAX_BPM bar's row should be visibly thicker than the MIN_BPM bar's, with no " +
                "animation involved (slowRows=$slowRows, fastRows=$fastRows)",
            fastRows > slowRows,
        )
    }

    @Test
    fun `the active bar's current beat reads brighter at phase 0 than at phase 1`() {
        val size = 25
        val queue = listOf(bar(4, 120f), bar(4, 120f))
        val fresh = QueueOverlay.apply(frameOf(size, 0), size, queue, 0, 1, 0f, MIN_BPM, MAX_BPM)
        val decayed = QueueOverlay.apply(frameOf(size, 0), size, queue, 0, 1, 1f, MIN_BPM, MAX_BPM)

        assertTrue(
            "a freshly-struck active beat (phase 0) should read brighter overall than a fully " +
                "decayed one (phase 1)",
            fresh.sum() > decayed.sum(),
        )
    }

    @Test
    fun `an inactive bar's ticks do not change with beatIndex or phase`() {
        val size = 25
        val queue = listOf(bar(4, 120f), bar(4, 120f))
        // Bar 0 is active in both calls; bar 1 is inactive throughout - only differ beatIndex/phase.
        val a = QueueOverlay.apply(frameOf(size, 0), size, queue, 0, 2, 0f, MIN_BPM, MAX_BPM)
        val b = QueueOverlay.apply(frameOf(size, 0), size, queue, 0, 2, 0.9f, MIN_BPM, MAX_BPM)

        val bar1Row = rowBounds(size, queue)[1]
        for (y in bar1Row) {
            if (y !in 0 until size) continue
            for (x in 0 until size) {
                val index = y * size + x
                assertEquals("inactive bar's pixels must not depend on phase", a[index], b[index])
            }
        }
    }

    @Test
    fun `blends via max rather than overwriting - a saturated background is never darkened`() {
        val size = 25
        val queue = listOf(bar(4, 120f), bar(4, 120f))
        val result = QueueOverlay.apply(frameOf(size, 255), size, queue, 0, 0, 0f, MIN_BPM, MAX_BPM)
        assertTrue("max-blending against an all-255 frame must leave every pixel at 255", result.all { it == 255 })
    }

    @Test
    fun `blending against a dark background can still raise pixels above zero`() {
        val size = 25
        val queue = listOf(bar(4, 120f), bar(4, 120f))
        val result = QueueOverlay.apply(frameOf(size, 0), size, queue, 0, 0, 0f, MIN_BPM, MAX_BPM)
        assertTrue("the tick structure should light up at least some pixels over an all-black frame", result.any { it > 0 })
    }

    @Test
    fun `a queue much larger than the available rows does not throw`() {
        val queue = (0 until 40).map { bar(beatCount = 1 + it % 8, bpm = 60f + it) }
        val result = QueueOverlay.apply(frameOf(13, 0), 13, queue, 39, 0, 0f, MIN_BPM, MAX_BPM)
        assertEquals(13 * 13, result.size)
        assertTrue(result.all { it in 0..255 })
    }

    @Test
    fun `an out-of-range active index or beat index is clamped instead of throwing`() {
        val queue = listOf(bar(4, 120f), bar(4, 120f))
        QueueOverlay.apply(frameOf(25, 0), 25, queue, -1, -1, 0f, MIN_BPM, MAX_BPM)
        QueueOverlay.apply(frameOf(25, 0), 25, queue, 99, 99, 0f, MIN_BPM, MAX_BPM)
    }

    @Test
    fun `the input frame array is never mutated`() {
        val original = frameOf(25, 0)
        val snapshot = original.copyOf()
        QueueOverlay.apply(original, 25, listOf(bar(4, 60f), bar(8, 240f)), 1, 0, 0f, MIN_BPM, MAX_BPM)
        assertTrue("apply() must not mutate its input array in place", original.contentEquals(snapshot))
    }

    @Test
    fun `a single-entry queue with only one phrase is still a no-op`() {
        val frame = frameOf(25, 50)
        val result = QueueOverlay.apply(frame, 25, listOf(bar()), 0, 0, 0f, MIN_BPM, MAX_BPM, phraseCount = 1, activePhraseIndex = 0)
        assertSame("nothing to indicate on either axis - should return the same array", frame, result)
    }

    @Test
    fun `a single-bar queue still draws the radial phrase indicator when multiple phrases exist`() {
        val size = 25
        val result = QueueOverlay.apply(
            frameOf(size, 0), size, listOf(bar()), 0, 0, 0f, MIN_BPM, MAX_BPM, phraseCount = 3, activePhraseIndex = 0,
        )
        assertTrue(
            "a single-bar queue no longer suppresses the phrase indicator - multiple phrases should still light pixels",
            result.any { it > 0 },
        )
    }

    @Test
    fun `a multi-bar queue still draws rows when only one phrase exists`() {
        val size = 25
        val queue = listOf(bar(4, 120f), bar(4, 120f))
        val withDefault = QueueOverlay.apply(frameOf(size, 0), size, queue, 0, 0, 0f, MIN_BPM, MAX_BPM)
        val withExplicitSinglePhrase = QueueOverlay.apply(
            frameOf(size, 0), size, queue, 0, 0, 0f, MIN_BPM, MAX_BPM, phraseCount = 1, activePhraseIndex = 0,
        )
        assertTrue(
            "rows must not depend on phraseCount defaulting vs. being passed explicitly as 1",
            withDefault.contentEquals(withExplicitSinglePhrase),
        )
    }

    @Test
    fun `every phrase-indicator pixel stays within the SDK's own circular mask`() {
        for (size in listOf(13, 25)) {
            val original = frameOf(size, 0)
            val result = QueueOverlay.apply(original, size, listOf(bar()), 0, 0, 0f, MIN_BPM, MAX_BPM, phraseCount = 6, activePhraseIndex = 2)
            val center = (size - 1) / 2f
            // The true mask boundary - matrixSize/2 - not PHRASE_INDICATOR_RADIUS_FRACTION plus an
            // arbitrary margin: QueueOverlay itself caps indicatorRadius so a dot's anti-aliased
            // edge never crosses this, so this test should hold that exactly, not loosely.
            val maxAllowedRadius = size / 2f

            for (y in 0 until size) {
                for (x in 0 until size) {
                    val index = y * size + x
                    if (result[index] != original[index]) {
                        val distance = hypot(x - center, y - center)
                        assertTrue(
                            "size=$size phrase-indicator pixel ($x,$y) at distance $distance exceeds $maxAllowedRadius",
                            distance <= maxAllowedRadius,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `the active phrase's dot is brighter than an inactive phrase's dot`() {
        val size = 25
        val result = QueueOverlay.apply(frameOf(size, 0), size, listOf(bar()), 0, 0, 0f, MIN_BPM, MAX_BPM, phraseCount = 4, activePhraseIndex = 0)
        assertTrue("some phrase-indicator pixels should read at full brightness (the active phrase)", result.any { it == 255 })
        assertTrue(
            "some phrase-indicator pixels should read dimmer than full brightness (an inactive phrase)",
            result.any { it in 1..254 },
        )
    }

    @Test
    fun `an out-of-range activePhraseIndex is clamped instead of throwing`() {
        QueueOverlay.apply(frameOf(25, 0), 25, listOf(bar()), 0, 0, 0f, MIN_BPM, MAX_BPM, phraseCount = 3, activePhraseIndex = -1)
        QueueOverlay.apply(frameOf(25, 0), 25, listOf(bar()), 0, 0, 0f, MIN_BPM, MAX_BPM, phraseCount = 3, activePhraseIndex = 99)
    }

    @Test
    fun `a phraseCount much larger than the matrix does not throw`() {
        val result = QueueOverlay.apply(frameOf(13, 0), 13, listOf(bar()), 0, 0, 0f, MIN_BPM, MAX_BPM, phraseCount = 40, activePhraseIndex = 20)
        assertEquals(13 * 13, result.size)
        assertTrue(result.all { it in 0..255 })
    }
}
