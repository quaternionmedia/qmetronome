package com.example.qmetronome.engine

/**
 * A snapshot of where the metronome is right now, sampled continuously while playing.
 * Visualizers are pure functions of this value, so they don't need to know about timers,
 * threads, or audio at all.
 */
data class BeatPhase(
    val bpm: Float,
    val beatsPerBar: Int,
    val beatIndex: Int,
    val totalBeats: Long,
    val phase: Float,
    val isAccent: Boolean,
    val isPlaying: Boolean,
) {
    companion object {
        val IDLE = BeatPhase(
            bpm = 120f,
            beatsPerBar = 4,
            beatIndex = 0,
            totalBeats = 0,
            phase = 0f,
            isAccent = true,
            isPlaying = false,
        )
    }
}
