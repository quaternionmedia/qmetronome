package media.quaternion.qmetronome.engine

/** What a beat's configured MIDI action sends, if anything - see [MidiBeatAction]. */
enum class MidiActionType { NONE, NOTE, CC }

/**
 * One [ClickSound]'s configured MIDI output, authored in Settings -> MIDI Actions and sent by
 * [media.quaternion.qmetronome.midi.MidiActionSender.fire] whenever that beat type occurs and no
 * per-beat override wins instead (see [MetronomeEngine.beatTypeFor]/
 * [MetronomeEngine.resolveMidiActionForBeat]). [number] is a note number (0-127) when [type] is
 * [MidiActionType.NOTE], or a CC number (0-127) when [MidiActionType.CC]; [value] is velocity or
 * CC value respectively (0-127). [channel] is 0-indexed (0-15, i.e. MIDI channels 1-16 as
 * commonly displayed). [durationMs] only applies to [MidiActionType.NOTE] - how long after Note
 * On [MidiActionSender] waits before sending the matching Note Off.
 */
data class MidiBeatAction(
    val type: MidiActionType = MidiActionType.NONE,
    val channel: Int = 0,
    val number: Int = 60,
    val value: Int = 100,
    val durationMs: Int = 20,
)
