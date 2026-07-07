package media.quaternion.qmetronome.glyph

import android.content.Context
import android.util.Log
import media.quaternion.qmetronome.engine.MetronomeEngine
import com.nothing.ketchum.Common
import com.nothing.ketchum.GlyphMatrixManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The actual Glyph Toy. Per the Glyph Toy lifecycle, selecting the toy on the Glyph Button starts
 * its function and deselecting stops it - so the metronome starts/stops with toy selection.
 * While selected:
 *  - Touch-down on the Glyph Button registers a tap-tempo beat.
 *  - Long-press cycles to the next visualizer algorithm.
 * All actual timing/rendering lives in [MetronomeEngine]; this class only wires it to the matrix.
 */
class MetronomeGlyphService : GlyphMatrixToyService("MetronomeGlyph") {

    private var toyScope: CoroutineScope? = null

    override fun performOnServiceConnected(context: Context, glyphMatrixManager: GlyphMatrixManager) {
        // Defensive: if this fires twice without an intervening disconnect (the same anomaly
        // GlyphMatrixToyService.onBind guards against), don't leak the previous collector - it
        // would keep calling setMatrixFrame() concurrently with the new one.
        toyScope?.cancel()

        MetronomeEngine.attach(context)
        MetronomeEngine.setMatrixSize(Common.getDeviceMatrixLength())

        val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.Default +
                CoroutineExceptionHandler { _, e -> Log.e(TAG, "Glyph frame collector failed", e) },
        )
        toyScope = scope
        scope.launch {
            // Every emission from a running render loop is a genuinely new array instance (see
            // GlyphCanvas.BufferPool's kdoc), but not necessarily different *content* frame to
            // frame - e.g. a fully-decayed resting pose repeated across several polls at a slow
            // tempo. Skipping an unchanged push saves a real hardware/IPC call each time, not just
            // a wasted allocation.
            var lastPushedFrame: IntArray? = null
            MetronomeEngine.frame.collect { frame ->
                if (frame.isEmpty()) return@collect
                if (lastPushedFrame?.contentEquals(frame) == true) return@collect
                try {
                    glyphMatrixManager.setMatrixFrame(frame)
                    lastPushedFrame = frame
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to update Glyph Matrix", e)
                }
            }
        }

        MetronomeEngine.start()
    }

    override fun performOnServiceDisconnected(context: Context) {
        toyScope?.cancel()
        toyScope = null
        // Nothing OS unbinds this service both when the user deliberately switches to a
        // different Glyph Toy (should stop - that's the whole point of toy selection) and when
        // the phone is simply unlocked, since the ambient Glyph Interface closes either way -
        // these two cases are indistinguishable from here, both are a plain unbind with no
        // further signal. Persistent mode (Settings -> Playback) opts out of tying playback to
        // toy-bind-state at all, trading "toy-switch also stops it" for "unlock doesn't."
        if (!MetronomeEngine.persistentModeEnabled.value) {
            MetronomeEngine.stop()
        }
    }

    override fun onTouchPointPressed() {
        MetronomeEngine.tapTempo()
    }

    override fun onTouchPointLongPress() {
        MetronomeEngine.nextVisualizer()
    }

    private companion object {
        const val TAG = "MetronomeGlyphService"
    }
}
