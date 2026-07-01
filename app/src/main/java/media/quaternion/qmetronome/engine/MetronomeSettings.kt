package media.quaternion.qmetronome.engine

import android.content.Context

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
     * the beat timestamp - lets performers compensate for display latency by feel or measurement. */
    var visualOffsetMs: Float
        get() = prefs.getFloat(KEY_VISUAL_OFFSET_MS, 0f)
        set(value) = prefs.edit().putFloat(KEY_VISUAL_OFFSET_MS, value).apply()

    /** When true and the device is in landscape, the main screen switches to a side-by-side
     * preview+controls layout that fits within the screen height instead of overflowing. */
    var compactLandscape: Boolean
        get() = prefs.getBoolean(KEY_COMPACT_LANDSCAPE, false)
        set(value) = prefs.edit().putBoolean(KEY_COMPACT_LANDSCAPE, value).apply()

    private companion object {
        const val PREFS_NAME = "metronome_settings"
        const val KEY_BPM = "bpm"
        const val KEY_BEATS_PER_BAR = "beats_per_bar"
        const val KEY_VISUALIZER_ID = "visualizer_id"
        const val KEY_CLICK_ENABLED = "click_enabled"
        const val KEY_CLOCK_OUT_ENABLED = "clock_out_enabled"
        const val KEY_VISUAL_OFFSET_MS = "visual_offset_ms"
        const val KEY_COMPACT_LANDSCAPE = "compact_landscape"
    }
}
