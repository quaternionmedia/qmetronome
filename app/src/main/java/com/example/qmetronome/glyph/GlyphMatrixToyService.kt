package com.example.qmetronome.glyph

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy

/**
 * Boilerplate for a Glyph Toy [Service]: binds [GlyphMatrixManager], registers the connected
 * device, and turns the Glyph Button's Messenger protocol into plain method calls. Concrete toys
 * only need to override [performOnServiceConnected] and whichever touch callbacks they care about.
 */
abstract class GlyphMatrixToyService(private val tag: String) : Service() {

    var glyphMatrixManager: GlyphMatrixManager? = null
        private set

    private val buttonHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                GlyphToy.MSG_GLYPH_TOY -> {
                    val event = msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)
                    when (event) {
                        GlyphToy.EVENT_ACTION_DOWN -> onTouchPointPressed()
                        GlyphToy.EVENT_ACTION_UP -> onTouchPointReleased()
                        GlyphToy.EVENT_CHANGE -> onTouchPointLongPress()
                        GlyphToy.EVENT_AOD -> onAlwaysOnDisplay()
                    }
                }
                else -> super.handleMessage(msg)
            }
        }
    }

    private val serviceMessenger = Messenger(buttonHandler)

    private val callback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(componentName: ComponentName?) {
            val manager = glyphMatrixManager ?: return
            if (registerDevice(manager)) {
                performOnServiceConnected(applicationContext, manager)
            } else {
                Log.w(tag, "Glyph Matrix not available on this device")
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName?) {
            performOnServiceDisconnected(applicationContext)
        }
    }

    final override fun onBind(intent: Intent?): IBinder? {
        // Guards against a rebind arriving without an intervening onUnbind (seen in practice on
        // some carousel/rebind edge cases): re-running init() on top of an already-bound manager
        // can leave two live connections/collectors racing, which looks like "the glyph needs a
        // restart" until the whole process is killed. Re-using the existing manager is safe since
        // performOnServiceConnected() is idempotent on the concrete toy side too.
        if (glyphMatrixManager != null) {
            Log.w(tag, "onBind called while already bound; reusing existing GlyphMatrixManager")
            return serviceMessenger.binder
        }
        val manager = GlyphMatrixManager.getInstance(applicationContext)
        glyphMatrixManager = manager
        manager.init(callback)
        return serviceMessenger.binder
    }

    final override fun onUnbind(intent: Intent?): Boolean {
        performOnServiceDisconnected(applicationContext)
        glyphMatrixManager?.turnOff()
        glyphMatrixManager?.unInit()
        glyphMatrixManager = null
        return false
    }

    private fun registerDevice(manager: GlyphMatrixManager): Boolean = when {
        Common.is23112() -> manager.register(Glyph.DEVICE_23112)
        Common.is25111p() -> manager.register(Glyph.DEVICE_25111p)
        else -> false
    }

    open fun performOnServiceConnected(context: Context, glyphMatrixManager: GlyphMatrixManager) {}
    open fun performOnServiceDisconnected(context: Context) {}

    open fun onTouchPointPressed() {}
    open fun onTouchPointReleased() {}
    open fun onTouchPointLongPress() {}
    open fun onAlwaysOnDisplay() {}
}
