package com.example.qmetronome.engine

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Plays the audible click so the engine works as a real metronome, not just a light show.
 * Uses [ToneGenerator] so no sound assets are needed; the accent beat gets a higher-pitched tone.
 */
class ClickPlayer {

    private var toneGenerator: ToneGenerator? = null

    fun playClick(isAccent: Boolean) {
        val generator = toneGenerator ?: ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME)
            .also { toneGenerator = it }
        val tone = if (isAccent) ToneGenerator.TONE_PROP_BEEP2 else ToneGenerator.TONE_PROP_BEEP
        generator.startTone(tone, CLICK_DURATION_MS)
    }

    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }

    private companion object {
        const val CLICK_DURATION_MS = 30
    }
}
