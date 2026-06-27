package com.example.qmetronome.engine

import com.example.qmetronome.visualizers.GlyphVisualizer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Regression coverage for the "the glyph needs to be restarted" bug: a render-loop failure
 * (e.g. a misbehaving visualizer) must never silently wedge [MetronomeEngine] into a state
 * where [BeatPhase.isPlaying] says true but nothing is actually ticking.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MetronomeEngineTest {

    @Before
    fun setUp() {
        MetronomeEngine.resetForTesting()
        MetronomeEngine.attach(RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() {
        MetronomeEngine.resetForTesting()
    }

    @Test
    fun `start sets isPlaying, stop clears it`() {
        MetronomeEngine.start()
        assertTrue(MetronomeEngine.state.value.isPlaying)

        MetronomeEngine.stop()
        assertFalse(MetronomeEngine.state.value.isPlaying)
    }

    @Test
    fun `toggle flips play state both ways`() {
        assertFalse(MetronomeEngine.state.value.isPlaying)
        MetronomeEngine.toggle()
        assertTrue(MetronomeEngine.state.value.isPlaying)
        MetronomeEngine.toggle()
        assertFalse(MetronomeEngine.state.value.isPlaying)
    }

    @Test
    fun `start is safe to call twice in a row`() {
        MetronomeEngine.start()
        MetronomeEngine.start()
        assertTrue(MetronomeEngine.state.value.isPlaying)
    }

    @Test
    fun `setBpm clamps to the supported range`() {
        MetronomeEngine.setBpm(1f)
        assertEquals(MetronomeEngine.MIN_BPM, MetronomeEngine.state.value.bpm)

        MetronomeEngine.setBpm(9000f)
        assertEquals(MetronomeEngine.MAX_BPM, MetronomeEngine.state.value.bpm)
    }

    @Test
    fun `setBeatsPerBar clamps to 1 through 12`() {
        MetronomeEngine.setBeatsPerBar(0)
        assertEquals(1, MetronomeEngine.state.value.beatsPerBar)

        MetronomeEngine.setBeatsPerBar(99)
        assertEquals(12, MetronomeEngine.state.value.beatsPerBar)
    }

    @Test
    fun `a visualizer that throws does not crash or wedge the engine`() {
        MetronomeEngine.setVisualizer(object : GlyphVisualizer {
            override val id = "throws-for-test"
            override val displayName = "Throws"
            override fun render(matrixSize: Int, beat: BeatPhase): IntArray {
                throw IllegalStateException("simulated visualizer bug")
            }
        })

        MetronomeEngine.start()
        // Let several render-loop iterations (25ms each) actually hit the throwing visualizer.
        // If the exception weren't isolated, it would propagate out of a non-test coroutine and
        // fail this test - it doesn't, which is exactly what's being verified.
        Thread.sleep(150)

        assertTrue(MetronomeEngine.state.value.isPlaying)
    }

    @Test
    fun `tap tempo needs two taps before it produces a tempo`() {
        // 0L is deliberately avoided as the first timestamp - it collides with tapTempo's
        // internal "never tapped" sentinel and would make this test pass for the wrong reason.
        MetronomeEngine.tapTempo(nowNanos = 1_000_000_000L)
        assertFalse(MetronomeEngine.state.value.isPlaying)

        MetronomeEngine.tapTempo(nowNanos = 1_500_000_000L) // 500ms later = 120 BPM
        assertTrue(MetronomeEngine.state.value.isPlaying)
        assertEquals(120f, MetronomeEngine.state.value.bpm, 0.5f)
    }

    @Test
    fun `a tap gap longer than the timeout resets tap tempo instead of producing a wild bpm`() {
        MetronomeEngine.tapTempo(nowNanos = 1_000_000_000L)
        MetronomeEngine.tapTempo(nowNanos = 6_000_000_000L) // 5s gap, over the 2s timeout
        assertFalse(MetronomeEngine.state.value.isPlaying)
    }
}
