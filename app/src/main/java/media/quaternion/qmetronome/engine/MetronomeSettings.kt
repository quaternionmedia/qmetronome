package media.quaternion.qmetronome.engine

import android.content.Context

/** Default [MetronomeSettings.visualOffsetMs] - shared with [MetronomeEngine]'s in-memory default
 * so a fresh install and a not-yet-attached engine agree on the same starting value.
 *
 * **Intentionally `0f`, not a guessed pre-roll.** Earlier defaulted to `-50ms`, a hand-guessed
 * compensation for "typical" human reaction/perception plus display lag - never measured against
 * this device's actual Glyph Matrix render/transmit latency, and no two devices' real latency is
 * guaranteed to match a single hardcoded guess. A guessed offset that's *wrong* for a given unit is
 * strictly worse than no offset at all: it moves the perceived beat somewhere the user didn't ask
 * for, in a direction they'd have to discover and undo. True zero is the honest, predictable
 * starting point - still fully adjustable via Settings for whatever a given performer's own
 * hardware/perception actually needs, exactly as before. */
const val DEFAULT_VISUAL_OFFSET_MS = 0f

/** Default [MetronomeSettings.audioOffsetMs] - see [MetronomeEngine.audioOffsetMs] for how it's
 * actually applied.
 *
 * **Intentionally `0f`, not a guessed pre-roll** - the same reasoning as [DEFAULT_VISUAL_OFFSET_MS]
 * (previously `-30ms`, a smaller hand-guessed lead than the visual offset's, never measured per-
 * device). Unlike the visual offset, this one has a real structural dependency worth knowing about:
 * `MetronomeEngine.startAudioScheduling`'s predictive lookahead loop used to gate on this value
 * being *negative*, conflating "the user wants perceptual pre-roll" with "the streaming engine
 * needs some lead time to place any click precisely at all" - the latter is true regardless of
 * this value's sign. That gate was widened to include exactly zero specifically so this default
 * change doesn't also silently disable lead-scheduling for every beat - see that function's own
 * kdoc and `docs/timing-accuracy-benchmark.md` for the measured before/after. */
const val DEFAULT_AUDIO_OFFSET_MS = 0f

/** Default [MetronomeSettings.firstBeatCountInCapMs] - matches [MetronomeEngine]'s own
 * `MAX_STREAMING_LEAD_MARGIN_NANOS` (100ms) rather than introducing a second magic number: this
 * is the same "generous headroom for an unusually large buffer" ceiling the steady-state lookahead
 * loop already uses, just applied to beat 0 specifically. See [MetronomeEngine.beatZeroCountInNanos]. */
const val DEFAULT_FIRST_BEAT_COUNT_IN_CAP_MS = 100f

/** Persists the last-used tempo, time signature and visualizer choice across process restarts. */
class MetronomeSettings(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var bpm: Float
        get() = prefs.getFloat(KEY_BPM, 120f)
        set(value) = prefs.edit().putFloat(KEY_BPM, value).apply()

    var beatsPerBar: Int
        get() = prefs.getInt(KEY_BEATS_PER_BAR, 4)
        set(value) = prefs.edit().putInt(KEY_BEATS_PER_BAR, value).apply()

    var visualizerId: String?
        get() = prefs.getString(KEY_VISUALIZER_ID, null)
        set(value) = prefs.edit().putString(KEY_VISUALIZER_ID, value).apply()

    /** Defaults to off - a silent visual metronome is the safer default than an unexpected click. */
    var clickEnabled: Boolean
        get() = prefs.getBoolean(KEY_CLICK_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_CLICK_ENABLED, value).apply()

    /** Defaults to off - don't start emitting MIDI clock until the user explicitly asks for it. */
    var clockOutEnabled: Boolean
        get() = prefs.getBoolean(KEY_CLOCK_OUT_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_CLOCK_OUT_ENABLED, value).apply()

    /** Defaults to [ClockTimingMode.MECHANICAL] - the corrected/stabilized outgoing clock, since
     * that's the behavior this app shipped with before [ClockTimingMode.ORGANIC] existed as a
     * choice. See [ClockTimingMode] and `MidiClockSender.effectiveBpm` for what each mode does. */
    var clockOutTimingMode: ClockTimingMode
        get() = ClockTimingMode.entries.getOrElse(prefs.getInt(KEY_CLOCK_OUT_TIMING_MODE, 0)) {
            ClockTimingMode.MECHANICAL
        }
        set(value) = prefs.edit().putInt(KEY_CLOCK_OUT_TIMING_MODE, value.ordinal).apply()

    /** How many milliseconds to shift the visual phase ahead of (negative) or behind (positive)
     * the beat timestamp - lets performers compensate for display latency by feel or measurement.
     * Defaults to [DEFAULT_VISUAL_OFFSET_MS] (`0`) - see that constant's own kdoc for why a
     * guessed non-zero default was deliberately removed rather than kept. Still fully adjustable
     * via Settings for whatever a given performer's hardware/perception actually needs. */
    var visualOffsetMs: Float
        get() = prefs.getFloat(KEY_VISUAL_OFFSET_MS, DEFAULT_VISUAL_OFFSET_MS)
        set(value) = prefs.edit().putFloat(KEY_VISUAL_OFFSET_MS, value).apply()

    /** Same idea as [visualOffsetMs], but for the audible click - see
     * [MetronomeEngine.audioOffsetMs] for why a discrete one-shot trigger needs genuine lookahead
     * scheduling to fire early, rather than just a shifted decay curve. Defaults to
     * [DEFAULT_AUDIO_OFFSET_MS] (`0`) - see that constant's own kdoc for why, and for the
     * lookahead-scheduling gating fix that had to come with it. */
    var audioOffsetMs: Float
        get() = prefs.getFloat(KEY_AUDIO_OFFSET_MS, DEFAULT_AUDIO_OFFSET_MS)
        set(value) = prefs.edit().putFloat(KEY_AUDIO_OFFSET_MS, value).apply()

    /** Maximum pause (ms) [MetronomeEngine] may hold the very first beat back by, to give its
     * click the same genuine lead-scheduling every later beat gets - see
     * [MetronomeEngine.beatZeroCountInNanos] for the full mechanism and why beat 0 structurally
     * can't get that lead for free. 0 keeps the first beat instant (today's behavior, at the cost
     * of that beat's click potentially trailing the visual flash); [DEFAULT_FIRST_BEAT_COUNT_IN_CAP_MS]
     * comfortably covers real hardware out of the box. */
    var firstBeatCountInCapMs: Float
        get() = prefs.getFloat(KEY_FIRST_BEAT_COUNT_IN_CAP_MS, DEFAULT_FIRST_BEAT_COUNT_IN_CAP_MS)
        set(value) = prefs.edit().putFloat(KEY_FIRST_BEAT_COUNT_IN_CAP_MS, value).apply()

    /** When true and the device is in landscape, the main screen switches to a side-by-side
     * preview+controls layout that fits within the screen height instead of overflowing. */
    var compactLandscape: Boolean
        get() = prefs.getBoolean(KEY_COMPACT_LANDSCAPE, false)
        set(value) = prefs.edit().putBoolean(KEY_COMPACT_LANDSCAPE, value).apply()

    /** Defaults to off - see [MetronomeEngine.symbolicControlsEnabled] for what it toggles. */
    var symbolicControlsEnabled: Boolean
        get() = prefs.getBoolean(KEY_SYMBOLIC_CONTROLS_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SYMBOLIC_CONTROLS_ENABLED, value).apply()

    /** Defaults to *on* - unlike [symbolicControlsEnabled] (a different, unrelated toggle: text
     * vs. icon-only transport controls), this one governs the small unit-symbol marks (bpm/beats/
     * beat-type/bar/phrase) shown next to their respective controls - see
     * [MetronomeEngine.unitSymbolsEnabled]. */
    var unitSymbolsEnabled: Boolean
        get() = prefs.getBoolean(KEY_UNIT_SYMBOLS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_UNIT_SYMBOLS_ENABLED, value).apply()

    /** Defaults to off - most users get equivalent reliability for free by raising their phone's
     * own screen-timeout and keeping the app open (see Settings -> Playback's own copy). When on,
     * a foreground service (`PersistentPlaybackService`) keeps the engine running through
     * backgrounding/screen-lock/Glyph Toy unbind instead of implicitly stopping - see
     * `MetronomeGlyphService.performOnServiceDisconnected`. */
    var persistentModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_PERSISTENT_MODE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_PERSISTENT_MODE_ENABLED, value).apply()

    /** Whether the one-time BPM-number gesture hint (tap/long-press/drag) has already been shown. */
    var hasShownBpmHint: Boolean
        get() = prefs.getBoolean(KEY_HAS_SHOWN_BPM_HINT, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_SHOWN_BPM_HINT, value).apply()

    /** Chance (0..1) that any given beat's click is skipped - a practice tool for internalizing
     * tempo without leaning on every click. Defaults to 0 - off until explicitly turned up. */
    var muteProbability: Float
        get() = prefs.getFloat(KEY_MUTE_PROBABILITY, 0f)
        set(value) = prefs.edit().putFloat(KEY_MUTE_PROBABILITY, value).apply()

    /** When true, muteProbability ramps up from 0 over the first few bars of playback instead of
     * applying at full strength immediately. */
    var progressiveMuteEnabled: Boolean
        get() = prefs.getBoolean(KEY_PROGRESSIVE_MUTE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_PROGRESSIVE_MUTE_ENABLED, value).apply()

    /** How many bars the progressive mute ramp takes to reach its target - the ramp's slope. */
    var progressiveMuteRampBars: Int
        get() = prefs.getInt(KEY_PROGRESSIVE_MUTE_RAMP_BARS, MetronomeEngine.DEFAULT_PROGRESSIVE_MUTE_RAMP_BARS)
        set(value) = prefs.edit().putInt(KEY_PROGRESSIVE_MUTE_RAMP_BARS, value).apply()

    /** Defaults to off - see [MetronomeEngine.extendedBpmRangeEnabled] for what it unlocks. */
    var extendedBpmRangeEnabled: Boolean
        get() = prefs.getBoolean(KEY_EXTENDED_BPM_RANGE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_EXTENDED_BPM_RANGE_ENABLED, value).apply()

    /** The bar queue - beats/note value/tempo/visualizer/accent-pattern/midi-overrides per bar -
     * as "beatCount:unitNoteValue:bpm:visualizerId:accentPattern:midiOverrides" bars joined by "|"
     * (visualizerId empty when no visualizer has been pinned to that bar - see
     * [TimeSignature.visualizerId]; accentPattern a comma-joined list of [BeatAccent] ordinals,
     * e.g. "0,1,2,0", empty when null or all [BeatAccent.NONE]; midiOverrides - see
     * [encodeMidiOverrides]'s own kdoc for its shape). No JSON dependency needed for something this
     * shape-stable. A missing, empty, or corrupt entry falls back to a single default bar rather
     * than an empty queue, which [MetronomeEngine] never allows. Tolerates bars encoded before
     * [TimeSignature.visualizerId]/[TimeSignature.accentPattern]/[TimeSignature.midiOverrides]
     * existed (a missing 4th, 5th, or 6th field decodes the same as an empty one - see
     * [TimeSignatureTest] and [MetronomeSettingsTest] for the exact tolerance this provides across
     * app versions). */
    var queue: List<TimeSignature>
        get() = prefs.getString(KEY_QUEUE, null)?.let(::decodeBars) ?: listOf(TimeSignature.DEFAULT)
        set(value) = prefs.edit().putString(KEY_QUEUE, encodeBars(value)).apply()

    private fun encodeBars(bars: List<TimeSignature>): String = bars.joinToString("|", transform = ::encodeBar)

    private fun decodeBars(encoded: String): List<TimeSignature> =
        encoded.split("|").mapNotNull(::decodeBar).ifEmpty { listOf(TimeSignature.DEFAULT) }

    private fun encodeBar(bar: TimeSignature): String =
        "${bar.beatCount}:${bar.unitNoteValue}:${bar.bpm}:${bar.visualizerId ?: ""}:" +
            "${encodeAccentPattern(bar.accentPattern)}:${encodeMidiOverrides(bar.midiOverrides)}"

    private fun decodeBar(encoded: String): TimeSignature? {
        val parts = encoded.split(":")
        val beatCount = parts.getOrNull(0)?.toIntOrNull()
        val unitNoteValue = parts.getOrNull(1)?.toIntOrNull()
        val bpm = parts.getOrNull(2)?.toFloatOrNull()
        val visualizerId = parts.getOrNull(3)?.takeIf { it.isNotEmpty() }
        val accentPattern = parts.getOrNull(4)?.takeIf { it.isNotEmpty() }?.let(::decodeAccentPattern)
        val midiOverrides = parts.getOrNull(5)?.takeIf { it.isNotEmpty() }?.let(::decodeMidiOverrides)
        if (beatCount == null || unitNoteValue == null || bpm == null) return null
        return TimeSignature(
            beatCount = beatCount,
            unitNoteValue = unitNoteValue,
            bpm = bpm,
            accentPattern = accentPattern,
            visualizerId = visualizerId,
            midiOverrides = midiOverrides,
        )
    }

    private fun encodeAccentPattern(pattern: List<BeatAccent>?): String =
        pattern?.takeIf { it.any { accent -> accent != BeatAccent.NONE } }
            ?.joinToString(",") { it.ordinal.toString() }
            ?: ""

    private fun decodeAccentPattern(encoded: String): List<BeatAccent>? =
        encoded.split(",").map { it.toIntOrNull()?.let { ordinal -> BeatAccent.entries.getOrNull(ordinal) } ?: BeatAccent.NONE }

    /** [TimeSignature.midiOverrides], sparse (typically 0-2 of up to 24 beats) - "" when null or
     * empty, otherwise `beatIndex@type.channel.number.value.durationMs` entries joined by ";".
     * Three delimiters ("`;`", "`@`", "`.`") not used anywhere else in the encoding stack (phrases
     * use "##"/"~", bars "|", bar-fields ":", accent ordinals ","). */
    private fun encodeMidiOverrides(overrides: Map<Int, MidiBeatAction>?): String =
        overrides?.takeIf { it.isNotEmpty() }
            ?.entries?.joinToString(";") { (beatIndex, action) ->
                "$beatIndex@${action.type.name}.${action.channel}.${action.number}.${action.value}.${action.durationMs}"
            }
            ?: ""

    private fun decodeMidiOverrides(encoded: String): Map<Int, MidiBeatAction>? =
        encoded.split(";").mapNotNull { entry ->
            val atIndex = entry.indexOf('@')
            if (atIndex < 0) return@mapNotNull null
            val beatIndex = entry.substring(0, atIndex).toIntOrNull() ?: return@mapNotNull null
            val fields = entry.substring(atIndex + 1).split(".")
            val type = fields.getOrNull(0)?.let { name -> MidiActionType.entries.firstOrNull { it.name == name } }
                ?: return@mapNotNull null
            val channel = fields.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            val number = fields.getOrNull(2)?.toIntOrNull() ?: return@mapNotNull null
            val value = fields.getOrNull(3)?.toIntOrNull() ?: return@mapNotNull null
            val durationMs = fields.getOrNull(4)?.toIntOrNull() ?: return@mapNotNull null
            beatIndex to MidiBeatAction(type = type, channel = channel, number = number, value = value, durationMs = durationMs)
        }.toMap().takeIf { it.isNotEmpty() }

    /** The phrase queue - song-form sections, each with its own full bar queue and own
     * [Phrase.action] (see [MetronomeEngine.phrases]/`Phrase`) - as
     * "barQueueMode~encodedAction~bar1|bar2|..." phrases joined by "##", reusing [encodeBar]/
     * [decodeBar]'s per-bar encoding and [encodeMidiAction]/[decodeMidiAction]'s single-action
     * encoding unchanged (no JSON dependency, matching [queue]'s own established convention).
     * Falls back to migrating the legacy single-queue keys ([queue]/[queueMode]) into a
     * single-phrase list when [KEY_PHRASES] is absent (a pre-this-feature install) -
     * [MetronomeEngine.attach] immediately re-persists that migrated result, so this fallback only
     * ever runs once per install. Also tolerates phrases encoded before [Phrase.action] existed
     * (`split('~', limit = 3)` yields exactly 2 parts for that older
     * "barQueueMode~bar1|bar2|..." shape, 3 for this one - encodeBars/encodeMidiAction never emit
     * a bare "~" themselves, so this is an unambiguous discriminator, not a guess). A missing,
     * empty, or corrupt entry falls back to a single default phrase, the same tolerance [queue]
     * already has for a bad/absent bar list. */
    var phrases: List<Phrase>
        get() {
            val encoded = prefs.getString(KEY_PHRASES, null) ?: return listOf(Phrase(bars = queue, barQueueMode = queueMode))
            val decodedPhrases = encoded.split("##").mapNotNull { phraseEncoded ->
                val parts = phraseEncoded.split('~', limit = 3)
                val mode = parts.getOrNull(0)?.let { name -> MetronomeEngine.QueueMode.entries.firstOrNull { it.name == name } }
                    ?: return@mapNotNull null
                when (parts.size) {
                    3 -> {
                        val action = parts[1].takeIf { it.isNotEmpty() }?.let(::decodeMidiAction) ?: MidiBeatAction()
                        Phrase(bars = decodeBars(parts[2]), barQueueMode = mode, action = action)
                    }
                    2 -> Phrase(bars = decodeBars(parts[1]), barQueueMode = mode)
                    else -> null
                }
            }
            return decodedPhrases.ifEmpty { listOf(Phrase()) }
        }
        set(value) {
            val encoded = value.joinToString("##") {
                "${it.barQueueMode.name}~${encodeMidiAction(it.action)}~${encodeBars(it.bars)}"
            }
            prefs.edit().putString(KEY_PHRASES, encoded).apply()
        }

    /** A single [MidiBeatAction], as "type.channel.number.value.durationMs" - the same
     * dot-delimited per-action shape [encodeMidiOverrides]'s own per-entry encoding uses, reused
     * here for [Phrase.action] rather than inventing a third scheme alongside it and
     * [midiBeatAction]'s older colon-delimited, separately-keyed one. */
    private fun encodeMidiAction(action: MidiBeatAction): String =
        "${action.type.name}.${action.channel}.${action.number}.${action.value}.${action.durationMs}"

    private fun decodeMidiAction(encoded: String): MidiBeatAction {
        val fields = encoded.split(".")
        val type = fields.getOrNull(0)?.let { name -> MidiActionType.entries.firstOrNull { it.name == name } } ?: return MidiBeatAction()
        val channel = fields.getOrNull(1)?.toIntOrNull() ?: return MidiBeatAction()
        val number = fields.getOrNull(2)?.toIntOrNull() ?: return MidiBeatAction()
        val value = fields.getOrNull(3)?.toIntOrNull() ?: return MidiBeatAction()
        val durationMs = fields.getOrNull(4)?.toIntOrNull() ?: return MidiBeatAction()
        return MidiBeatAction(type = type, channel = channel, number = number, value = value, durationMs = durationMs)
    }

    /** Which phrase in [phrases] was active. */
    var activePhraseIndex: Int
        get() = prefs.getInt(KEY_ACTIVE_PHRASE_INDEX, 0)
        set(value) = prefs.edit().putInt(KEY_ACTIVE_PHRASE_INDEX, value).apply()

    /** Governs how phrases advance into each other - defaults to [MetronomeEngine.QueueMode.LOOP],
     * same default [queueMode] already uses. */
    var phraseQueueMode: MetronomeEngine.QueueMode
        get() = MetronomeEngine.QueueMode.entries.getOrElse(prefs.getInt(KEY_PHRASE_QUEUE_MODE, 0)) {
            MetronomeEngine.QueueMode.LOOP
        }
        set(value) = prefs.edit().putInt(KEY_PHRASE_QUEUE_MODE, value.ordinal).apply()

    /** Which bar in [queue] was active - clamped to the restored queue's actual size by the
     * caller, since the queue length itself isn't known until it's decoded. Still the source of
     * truth for "which bar within the *active phrase*" post-phrases - see [MetronomeEngine.goToPhrase]'s
     * own kdoc for why phrase switches don't need a separate per-phrase memory of this. */
    var queueIndex: Int
        get() = prefs.getInt(KEY_QUEUE_INDEX, 0)
        set(value) = prefs.edit().putInt(KEY_QUEUE_INDEX, value).apply()

    var queueMode: MetronomeEngine.QueueMode
        get() = MetronomeEngine.QueueMode.entries.getOrElse(prefs.getInt(KEY_QUEUE_MODE, 0)) {
            MetronomeEngine.QueueMode.LOOP
        }
        set(value) = prefs.edit().putInt(KEY_QUEUE_MODE, value.ordinal).apply()

    /** Whether the ambient per-bar/per-beat background is drawn into the Glyph frame at all -
     * on by default, since it's purely cosmetic and off is just an opt-out. */
    var queueOverlayEnabled: Boolean
        get() = prefs.getBoolean(KEY_QUEUE_OVERLAY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_QUEUE_OVERLAY_ENABLED, value).apply()

    /** Whether the radial per-phrase indicator is drawn into the Glyph frame - on by default,
     * independent of [queueOverlayEnabled] (which governs the per-bar rows). */
    var phraseIndicatorEnabled: Boolean
        get() = prefs.getBoolean(KEY_PHRASE_INDICATOR_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_PHRASE_INDICATOR_ENABLED, value).apply()

    /** Whether the selected visualizer itself renders at all - on by default, independent of
     * [queueOverlayEnabled] so either, both, or neither can be running. */
    var visualizerEnabled: Boolean
        get() = prefs.getBoolean(KEY_VISUALIZER_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VISUALIZER_ENABLED, value).apply()

    /** A [ClickSound]'s tunable definition, as "waveform:frequencyHz:durationMs:gain" - the same
     * compact, no-JSON encoding style as [queue]. Falls back to [ClickSpec.defaultFor] when
     * missing or corrupt, the same tolerance [queue] has for a bad/absent entry. */
    fun clickSpec(sound: ClickSound): ClickSpec {
        val encoded = prefs.getString(clickSpecKey(sound), null) ?: return ClickSpec.defaultFor(sound)
        val parts = encoded.split(":")
        val waveform = parts.getOrNull(0)?.let { name -> ClickWaveform.entries.firstOrNull { it.name == name } }
        val frequencyHz = parts.getOrNull(1)?.toFloatOrNull()
        val durationMs = parts.getOrNull(2)?.toIntOrNull()
        val gain = parts.getOrNull(3)?.toFloatOrNull()
        if (waveform == null || frequencyHz == null || durationMs == null || gain == null) {
            return ClickSpec.defaultFor(sound)
        }
        return ClickSpec(waveform, frequencyHz, durationMs, gain)
    }

    fun setClickSpec(sound: ClickSound, spec: ClickSpec) {
        val encoded = "${spec.waveform.name}:${spec.frequencyHz}:${spec.durationMs}:${spec.gain}"
        prefs.edit().putString(clickSpecKey(sound), encoded).apply()
    }

    private fun clickSpecKey(sound: ClickSound) = "click_spec_${sound.name.lowercase()}"

    /** Defaults to off - don't start emitting MIDI notes/CC until the user explicitly asks for
     * it, the same reasoning [clockOutEnabled] already applies to clock-out. */
    var midiActionsEnabled: Boolean
        get() = prefs.getBoolean(KEY_MIDI_ACTIONS_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_MIDI_ACTIONS_ENABLED, value).apply()

    /** A [ClickSound]'s configured [MidiBeatAction], as "type:channel:number:value:durationMs" -
     * the same per-sound-key, no-JSON encoding style as [clickSpec]. Falls back to a default
     * (all-[MidiActionType.NONE]) [MidiBeatAction] when missing or corrupt. */
    fun midiBeatAction(sound: ClickSound): MidiBeatAction {
        val encoded = prefs.getString(midiActionKey(sound), null) ?: return MidiBeatAction()
        val parts = encoded.split(":")
        val type = parts.getOrNull(0)?.let { name -> MidiActionType.entries.firstOrNull { it.name == name } }
        val channel = parts.getOrNull(1)?.toIntOrNull()
        val number = parts.getOrNull(2)?.toIntOrNull()
        val value = parts.getOrNull(3)?.toIntOrNull()
        val durationMs = parts.getOrNull(4)?.toIntOrNull()
        if (type == null || channel == null || number == null || value == null || durationMs == null) {
            return MidiBeatAction()
        }
        return MidiBeatAction(type, channel, number, value, durationMs)
    }

    fun setMidiBeatAction(sound: ClickSound, action: MidiBeatAction) {
        val encoded = "${action.type.name}:${action.channel}:${action.number}:${action.value}:${action.durationMs}"
        prefs.edit().putString(midiActionKey(sound), encoded).apply()
    }

    private fun midiActionKey(sound: ClickSound) = "midi_action_${sound.name.lowercase()}"

    private companion object {
        const val PREFS_NAME = "metronome_settings"
        const val KEY_BPM = "bpm"
        const val KEY_BEATS_PER_BAR = "beats_per_bar"
        const val KEY_VISUALIZER_ID = "visualizer_id"
        const val KEY_CLICK_ENABLED = "click_enabled"
        const val KEY_CLOCK_OUT_ENABLED = "clock_out_enabled"
        const val KEY_CLOCK_OUT_TIMING_MODE = "clock_out_timing_mode"
        const val KEY_MIDI_ACTIONS_ENABLED = "midi_actions_enabled"
        const val KEY_VISUAL_OFFSET_MS = "visual_offset_ms"
        const val KEY_AUDIO_OFFSET_MS = "audio_offset_ms"
        const val KEY_FIRST_BEAT_COUNT_IN_CAP_MS = "first_beat_count_in_cap_ms"
        const val KEY_COMPACT_LANDSCAPE = "compact_landscape"
        const val KEY_SYMBOLIC_CONTROLS_ENABLED = "symbolic_controls_enabled"
        const val KEY_UNIT_SYMBOLS_ENABLED = "unit_symbols_enabled"
        const val KEY_PERSISTENT_MODE_ENABLED = "persistent_mode_enabled"
        const val KEY_HAS_SHOWN_BPM_HINT = "has_shown_bpm_hint"
        const val KEY_MUTE_PROBABILITY = "mute_probability"
        const val KEY_PROGRESSIVE_MUTE_ENABLED = "progressive_mute_enabled"
        const val KEY_PROGRESSIVE_MUTE_RAMP_BARS = "progressive_mute_ramp_bars"
        const val KEY_EXTENDED_BPM_RANGE_ENABLED = "extended_bpm_range_enabled"
        const val KEY_QUEUE = "queue"
        const val KEY_QUEUE_INDEX = "queue_index"
        const val KEY_QUEUE_MODE = "queue_mode"
        const val KEY_PHRASES = "phrases"
        const val KEY_ACTIVE_PHRASE_INDEX = "active_phrase_index"
        const val KEY_PHRASE_QUEUE_MODE = "phrase_queue_mode"
        const val KEY_QUEUE_OVERLAY_ENABLED = "queue_overlay_enabled"
        const val KEY_PHRASE_INDICATOR_ENABLED = "phrase_indicator_enabled"
        const val KEY_VISUALIZER_ENABLED = "visualizer_enabled"
    }
}
