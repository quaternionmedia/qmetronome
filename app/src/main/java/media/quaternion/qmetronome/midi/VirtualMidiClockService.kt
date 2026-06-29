package media.quaternion.qmetronome.midi

import android.media.midi.MidiDeviceService
import android.media.midi.MidiReceiver

/**
 * Exposes qMetronome as a virtual MIDI device ("qMetronome Clock") with one port in each
 * direction: other apps - a DAW, a looper, a sequencer - can pick it as a MIDI *output* target to
 * have us follow their clock (handled by [MidiClockSource]), or as a MIDI *input* source to
 * follow ours instead (handled by [MidiClockSender]). No hardware or pairing required either way.
 * See `res/xml/midi_device_info.xml` for the declared ports and the manifest entry for the
 * required `BIND_MIDI_DEVICE_SERVICE` permission.
 */
class VirtualMidiClockService : MidiDeviceService() {
    override fun onGetInputPortReceivers(): Array<MidiReceiver> =
        arrayOf(MidiClockSource.receiverFor(MidiClockSource.Source.VIRTUAL))

    override fun onCreate() {
        super.onCreate()
        // Our declared output-port receiver, which MidiClockSender writes clock bytes into to
        // reach whatever app has opened this device as a MIDI input source.
        getOutputPortReceivers().forEach(MidiClockSender::addDestination)
    }

    override fun onClose() {
        getOutputPortReceivers().forEach(MidiClockSender::removeDestination)
        super.onClose()
    }
}
