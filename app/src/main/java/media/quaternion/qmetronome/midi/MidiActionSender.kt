package media.quaternion.qmetronome.midi

import android.content.Context
import android.media.midi.MidiReceiver
import android.util.Log
import media.quaternion.qmetronome.engine.ClickSound
import media.quaternion.qmetronome.engine.MetronomeSettings
import media.quaternion.qmetronome.engine.MidiActionType
import media.quaternion.qmetronome.engine.MidiBeatAction
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Sends a MIDI Note or CC message for a beat's resolved [ClickSound] (via [fireForBeat], today
 * only exercised directly by tests and available for any future caller that just wants "this
 * beat type's own configured default, no override layering") or an explicitly resolved
 * [MidiBeatAction] (via [fire] directly). Production's actual beat-firing path is
 * `MetronomeEngine.onBeat` calling `MetronomeEngine.resolveMidiActionForBeat` (which layers a
 * per-beat override over the beat-type default - see [TimeSignature.midiOverrides]) and passing
 * the result straight to [fire]; the manual Trigger button and [Phrase.action] both call [fire]
 * the same way. The mirror image of [MidiClockSender] (same [MidiReceiverRegistry] destination
 * shape, same "always derives from whatever's actually happening, don't care how it got here"
 * philosophy) but reactive rather than a free-running ticking loop: `onBeat` already fires exactly
 * once per real beat, so there's no schedule of its own to maintain or resync - unlike
 * [MidiClockSender]'s 24-ppqn clock, a Note/CC message only needs to happen once per beat, not be
 * subdivided.
 *
 * **[fire] itself is synchronous, but the actual sending never runs on the caller's thread.**
 * `onBeat` runs on `MetronomeEngine`'s own dedicated, timing-critical clock thread -
 * `MidiReceiver.send()` is not guaranteed non-blocking (a slow USB device or Binder IPC overhead
 * could take real time), so every byte-send, including the first one, happens inside [scope]'s
 * `launch` rather than inline. [fire]/[fireForBeat] cost only a couple of `StateFlow` reads and
 * one cheap coroutine dispatch on the calling thread - see [scope]'s own comment for why that
 * dispatch is safe to not be one of `MetronomeEngine`'s elevated-priority dispatchers.
 *
 * Deliberately independent of `clickEnabled`/mute-probability - see `beatTypeFor`'s own kdoc for
 * why (the audible click is muted for practice; MIDI-driven external gear/lights aren't). Gated
 * only by [enabled] (this class's own on/off switch, mirroring [MidiClockSender.enabled]) and
 * each beat type's own configured [MidiBeatAction.type].
 */
object MidiActionSender {

    @Volatile
    private var settings: MetronomeSettings? = null

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "MIDI action send failed", throwable)
    }

    // Not one of MetronomeEngine's elevated-priority dispatchers (see engine/TimingDispatcher.kt) -
    // this exists specifically to get send() calls and the Note Off delay *off* onBeat's own
    // timing-critical thread, not to place them precisely; ordinary Dispatchers.Default scheduling
    // latency (sub-beat-interval, not sub-millisecond) is fine for a MIDI receiver's own tolerance.
    private val scope = CoroutineScope(SupervisorJob() + exceptionHandler + Dispatchers.Default)

    private val registry = MidiReceiverRegistry()

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _actions = MutableStateFlow(ClickSound.entries.associateWith { MidiBeatAction() })
    val actions: StateFlow<Map<ClickSound, MidiBeatAction>> = _actions.asStateFlow()

    /** Loads the persisted on/off state and per-sound actions. Safe to call multiple times. */
    fun attach(context: Context) {
        if (settings != null) return
        val store = MetronomeSettings(context.applicationContext)
        settings = store
        _enabled.value = store.midiActionsEnabled
        _actions.value = ClickSound.entries.associateWith { store.midiBeatAction(it) }
    }

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        settings?.midiActionsEnabled = value
    }

    fun setAction(sound: ClickSound, action: MidiBeatAction) {
        _actions.update { it + (sound to action) }
        settings?.setMidiBeatAction(sound, action)
    }

    fun addDestination(receiver: MidiReceiver) = registry.add(receiver)

    fun removeDestination(receiver: MidiReceiver) = registry.remove(receiver)

    /** Thin wrapper over [fire] for the common case - looks up [sound]'s own configured
     * type-default action and fires that. See [fire] for the actual gating/send logic; this
     * function's own signature is unchanged from before [fire] was extracted out of it, so every
     * existing call site and test keeps working unmodified. */
    fun fireForBeat(sound: ClickSound, timestampNanos: Long) {
        val action = _actions.value[sound] ?: return
        fire(action, timestampNanos)
    }

    /** Called once per real beat from `MetronomeEngine.onBeat` (via [fireForBeat]) or directly
     * from a resolved per-beat override/manual Trigger (see
     * `MetronomeEngine.resolveMidiActionForBeat`) - either way, this function must return promptly
     * regardless of what actually sending bytes involves (`MidiReceiver.send()` is not guaranteed
     * non-blocking - a slow USB device or Binder IPC overhead could otherwise stall the engine's
     * own beat-firing loop), so the real work - including the *first* send, not just the delayed
     * Note Off - happens inside [scope]'s `launch`, off the caller's thread entirely. A no-op
     * whenever disabled, there's nowhere to send to, or [action]'s own type is
     * [MidiActionType.NONE] - checked before launching, so a disabled/unconfigured action costs
     * only two `StateFlow` reads on the caller's thread, no coroutine dispatch at all. */
    fun fire(action: MidiBeatAction, timestampNanos: Long) {
        if (!_enabled.value || registry.isEmpty) return
        if (action.type == MidiActionType.NONE) return
        scope.launch {
            when (action.type) {
                MidiActionType.NONE -> return@launch
                MidiActionType.NOTE -> {
                    val channel = action.channel.coerceIn(0, 15)
                    val note = action.number.coerceIn(0, 127)
                    // Floored at 1, not 0 - a Note On with velocity 0 is conventionally read by
                    // receivers as a Note Off, which would silently turn a quiet accent into no
                    // sound at all rather than a quiet one.
                    val velocity = action.value.coerceIn(1, 127)
                    send(byteArrayOf((NOTE_ON or channel).toByte(), note.toByte(), velocity.toByte()), timestampNanos)
                    val durationMs = action.durationMs.coerceAtLeast(0).toLong()
                    delay(durationMs)
                    // Stamped at the beat's own time plus the held duration, not send()'s actual
                    // dispatch time - a timestamp-aware receiver should read Note Off as landing
                    // exactly durationMs after this beat, regardless of coroutine/IPC scheduling jitter.
                    send(byteArrayOf((NOTE_OFF or channel).toByte(), note.toByte(), 0), timestampNanos + durationMs * 1_000_000L)
                }
                MidiActionType.CC -> {
                    val channel = action.channel.coerceIn(0, 15)
                    val controller = action.number.coerceIn(0, 127)
                    val value = action.value.coerceIn(0, 127)
                    send(byteArrayOf((CONTROL_CHANGE or channel).toByte(), controller.toByte(), value.toByte()), timestampNanos)
                }
            }
        }
    }

    // [timestampNanos] is the caller's authoritative beat time (System.nanoTime() base, possibly
    // lookahead-scheduled ahead of "now" - see MetronomeEngine.onBeat) rather than this function's
    // own dispatch time, so a timestamp-aware receiver can align the event to the metronome's beat
    // timing model instead of whenever the async send happened to run.
    private fun send(message: ByteArray, timestampNanos: Long) {
        registry.forEach { destination ->
            try {
                destination.send(message, 0, message.size, timestampNanos)
            } catch (e: Exception) {
                Log.w(TAG, "Failed sending a MIDI action to a destination", e)
            }
        }
    }

    /** For tests only - this is a process-wide singleton, so state would otherwise leak between
     * test cases, mirroring [media.quaternion.qmetronome.engine.MetronomeEngine.resetForTesting]'s
     * own precedent. */
    fun resetForTesting() {
        _enabled.value = false
        _actions.value = ClickSound.entries.associateWith { MidiBeatAction() }
        settings = null
    }

    private const val TAG = "MidiActionSender"

    // MIDI 1.0 Channel Voice messages - low nibble carries the channel (0-15).
    private const val NOTE_OFF = 0x80
    private const val NOTE_ON = 0x90
    private const val CONTROL_CHANGE = 0xB0
}
