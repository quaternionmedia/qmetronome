package media.quaternion.qmetronome.midi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Coverage for [UsbMidiConnector.deviceKey] - the stable vendor/product/serial identity that the
 * starring feature keys off, since `MidiDeviceInfo.id` itself is reassigned by the platform on
 * every reconnect and can't be used to recognize "this is the same device" across an
 * unplug/replug. Connecting/auto-reconnecting against real `MidiManager`/`MidiDeviceInfo`
 * instances needs actual USB hardware - see `docs/usb-midi-test-plan.md`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UsbMidiConnectorTest {

    @Test
    fun `device key combines vendor, product and serial`() {
        assertEquals("1:2:ABC123", UsbMidiConnector.deviceKey(1, 2, "ABC123"))
    }

    @Test
    fun `different serials produce different keys for the same vendor and product`() {
        val a = UsbMidiConnector.deviceKey(1, 2, "ABC123")
        val b = UsbMidiConnector.deviceKey(1, 2, "XYZ789")
        assertNotEquals(a, b)
    }

    @Test
    fun `different vendor or product ids produce different keys for the same serial`() {
        val a = UsbMidiConnector.deviceKey(1, 2, "ABC123")
        val b = UsbMidiConnector.deviceKey(9, 2, "ABC123")
        assertNotEquals(a, b)
    }
}
