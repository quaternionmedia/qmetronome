package media.quaternion.qmetronome

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import media.quaternion.qmetronome.engine.MetronomeEngine
import media.quaternion.qmetronome.engine.PersistentPlaybackService
import media.quaternion.qmetronome.midi.MidiActionSender
import media.quaternion.qmetronome.midi.MidiClockSender
import media.quaternion.qmetronome.midi.UsbMidiConnector
import media.quaternion.qmetronome.widget.MetronomeWidget
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val TAG = "QMetronomeApp"

class QMetronomeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MetronomeEngine.attach(this)
        MidiClockSender.attach(this)
        MidiActionSender.attach(this)
        UsbMidiConnector.attach(this)

        // Push a widget update only when displayed bpm or play state actually changes - not on
        // every render-loop tick. The engine's state flow emits a new phase ~40 times a second
        // while playing; distinctUntilChanged on just the fields the widget shows is what keeps
        // this event-driven instead of an accidental poll.
        //
        // The exception handler matters more than it looks: a bare `launch` with no handler that
        // throws on its first emission dies silently and never collects again - which looks
        // exactly like "the widget stopped updating" with nothing in the normal log output to
        // explain why. This makes that failure mode loud instead of silent.
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Widget update collector died", throwable)
        }
        CoroutineScope(Dispatchers.Default + exceptionHandler).launch {
            MetronomeEngine.state
                .map { it.bpm.roundToInt() to it.isPlaying }
                .distinctUntilChanged()
                .collect { (bpm, isPlaying) ->
                    Log.d(TAG, "widget state changed: bpm=$bpm isPlaying=$isPlaying - updating widget")
                    try {
                        MetronomeWidget().updateAll(this@QMetronomeApp)
                    } catch (e: Exception) {
                        Log.e(TAG, "updateAll failed", e)
                    }
                }
        }

        // Starts/stops PersistentPlaybackService - only while persistent mode is on *and* the
        // engine is actually playing, so there's nothing to protect (and no foreground service
        // running) the rest of the time.
        CoroutineScope(Dispatchers.Default + exceptionHandler).launch {
            combine(
                MetronomeEngine.persistentModeEnabled,
                MetronomeEngine.state.map { it.isPlaying }.distinctUntilChanged(),
            ) { persistentModeEnabled, isPlaying -> persistentModeEnabled && isPlaying }
                .distinctUntilChanged()
                .collect { shouldRun ->
                    Log.d(TAG, "persistent playback service shouldRun=$shouldRun")
                    val intent = Intent(this@QMetronomeApp, PersistentPlaybackService::class.java)
                    try {
                        if (shouldRun) {
                            ContextCompat.startForegroundService(this@QMetronomeApp, intent)
                        } else {
                            stopService(intent)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start/stop PersistentPlaybackService", e)
                    }
                }
        }
    }
}
