package media.quaternion.qmetronome.midi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression coverage for the "beat fires way faster than the displayed BPM" bug: a virtual
 * MIDI cable and a USB device sending clock at the same time were both feeding the same tick
 * counter with no isolation, so the beat fired after whatever the *combined* tick rate worked
 * out to. [MidiClockSource] now arbitrates to a single active source at a time - these tests
 * pin that behavior down.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MidiClockSourceTest {

    private var beatCount = 0
    private var lastMeasuredBpm: Float? = null

    @Before
    fun setUp() {
        MidiClockSource.resetForTesting()
        beatCount = 0
        lastMeasuredBpm = null
        MidiClockSource.start(CoroutineScope(Dispatchers.Unconfined), 120f) { _, bpm ->
            beatCount++
            lastMeasuredBpm = bpm
        }
    }

    @After
    fun tearDown() {
        MidiClockSource.stop()
        MidiClockSource.resetForTesting()
    }

    private fun sendTicks(source: MidiClockSource.Source, count: Int, startNanos: Long = 0L, stepNanos: Long = 20_833_333L) {
        val receiver = MidiClockSource.receiverFor(source)
        val message = byteArrayOf(0xF8.toByte())
        var t = startNanos
        repeat(count) {
            receiver.send(message, 0, 1, t)
            t += stepNanos
        }
    }

    @Test
    fun `exactly one beat fires per 24 ticks from a single source`() {
        sendTicks(MidiClockSource.Source.USB, 23)
        assertEquals(0, beatCount)

        sendTicks(MidiClockSource.Source.USB, 1, startNanos = 23 * 20_833_333L)
        assertEquals(1, beatCount)
    }

    @Test
    fun `a second source is ignored while the first is still actively sending`() {
        // 12 ticks from VIRTUAL, then 12 from USB with no gap - if both counted, this would be
        // 24 total ticks and fire a beat. It must not, because USB's ticks should be ignored
        // while VIRTUAL is still the active source.
        sendTicks(MidiClockSource.Source.VIRTUAL, 12)
        sendTicks(MidiClockSource.Source.USB, 12, startNanos = 12 * 20_833_333L)

        assertEquals(0, beatCount)
        assertEquals(MidiClockSource.Source.VIRTUAL, MidiClockSource.activeSource)
    }

    @Test
    fun `a different source can take over once the active one goes quiet`() {
        sendTicks(MidiClockSource.Source.VIRTUAL, 5)
        assertEquals(MidiClockSource.Source.VIRTUAL, MidiClockSource.activeSource)

        Thread.sleep(600) // over SOURCE_TAKEOVER_SILENCE_MS

        sendTicks(MidiClockSource.Source.USB, 1)
        assertEquals(MidiClockSource.Source.USB, MidiClockSource.activeSource)
    }

    @Test
    fun `stop clears the beat callback so late ticks are silently dropped`() {
        MidiClockSource.stop()
        sendTicks(MidiClockSource.Source.USB, 24)
        assertEquals(0, beatCount)
    }

    @Test
    fun `measured bpm is null until at least two ticks have established an interval`() {
        assertNull(lastMeasuredBpm)
    }
}
