package media.quaternion.qmetronome.midi

import android.content.Context
import android.media.midi.MidiReceiver
import android.util.Log
import java.util.concurrent.CopyOnWriteArraySet
import media.quaternion.qmetronome.engine.MetronomeEngine
import media.quaternion.qmetronome.engine.MetronomeSettings
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Sends this app's tempo out as MIDI Clock, the mirror image of [MidiClockSource]: instead of
 * following an external clock, other apps (or, once a destination is registered for it, USB
 * gear) can follow *us*. Always derives from [MetronomeEngine.state] regardless of where that
 * tempo actually came from - if the engine is currently following an external clock itself, this
 * naturally becomes a repeater for it, with no special-casing needed.
 *
 * Destinations are anything that can receive raw MIDI bytes - [VirtualMidiClockService] registers
 * its declared output port's receiver here in `onCreate()`/unregisters in `onClose()`, and a USB
 * destination would be exactly the same shape (a `MidiInputPort`, which is also a `MidiReceiver`).
 */
object MidiClockSender {

    @Volatile
    private var settings: MetronomeSettings? = null

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Clock-out loop failed", throwable)
    }
    private val scope = CoroutineScope(SupervisorJob() + exceptionHandler)

    // addDestination()/removeDestination() are called from the main thread (UI button presses,
    // service onCreate()/onClose()), while sendByte() iterates this from the tick loop's
    // background dispatcher - a plain MutableSet would risk a ConcurrentModificationException if
    // a destination connects/disconnects mid-tick. CopyOnWriteArraySet is built for exactly this
    // shape: rare writes, frequent lock-free reads.
    private val destinations = CopyOnWriteArraySet<MidiReceiver>()

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private var tickJob: Job? = null

    // Written from the tick loop's background dispatcher every iteration, but also read/written
    // from the main thread in stopLoop() (setEnabled(false) can race a still-cancelling tick
    // job) - @Volatile for cross-thread visibility, same convention MetronomeEngine uses for its
    // own cross-thread flags.
    @Volatile
    private var wasPlaying = false

    /** Loads the persisted on/off state. Safe to call multiple times. */
    fun attach(context: Context) {
        if (settings != null) return
        val store = MetronomeSettings(context.applicationContext)
        settings = store
        if (store.clockOutEnabled) setEnabled(true)
    }

    fun addDestination(receiver: MidiReceiver) {
        destinations.add(receiver)
    }

    fun removeDestination(receiver: MidiReceiver) {
        destinations.remove(receiver)
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled == _enabled.value) return
        _enabled.value = enabled
        settings?.clockOutEnabled = enabled
        if (enabled) startLoop() else stopLoop()
    }

    private fun startLoop() {
        wasPlaying = false
        tickJob?.cancel()
        tickJob = scope.launch {
            var nextTickNanos = System.nanoTime()
            while (isActive && _enabled.value) {
                val state = MetronomeEngine.state.value
                if (state.isPlaying != wasPlaying) {
                    sendByte(if (state.isPlaying) START else STOP)
                    wasPlaying = state.isPlaying
                    nextTickNanos = System.nanoTime()
                }
                if (state.isPlaying) {
                    val now = System.nanoTime()
                    val delayMillis = (nextTickNanos - now) / 1_000_000
                    if (delayMillis > 0) delay(delayMillis)
                    sendByte(CLOCK_TICK)
                    val intervalNanos = (60_000_000_000.0 / (state.bpm.coerceAtLeast(1f) * TICKS_PER_BEAT)).toLong()
                    nextTickNanos += intervalNanos
                    // If we fell badly behind (e.g. process was suspended), resync instead of
                    // firing a burst of catch-up ticks - same approach as InternalClockSource.
                    val behindBy = System.nanoTime() - nextTickNanos
                    if (behindBy > intervalNanos) {
                        nextTickNanos = System.nanoTime() + intervalNanos
                    }
                } else {
                    // Not playing - just wait for a state change rather than ticking.
                    delay(IDLE_POLL_MS)
                }
            }
        }
    }

    private fun stopLoop() {
        tickJob?.cancel()
        tickJob = null
        if (wasPlaying) sendByte(STOP)
        wasPlaying = false
    }

    private fun sendByte(byte: Int) {
        if (destinations.isEmpty()) return
        val message = byteArrayOf(byte.toByte())
        val timestamp = System.nanoTime()
        for (destination in destinations) {
            try {
                destination.send(message, 0, 1, timestamp)
            } catch (e: Exception) {
                Log.w(TAG, "Failed sending to a clock-out destination", e)
            }
        }
    }

    private const val TAG = "MidiClockSender"
    private const val TICKS_PER_BEAT = 24
    private const val IDLE_POLL_MS = 50L

    // MIDI 1.0 System Real-Time messages.
    private const val CLOCK_TICK = 0xF8
    private const val START = 0xFA
    private const val STOP = 0xFC
}
