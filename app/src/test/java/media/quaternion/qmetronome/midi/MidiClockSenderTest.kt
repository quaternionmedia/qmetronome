package media.quaternion.qmetronome.midi

import android.media.midi.MidiReceiver
import media.quaternion.qmetronome.engine.ClockTimingMode
import media.quaternion.qmetronome.engine.MetronomeEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MidiClockSenderTest {

    private val receivedBytes = mutableListOf<Int>()
    private val fakeDestination = object : MidiReceiver() {
        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
            for (i in 0 until count) receivedBytes.add(msg[offset + i].toInt() and 0xFF)
        }
    }

    @Before
    fun setUp() {
        MetronomeEngine.resetForTesting()
        MetronomeEngine.attach(RuntimeEnvironment.getApplication())
        receivedBytes.clear()
        MidiClockSender.addDestination(fakeDestination)
    }

    @After
    fun tearDown() {
        MidiClockSender.setEnabled(false)
        MidiClockSender.setTimingMode(ClockTimingMode.MECHANICAL)
        MidiClockSender.removeDestination(fakeDestination)
        MetronomeEngine.resetForTesting()
    }

    @Test
    fun `enabling without playing sends nothing`() {
        MidiClockSender.setEnabled(true)
        Thread.sleep(100)
        assertTrue(receivedBytes.isEmpty())
    }

    @Test
    fun `starting the engine while enabled sends Start then clock ticks`() {
        MidiClockSender.setEnabled(true)
        MetronomeEngine.setBpm(240f) // fast - ticks arrive quickly, keeps the test short
        MetronomeEngine.start()
        Thread.sleep(150)

        assertTrue("expected a Start (0xFA) byte", receivedBytes.contains(0xFA))
        assertTrue("expected at least one clock tick (0xF8)", receivedBytes.count { it == 0xF8 } > 0)
    }

    @Test
    fun `stopping the engine sends a Stop byte`() {
        MidiClockSender.setEnabled(true)
        MetronomeEngine.setBpm(240f)
        MetronomeEngine.start()
        Thread.sleep(80)
        MetronomeEngine.stop()
        Thread.sleep(80)

        assertTrue("expected a Stop (0xFC) byte", receivedBytes.contains(0xFC))
    }

    @Test
    fun `disabling stops sending`() {
        MidiClockSender.setEnabled(true)
        MetronomeEngine.setBpm(240f)
        MetronomeEngine.start()
        Thread.sleep(80)

        MidiClockSender.setEnabled(false)
        receivedBytes.clear()
        Thread.sleep(100)

        assertTrue(receivedBytes.isEmpty())
    }

    @Test
    fun `a removed destination receives nothing further`() {
        MidiClockSender.removeDestination(fakeDestination)
        MidiClockSender.setEnabled(true)
        MetronomeEngine.setBpm(240f)
        MetronomeEngine.start()
        Thread.sleep(100)

        assertTrue(receivedBytes.isEmpty())
    }

    @Test
    fun `clock tick rate approximately matches configured bpm`() {
        // At 240 BPM: 24 ticks/beat × 4 beats/sec = 96 ticks/sec → ~48 ticks in 500ms.
        MidiClockSender.setEnabled(true)
        MetronomeEngine.setBpm(240f)
        MetronomeEngine.start()
        Thread.sleep(500)

        val tickCount = receivedBytes.count { it == 0xF8 }
        assertTrue("expected ~48 ticks at 240 BPM in 500ms, got $tickCount", tickCount in 40..56)
    }

    @Test
    fun `a manual bpm change reaches clock-out immediately, not smoothed`() {
        // TempoStabilizer only engages while following an external clock (see
        // MidiClockSender.effectiveBpm) - a deliberate, user-driven tempo change is the common
        // case and must never be damped/delayed the way a noisy measured tempo would be.
        MidiClockSender.setEnabled(true)
        MetronomeEngine.setBpm(60f)
        MetronomeEngine.start()
        Thread.sleep(60)

        MetronomeEngine.setBpm(300f) // a big, sudden, deliberate jump
        receivedBytes.clear()
        Thread.sleep(150)

        // At an unsmoothed 300 BPM: 24 ticks/beat × 5 beats/sec = 120 ticks/sec → ~18 ticks in
        // 150ms. If this were damped toward the old 60 BPM instead, far fewer ticks would arrive.
        val tickCount = receivedBytes.count { it == 0xF8 }
        assertTrue(
            "expected the new tempo to apply immediately, got only $tickCount ticks in 150ms",
            tickCount > 10,
        )
    }

    @Test
    fun `timing mode defaults to Mechanical and round-trips via setTimingMode`() {
        assertEquals(ClockTimingMode.MECHANICAL, MidiClockSender.timingMode.value)

        MidiClockSender.setTimingMode(ClockTimingMode.ORGANIC)

        assertEquals(ClockTimingMode.ORGANIC, MidiClockSender.timingMode.value)
    }

    @Test
    fun `organic mode still ticks at roughly the configured bpm when not following an external clock`() {
        // Organic mode only changes whether TempoStabilizer/phase-resync engage while *following*
        // an external clock - a plain internal-clock session should look the same either way.
        MidiClockSender.setTimingMode(ClockTimingMode.ORGANIC)
        MidiClockSender.setEnabled(true)
        MetronomeEngine.setBpm(240f)
        MetronomeEngine.start()
        Thread.sleep(500)

        val tickCount = receivedBytes.count { it == 0xF8 }
        assertTrue("expected ~48 ticks at 240 BPM in 500ms, got $tickCount", tickCount in 40..56)
    }

    @Test
    fun `mechanical mode still ticks at roughly the configured bpm at a slow tempo`() {
        // At 60 BPM: 24 ticks/beat x 1 beat/sec = 24 ticks/sec -> ~12 ticks in 500ms. Slow tempos
        // are exactly the case where per-outgoing-tick resync polling had the worst (tempo-
        // scaling) detection latency before RESYNC_POLL_NANOS - confirm the finer-grained polling
        // loop doesn't itself throw off the tick rate.
        MidiClockSender.setEnabled(true)
        MetronomeEngine.setBpm(60f)
        MetronomeEngine.start()
        Thread.sleep(500)

        val tickCount = receivedBytes.count { it == 0xF8 }
        assertTrue("expected ~12 ticks at 60 BPM in 500ms, got $tickCount", tickCount in 9..15)
    }
}
