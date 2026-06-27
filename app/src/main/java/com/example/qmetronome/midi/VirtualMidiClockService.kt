package com.example.qmetronome.midi

import android.media.midi.MidiDeviceService
import android.media.midi.MidiReceiver

/**
 * Exposes qMetronome as a virtual MIDI destination ("qMetronome Clock In") that any other app
 * on the same phone - a DAW, a looper, a sequencer - can pick as a MIDI output target. No
 * hardware or pairing required; this is the cheapest way to follow an external clock. See
 * `res/xml/midi_device_info.xml` for the declared port and the manifest entry for the required
 * `BIND_MIDI_DEVICE_SERVICE` permission.
 */
class VirtualMidiClockService : MidiDeviceService() {
    override fun onGetInputPortReceivers(): Array<MidiReceiver> =
        arrayOf(MidiClockSource.receiverFor(MidiClockSource.Source.VIRTUAL))
}
