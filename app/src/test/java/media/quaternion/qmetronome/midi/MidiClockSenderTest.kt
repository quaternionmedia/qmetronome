package media.quaternion.qmetronome.midi

import android.media.midi.MidiReceiver
import media.quaternion.qmetronome.engine.MetronomeEngine
import org.junit.After
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
}
