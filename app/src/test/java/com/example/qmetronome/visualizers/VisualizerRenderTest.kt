package com.example.qmetronome.visualizers

import com.example.qmetronome.engine.BeatPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every built-in visualizer, exercised across both real device matrix sizes and a spread of
 * beat phases. These are the contract every contributed visualizer is expected to meet (see
 * [GlyphVisualizer]'s docs): correct frame size, in-range brightness, and fast enough that one
 * misbehaving algorithm can't visibly stall the render loop.
 */
class VisualizerRenderTest {

    private val matrixSizes = listOf(13, 25)

    private val samplePhases = listOf(
        BeatPhase.IDLE.copy(isPlaying = true, phase = 0f, beatIndex = 0, isAccent = true),
        BeatPhase.IDLE.copy(isPlaying = true, phase = 0.25f, beatIndex = 1, isAccent = false),
        BeatPhase.IDLE.copy(isPlaying = true, phase = 0.5f, beatIndex = 2, isAccent = false, beatsPerBar = 3),
        BeatPhase.IDLE.copy(isPlaying = true, phase = 0.99f, beatIndex = 0, isAccent = true, beatsPerBar = 1),
        BeatPhase.IDLE.copy(isPlaying = true, phase = 1f, beatIndex = 7, isAccent = false, beatsPerBar = 12),
    )

    @Test
    fun `every visualizer returns a correctly sized in-range frame for every sample phase`() {
        for (visualizer in VisualizerRegistry.all) {
            for (size in matrixSizes) {
                for (phase in samplePhases) {
                    val frame = visualizer.render(size, phase)
                    assertEquals(
                        "${visualizer.id} at size=$size phase=$phase returned wrong frame size",
                        size * size,
                        frame.size,
                    )
                    assertTrue(
                        "${visualizer.id} at size=$size phase=$phase produced an out-of-range pixel",
                        frame.all { it in 0..255 },
                    )
                }
            }
        }
    }

    @Test
    fun `every visualizer puts out more total light at the start of a beat than mid-decay (accessible without audio)`() {
        // Total brightness (not peak-pixel brightness) is what actually distinguishes "a flash
        // happened" from "there's always a bright pixel somewhere" - a constantly-bright moving
        // dot would pass a peak-brightness check at every phase without ever actually flashing.
        for (visualizer in VisualizerRegistry.all) {
            val onBeat = BeatPhase.IDLE.copy(isPlaying = true, phase = 0f, isAccent = true)
            val midDecay = BeatPhase.IDLE.copy(isPlaying = true, phase = 0.5f, isAccent = true)
            val totalAtOnset = visualizer.render(25, onBeat).sum()
            val totalMidDecay = visualizer.render(25, midDecay).sum()
            assertTrue(
                "${visualizer.id} should put out more total light at phase=0 than at phase=0.5, " +
                    "so the beat is perceptible without audio - got onset=$totalAtOnset mid=$totalMidDecay",
                totalAtOnset > totalMidDecay,
            )
        }
    }

    @Test
    fun `every visualizer renders within a reasonable time budget`() {
        val maxMillisPerFrame = 5.0
        for (visualizer in VisualizerRegistry.all) {
            // Warm up the JIT before measuring.
            repeat(20) { visualizer.render(25, samplePhases[0]) }

            val start = System.nanoTime()
            val iterations = 200
            repeat(iterations) {
                for (phase in samplePhases) visualizer.render(25, phase)
            }
            val elapsedMillis = (System.nanoTime() - start) / 1_000_000.0
            val perFrameMillis = elapsedMillis / (iterations * samplePhases.size)

            assertTrue(
                "${visualizer.id} averaged ${perFrameMillis}ms/frame, over the $maxMillisPerFrame" +
                    "ms budget - the render loop pushes frames every 25ms, so a slow visualizer " +
                    "would visibly lag",
                perFrameMillis < maxMillisPerFrame,
            )
        }
    }
}
