package media.quaternion.qmetronome.engine

import android.content.Context

/** Default [MetronomeSettings.visualOffsetMs] - shared with [MetronomeEngine]'s in-memory default
 * so a fresh install and a not-yet-attached engine agree on the same starting value. */
const val DEFAULT_VISUAL_OFFSET_MS = -50f

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

    /** How many milliseconds to shift the visual phase ahead of (negative) or behind (positive)
     * the beat timestamp - lets performers compensate for display latency by feel or measurement.
     * Defaults to [DEFAULT_VISUAL_OFFSET_MS] (ahead), not 0 - a human pressing/perceiving a beat
     * and the system rendering/transmitting it both add a little late-arriving lag, so the visual
     * needs to fire a bit early out of the box to land "on time" by feel. Still fully adjustable
     * via Settings for whatever a given performer's hardware/perception actually needs. */
    var visualOffsetMs: Float
        get() = prefs.getFloat(KEY_VISUAL_OFFSET_MS, DEFAULT_VISUAL_OFFSET_MS)
        set(value) = prefs.edit().putFloat(KEY_VISUAL_OFFSET_MS, value).apply()

    /** When true and the device is in landscape, the main screen switches to a side-by-side
     * preview+controls layout that fits within the screen height instead of overflowing. */
    var compactLandscape: Boolean
        get() = prefs.getBoolean(KEY_COMPACT_LANDSCAPE, false)
        set(value) = prefs.edit().putBoolean(KEY_COMPACT_LANDSCAPE, value).apply()

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

    /** The bar queue - beats/note value/tempo/visualizer per bar - as
     * "beatCount:unitNoteValue:bpm:visualizerId" rows joined by "|" (the last field empty when no
     * visualizer has been pinned to that bar - see [TimeSignature.visualizerId]). No JSON
     * dependency needed for something this shape-stable. [accentPattern] isn't persisted - there's
     * no UI to set a custom one yet, so every restored bar reads back with the default (beat 0
     * only) accent. A missing, empty, or corrupt entry falls back to a single default bar rather
     * than an empty queue, which [MetronomeEngine] never allows. Tolerates rows encoded before
     * [TimeSignature.visualizerId] existed (a missing 4th field decodes the same as an empty one). */
    var queue: List<TimeSignature>
        get() {
            val encoded = prefs.getString(KEY_QUEUE, null) ?: return listOf(TimeSignature.DEFAULT)
            val bars = encoded.split("|").mapNotNull { bar ->
                val parts = bar.split(":")
                val beatCount = parts.getOrNull(0)?.toIntOrNull()
                val unitNoteValue = parts.getOrNull(1)?.toIntOrNull()
                val bpm = parts.getOrNull(2)?.toFloatOrNull()
                val visualizerId = parts.getOrNull(3)?.takeIf { it.isNotEmpty() }
                if (beatCount == null || unitNoteValue == null || bpm == null) return@mapNotNull null
                TimeSignature(beatCount = beatCount, unitNoteValue = unitNoteValue, bpm = bpm, visualizerId = visualizerId)
            }
            return bars.ifEmpty { listOf(TimeSignature.DEFAULT) }
        }
        set(value) {
            val encoded = value.joinToString("|") { "${it.beatCount}:${it.unitNoteValue}:${it.bpm}:${it.visualizerId ?: ""}" }
            prefs.edit().putString(KEY_QUEUE, encoded).apply()
        }

    /** Which bar in [queue] was active - clamped to the restored queue's actual size by the
     * caller, since the queue length itself isn't known until it's decoded. */
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

    private companion object {
        const val PREFS_NAME = "metronome_settings"
        const val KEY_BPM = "bpm"
        const val KEY_BEATS_PER_BAR = "beats_per_bar"
        const val KEY_VISUALIZER_ID = "visualizer_id"
        const val KEY_CLICK_ENABLED = "click_enabled"
        const val KEY_CLOCK_OUT_ENABLED = "clock_out_enabled"
        const val KEY_VISUAL_OFFSET_MS = "visual_offset_ms"
        const val KEY_COMPACT_LANDSCAPE = "compact_landscape"
        const val KEY_HAS_SHOWN_BPM_HINT = "has_shown_bpm_hint"
        const val KEY_MUTE_PROBABILITY = "mute_probability"
        const val KEY_PROGRESSIVE_MUTE_ENABLED = "progressive_mute_enabled"
        const val KEY_QUEUE = "queue"
        const val KEY_QUEUE_INDEX = "queue_index"
        const val KEY_QUEUE_MODE = "queue_mode"
        const val KEY_QUEUE_OVERLAY_ENABLED = "queue_overlay_enabled"
        const val KEY_VISUALIZER_ENABLED = "visualizer_enabled"
    }
}
