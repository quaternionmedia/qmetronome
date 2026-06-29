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
            MetronomeEngine.frame.collect { frame ->
                if (frame.isEmpty()) return@collect
                try {
                    glyphMatrixManager.setMatrixFrame(frame)
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
        MetronomeEngine.stop()
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
