package media.quaternion.qmetronome.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Discovers USB MIDI devices and connects them in either direction - following a device's clock
 * (its output port feeds [MidiClockSource]) or sending our clock to it (its input port becomes a
 * [MidiClockSender] destination). The two are independent: you can follow one device while
 * sending to another, both to the same device, or just one direction - `MidiManager` handles the
 * one-time USB permission prompt itself either way, no manual `UsbManager` permission code
 * needed.
 *
 * Following and sending *to the same device* is allowed, not blocked - on real MIDI hardware
 * that's normal (separate IN/OUT jacks, no inherent loop). It would only become a problem if
 * that specific device has MIDI Thru/echo enabled, looping its received clock back out its own
 * output - a device setting, not something this app can detect, so the UI surfaces it as a
 * heads-up rather than silently allowing or blocking it. This combination isn't something I can
 * verify without real hardware in hand - see `docs/usb-midi-test-plan.md`.
 */
class UsbMidiConnector(context: Context) {

    private val midiManager = context.getSystemService(MidiManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var followingDevice: MidiDevice? = null
    private var sendingDevice: MidiDevice? = null
    private var sendingPort: MidiInputPort? = null

    private val _availableDevices = MutableStateFlow<List<MidiDeviceInfo>>(emptyList())
    val availableDevices: StateFlow<List<MidiDeviceInfo>> = _availableDevices.asStateFlow()

    // Identity is tracked by MidiDeviceInfo.id, not display name - two devices can legitimately
    // report the same (or an empty, fallback) name, and name-matching would make the UI show the
    // wrong row as connected in that case.
    private val _followingDeviceId = MutableStateFlow<Int?>(null)
    val followingDeviceId: StateFlow<Int?> = _followingDeviceId.asStateFlow()

    private val _sendingDeviceId = MutableStateFlow<Int?>(null)
    val sendingDeviceId: StateFlow<Int?> = _sendingDeviceId.asStateFlow()

    fun refreshDevices() {
        _availableDevices.value = midiManager
            ?.getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM)
            ?.filter { it.type == MidiDeviceInfo.TYPE_USB }
            .orEmpty()
    }

    /** Follows this device's clock - its output port feeds [MidiClockSource]. */
    fun connectForFollowing(deviceInfo: MidiDeviceInfo) {
        val manager = midiManager ?: return
        manager.openDevice(deviceInfo, { device ->
            if (device == null) return@openDevice
            val outputPort = device.openOutputPort(0)
            if (outputPort == null) {
                closeQuietly(device)
                return@openDevice
            }
            outputPort.connect(MidiClockSource.receiverFor(MidiClockSource.Source.USB))
            closeQuietly(followingDevice)
            followingDevice = device
            _followingDeviceId.value = deviceInfo.id
        }, mainHandler)
    }

    fun disconnectFollowing() {
        closeQuietly(followingDevice)
        followingDevice = null
        _followingDeviceId.value = null
    }

    /** Sends our clock to this device - its input port becomes a [MidiClockSender] destination. */
    fun connectForSending(deviceInfo: MidiDeviceInfo) {
        val manager = midiManager ?: return
        manager.openDevice(deviceInfo, { device ->
            if (device == null) return@openDevice
            val inputPort = device.openInputPort(0)
            if (inputPort == null) {
                closeQuietly(device)
                return@openDevice
            }
            disconnectSending()
            MidiClockSender.addDestination(inputPort)
            sendingPort = inputPort
            sendingDevice = device
            _sendingDeviceId.value = deviceInfo.id
        }, mainHandler)
    }

    fun disconnectSending() {
        sendingPort?.let { MidiClockSender.removeDestination(it) }
        sendingPort = null
        closeQuietly(sendingDevice)
        sendingDevice = null
        _sendingDeviceId.value = null
    }

    fun displayName(info: MidiDeviceInfo): String {
        val properties = info.properties
        return properties.getString(MidiDeviceInfo.PROPERTY_NAME)
            ?: properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT)
            ?: "USB MIDI device"
    }

    /** [MidiDevice.close] declares `throws IOException` - a closed/unplugged device shouldn't be
     * able to crash whatever UI action triggered the disconnect. */
    private fun closeQuietly(device: MidiDevice?) {
        try {
            device?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Failed closing a USB MIDI device", e)
        }
    }

    private companion object {
        const val TAG = "UsbMidiConnector"
    }
}
