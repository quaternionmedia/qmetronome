package media.quaternion.qmetronome.midi

import android.content.Context

/**
 * Persists which USB MIDI devices are "starred" for aggressive auto-reconnect, plus which
 * direction(s) - follow, send, or both - to restore when a starred device reappears. Keyed by
 * [UsbMidiConnector.deviceKey], a stable vendor/product/serial identity, not
 * `MidiDeviceInfo.id` - the platform reassigns that on every (re)connection, so it can't be used
 * to recognize "this is the same device" across an unplug/replug.
 */
class StarredMidiDevices(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun starredKeys(): Set<String> = prefs.getStringSet(KEY_STARRED, emptySet()).orEmpty()

    fun isStarred(key: String): Boolean = key in starredKeys()

    /** Unstarring also forgets the desired follow/send state, so a later re-star starts clean. */
    fun setStarred(key: String, starred: Boolean) {
        val updated = starredKeys().toMutableSet()
        if (starred) updated.add(key) else updated.remove(key)
        prefs.edit().putStringSet(KEY_STARRED, updated).apply()
        if (!starred) {
            prefs.edit().remove(followKeyFor(key)).remove(sendKeyFor(key)).apply()
        }
    }

    fun desiredFollow(key: String): Boolean = prefs.getBoolean(followKeyFor(key), false)
    fun setDesiredFollow(key: String, value: Boolean) {
        prefs.edit().putBoolean(followKeyFor(key), value).apply()
    }

    fun desiredSend(key: String): Boolean = prefs.getBoolean(sendKeyFor(key), false)
    fun setDesiredSend(key: String, value: Boolean) {
        prefs.edit().putBoolean(sendKeyFor(key), value).apply()
    }

    private fun followKeyFor(key: String) = "follow:$key"
    private fun sendKeyFor(key: String) = "send:$key"

    private companion object {
        const val PREFS_NAME = "starred_midi_devices"
        const val KEY_STARRED = "starred_keys"
    }
}
