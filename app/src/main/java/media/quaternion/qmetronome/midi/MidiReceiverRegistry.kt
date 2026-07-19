package media.quaternion.qmetronome.midi

import android.media.midi.MidiReceiver
import java.util.concurrent.CopyOnWriteArraySet

/**
 * The "who gets outgoing MIDI bytes" bookkeeping shared by [MidiClockSender] and
 * [MidiActionSender] as code, not as a shared runtime instance - each owns its own private
 * registry, so registering a destination with one doesn't imply the other, and neither's existing
 * behavior needs to change to pick this up. [CopyOnWriteArraySet] because writes (a device
 * connecting/disconnecting, e.g. from [UsbMidiConnector]) are rare and reads (iterating on every
 * outgoing message) are frequent and must never risk a `ConcurrentModificationException` racing a
 * connect/disconnect - the same reasoning [MidiClockSender]'s own destination set already used
 * before this was factored out.
 */
class MidiReceiverRegistry {
    private val destinations = CopyOnWriteArraySet<MidiReceiver>()

    fun add(receiver: MidiReceiver) {
        destinations.add(receiver)
    }

    fun remove(receiver: MidiReceiver) {
        destinations.remove(receiver)
    }

    val isEmpty: Boolean get() = destinations.isEmpty()

    fun forEach(action: (MidiReceiver) -> Unit) {
        for (destination in destinations) action(destination)
    }
}
