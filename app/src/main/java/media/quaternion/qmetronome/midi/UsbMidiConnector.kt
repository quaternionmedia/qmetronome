package media.quaternion.qmetronome.midi

import android.content.Context
import android.hardware.usb.UsbDevice
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
 *
 * A process-wide singleton, not scoped to the settings UI - [attach] registers a
 * [MidiManager.DeviceCallback] so a starred device (see [StarredMidiDevices]) reconnects and
 * restores its last follow/send state the moment it reappears on the bus, even while Settings
 * isn't open.
 */
object UsbMidiConnector {

    private var midiManager: MidiManager? = null
    private var starredDevices: StarredMidiDevices? = null
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private var followingDevice: MidiDevice? = null
    private var followingDeviceInfo: MidiDeviceInfo? = null
    private var sendingDevice: MidiDevice? = null
    private var sendingPort: MidiInputPort? = null
    private var sendingDeviceInfo: MidiDeviceInfo? = null

    private val _availableDevices = MutableStateFlow<List<MidiDeviceInfo>>(emptyList())
    val availableDevices: StateFlow<List<MidiDeviceInfo>> = _availableDevices.asStateFlow()

    // Identity is tracked by MidiDeviceInfo.id, not display name - two devices can legitimately
    // report the same (or an empty, fallback) name, and name-matching would make the UI show the
    // wrong row as connected in that case.
    private val _followingDeviceId = MutableStateFlow<Int?>(null)
    val followingDeviceId: StateFlow<Int?> = _followingDeviceId.asStateFlow()

    private val _sendingDeviceId = MutableStateFlow<Int?>(null)
    val sendingDeviceId: StateFlow<Int?> = _sendingDeviceId.asStateFlow()

    private val _starredDeviceKeys = MutableStateFlow<Set<String>>(emptySet())
    val starredDeviceKeys: StateFlow<Set<String>> = _starredDeviceKeys.asStateFlow()

    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo) {
            refreshDevices()
            maybeAutoReconnect(device)
        }

        override fun onDeviceRemoved(device: MidiDeviceInfo) {
            refreshDevices()
            if (device.id == followingDeviceInfo?.id) {
                followingDevice = null
                followingDeviceInfo = null
                _followingDeviceId.value = null
            }
            if (device.id == sendingDeviceInfo?.id) {
                sendingPort = null
                sendingDevice = null
                sendingDeviceInfo = null
                _sendingDeviceId.value = null
            }
        }
    }

    /** Wires up discovery and starred-device auto-reconnect. Safe to call more than once. */
    fun attach(context: Context) {
        if (midiManager != null) return
        val manager = context.applicationContext.getSystemService(MidiManager::class.java) ?: return
        midiManager = manager
        starredDevices = StarredMidiDevices(context.applicationContext)
        _starredDeviceKeys.value = starredDevices?.starredKeys().orEmpty()
        manager.registerDeviceCallback(deviceCallback, mainHandler)
        refreshDevices()
        // registerDeviceCallback doesn't replay devices that were already connected before this
        // call - catch any starred device that was already plugged in when the app started.
        _availableDevices.value.forEach(::maybeAutoReconnect)
    }

    fun refreshDevices() {
        _availableDevices.value = midiManager
            ?.getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM)
            ?.filter { it.type == MidiDeviceInfo.TYPE_USB }
            .orEmpty()
    }

    /** A stable identity for a device that survives reconnects, unlike [MidiDeviceInfo.id], which
     * the system reassigns every time a device is opened. Vendor/product IDs live on the
     * [UsbDevice] parcelable exposed via [MidiDeviceInfo.PROPERTY_USB_DEVICE] - `MidiDeviceInfo`
     * itself has no vendor/product ID properties of its own. */
    fun deviceKey(info: MidiDeviceInfo): String {
        val properties = info.properties
        val usbDevice = properties.getParcelable(MidiDeviceInfo.PROPERTY_USB_DEVICE, UsbDevice::class.java)
        val vendorId = usbDevice?.vendorId ?: -1
        val productId = usbDevice?.productId ?: -1
        val serial = properties.getString(MidiDeviceInfo.PROPERTY_SERIAL_NUMBER)
        return deviceKey(vendorId, productId, serial ?: displayName(info))
    }

    fun deviceKey(vendorId: Int, productId: Int, serialOrName: String): String =
        "$vendorId:$productId:$serialOrName"

    fun isStarred(info: MidiDeviceInfo): Boolean = starredDevices?.isStarred(deviceKey(info)) == true

    /** Stars or unstars a device. Starring captures whichever connection(s) are active right now
     * as what gets automatically restored next time it reappears; unstarring forgets that state. */
    fun toggleStar(info: MidiDeviceInfo) {
        val store = starredDevices ?: return
        val key = deviceKey(info)
        val starred = !store.isStarred(key)
        store.setStarred(key, starred)
        if (starred) {
            store.setDesiredFollow(key, info.id == _followingDeviceId.value)
            store.setDesiredSend(key, info.id == _sendingDeviceId.value)
        }
        _starredDeviceKeys.value = store.starredKeys()
    }

    private fun maybeAutoReconnect(info: MidiDeviceInfo) {
        val store = starredDevices ?: return
        if (info.type != MidiDeviceInfo.TYPE_USB) return
        val key = deviceKey(info)
        if (!store.isStarred(key)) return
        if (store.desiredFollow(key) && info.id != _followingDeviceId.value) connectForFollowing(info)
        if (store.desiredSend(key) && info.id != _sendingDeviceId.value) connectForSending(info)
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
            followingDeviceInfo = deviceInfo
            _followingDeviceId.value = deviceInfo.id
            persistDesiredState(deviceInfo, following = true)
        }, mainHandler)
    }

    fun disconnectFollowing() {
        followingDeviceInfo?.let { persistDesiredState(it, following = false) }
        closeQuietly(followingDevice)
        followingDevice = null
        followingDeviceInfo = null
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
            MidiActionSender.addDestination(inputPort)
            sendingPort = inputPort
            sendingDevice = device
            sendingDeviceInfo = deviceInfo
            _sendingDeviceId.value = deviceInfo.id
            persistDesiredState(deviceInfo, sending = true)
        }, mainHandler)
    }

    fun disconnectSending() {
        sendingDeviceInfo?.let { persistDesiredState(it, sending = false) }
        sendingPort?.let {
            MidiClockSender.removeDestination(it)
            MidiActionSender.removeDestination(it)
        }
        sendingPort = null
        closeQuietly(sendingDevice)
        sendingDevice = null
        sendingDeviceInfo = null
        _sendingDeviceId.value = null
    }

    private fun persistDesiredState(info: MidiDeviceInfo, following: Boolean? = null, sending: Boolean? = null) {
        val store = starredDevices ?: return
        val key = deviceKey(info)
        if (!store.isStarred(key)) return
        following?.let { store.setDesiredFollow(key, it) }
        sending?.let { store.setDesiredSend(key, it) }
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

    private const val TAG = "UsbMidiConnector"
}
