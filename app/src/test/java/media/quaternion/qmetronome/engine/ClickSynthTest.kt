package media.quaternion.qmetronome.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

class ClickSynthTest {

    private val sampleRateHz = 44_100

    @Test
    fun `buffer length matches the requested duration`() {
        val spec = ClickSpec(ClickWaveform.SINE, frequencyHz = 440f, durationMs = 50, gain = 1f)
        val samples = ClickSynth.render(spec, sampleRateHz)
        val expectedSamples = (50 / 1000f * sampleRateHz).roundToInt()
        assertEquals(expectedSamples, samples.size)
    }

    @Test
    fun `envelope starts and ends near zero to avoid a pop`() {
        val spec = ClickSpec(ClickWaveform.SQUARE, frequencyHz = 220f, durationMs = 80, gain = 1f)
        val samples = ClickSynth.render(spec, sampleRateHz)
        val firstFewPeak = samples.take(5).maxOf { abs(it.toInt()) }
        val lastFewPeak = samples.takeLast(5).maxOf { abs(it.toInt()) }
        val tenPercent = Short.MAX_VALUE / 10

        assertTrue("first samples should start near zero, was $firstFewPeak", firstFewPeak < tenPercent)
        assertTrue("last samples should end near zero, was $lastFewPeak", lastFewPeak < tenPercent)
    }

    @Test
    fun `peak amplitude scales with gain`() {
        val loud = ClickSynth.render(ClickSpec(ClickWaveform.SINE, 440f, durationMs = 50, gain = 1f), sampleRateHz)
        val quiet = ClickSynth.render(ClickSpec(ClickWaveform.SINE, 440f, durationMs = 50, gain = 0.25f), sampleRateHz)
        val loudPeak = loud.maxOf { abs(it.toInt()) }
        val quietPeak = quiet.maxOf { abs(it.toInt()) }

        // Waveform and envelope are identical between the two renders - only gain differs - so
        // the peaks should scale (almost) exactly by that ratio, modulo Short rounding.
        assertEquals(loudPeak * 0.25, quietPeak.toDouble(), loudPeak * 0.02)
    }

    @Test
    fun `noise waveform stays within 16-bit amplitude bounds`() {
        val spec = ClickSpec(ClickWaveform.NOISE, frequencyHz = 2000f, durationMs = 40, gain = 0.9f)
        val samples = ClickSynth.render(spec, sampleRateHz)

        assertTrue(samples.all { abs(it.toInt()) <= Short.MAX_VALUE })
    }

    @Test
    fun `every waveform renders a non-empty, non-silent buffer`() {
        ClickWaveform.entries.forEach { waveform ->
            val spec = ClickSpec(waveform, frequencyHz = 440f, durationMs = 60, gain = 1f)
            val samples = ClickSynth.render(spec, sampleRateHz)
            assertTrue("$waveform produced an empty buffer", samples.isNotEmpty())
            assertTrue("$waveform never made any audible sound", samples.any { it != 0.toShort() })
        }
    }
}
