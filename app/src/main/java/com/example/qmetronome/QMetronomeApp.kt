package com.example.qmetronome

import android.app.Application
import com.example.qmetronome.engine.MetronomeEngine

class QMetronomeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MetronomeEngine.attach(this)
    }
}
