package com.example.qmetronome.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Discovers USB MIDI devices and routes whatever they send into [MidiClockSource]. `MidiManager`
 * handles the one-time USB permission prompt itself - no manual `UsbManager` permission code
 * needed.
 */
class UsbMidiConnector(context: Context) {

    private val midiManager = context.getSystemService(MidiManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var connectedDevice: MidiDevice? = null

    private val _availableDevices = MutableStateFlow<List<MidiDeviceInfo>>(emptyList())
    val availableDevices: StateFlow<List<MidiDeviceInfo>> = _availableDevices.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    fun refreshDevices() {
        _availableDevices.value = midiManager
            ?.getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM)
            ?.filter { it.type == MidiDeviceInfo.TYPE_USB }
            .orEmpty()
    }

    fun connect(deviceInfo: MidiDeviceInfo) {
        val manager = midiManager ?: return
        manager.openDevice(deviceInfo, { device ->
            if (device == null) return@openDevice
            val outputPort = device.openOutputPort(0)
            if (outputPort == null) {
                device.close()
                return@openDevice
            }
            outputPort.connect(MidiClockSource.receiverFor(MidiClockSource.Source.USB))
            connectedDevice?.close()
            connectedDevice = device
            _connectedDeviceName.value = displayName(deviceInfo)
        }, mainHandler)
    }

    fun disconnect() {
        connectedDevice?.close()
        connectedDevice = null
        _connectedDeviceName.value = null
    }

    fun displayName(info: MidiDeviceInfo): String {
        val properties = info.properties
        return properties.getString(MidiDeviceInfo.PROPERTY_NAME)
            ?: properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT)
            ?: "USB MIDI device"
    }
}
