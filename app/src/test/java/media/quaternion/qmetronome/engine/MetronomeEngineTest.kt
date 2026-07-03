package media.quaternion.qmetronome.engine

import media.quaternion.qmetronome.midi.MidiClockSource
import media.quaternion.qmetronome.visualizers.GlyphVisualizer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun `engine auto-switches to midi clock on first external tick`() {
        assertEquals(MetronomeEngine.ClockStatus.Internal, MetronomeEngine.clockStatus.value)

        MidiClockSource.receiverFor(MidiClockSource.Source.VIRTUAL)
            .send(byteArrayOf(0xF8.toByte()), 0, 1, System.nanoTime())

        assertTrue(MetronomeEngine.clockStatus.value is MetronomeEngine.ClockStatus.Midi)
    }

    @Test
    fun `engine bpm follows measured midi clock tempo`() {
        MetronomeEngine.useMidiClock()
        MetronomeEngine.start()

        // Send 48 ticks at 120 BPM timing (20,833,333 ns per tick at 24 PPQN = 2 full beats).
        // Start one step-width in so tick 0 has a non-zero timestamp and tick 1 records the
        // first real interval.
        val receiver = MidiClockSource.receiverFor(MidiClockSource.Source.USB)
        val message = byteArrayOf(0xF8.toByte())
        val stepNanos = 20_833_333L
        for (i in 1..48) {
            receiver.send(message, 0, 1, i.toLong() * stepNanos)
        }

        assertEquals(120f, MetronomeEngine.state.value.bpm, 5f)
    }

    @Test
    fun `setBpm and setBeatsPerBar still apply immediately when hold is off`() {
        assertEquals(MetronomeEngine.HoldMode.Off, MetronomeEngine.holdMode.value)

        MetronomeEngine.setBpm(140f)
        MetronomeEngine.setBeatsPerBar(6)

        assertEquals(140f, MetronomeEngine.state.value.bpm)
        assertEquals(6, MetronomeEngine.state.value.beatsPerBar)
        assertNull(MetronomeEngine.stagedBpm.value)
        assertNull(MetronomeEngine.stagedBeatsPerBar.value)
    }

    @Test
    fun `momentary hold stages bpm changes and flushes them on release`() {
        MetronomeEngine.setBpm(100f)

        MetronomeEngine.beginHold()
        MetronomeEngine.setBpm(140f)
        assertEquals(100f, MetronomeEngine.state.value.bpm)
        assertEquals(140f, MetronomeEngine.stagedBpm.value)

        MetronomeEngine.endHold()
        assertEquals(140f, MetronomeEngine.state.value.bpm)
        assertEquals(MetronomeEngine.HoldMode.Off, MetronomeEngine.holdMode.value)
        assertNull(MetronomeEngine.stagedBpm.value)
    }

    @Test
    fun `toggleLatch enters a sticky latch that only clears on a later toggle, not a release`() {
        MetronomeEngine.setBpm(100f)

        MetronomeEngine.toggleLatch()
        assertEquals(MetronomeEngine.HoldMode.Latched, MetronomeEngine.holdMode.value)

        MetronomeEngine.setBpm(150f)
        assertEquals(100f, MetronomeEngine.state.value.bpm)
        assertEquals(150f, MetronomeEngine.stagedBpm.value)

        // beginHold/endHold model a press-release cycle that doesn't itself unlatch - only a
        // dedicated toggleLatch() call (the unlatch tap) does.
        MetronomeEngine.beginHold()
        MetronomeEngine.endHold()
        assertEquals(MetronomeEngine.HoldMode.Latched, MetronomeEngine.holdMode.value)
        assertEquals(100f, MetronomeEngine.state.value.bpm)

        MetronomeEngine.toggleLatch()
        assertEquals(MetronomeEngine.HoldMode.Off, MetronomeEngine.holdMode.value)
        assertEquals(150f, MetronomeEngine.state.value.bpm)
        assertNull(MetronomeEngine.stagedBpm.value)
    }

    @Test
    fun `tap tempo stages instead of applying live bpm while held`() {
        val liveBpmBefore = MetronomeEngine.state.value.bpm
        MetronomeEngine.beginHold()

        MetronomeEngine.tapTempo(nowNanos = 1_000_000_000L)
        MetronomeEngine.tapTempo(nowNanos = 1_500_000_000L) // 500ms later = 120 BPM

        assertEquals(liveBpmBefore, MetronomeEngine.state.value.bpm)
        assertEquals(120f, requireNotNull(MetronomeEngine.stagedBpm.value), 0.5f)
    }

    @Test
    fun `a staged beats-per-bar change while playing waits for the next bar's downbeat`() {
        MetronomeEngine.setBpm(400f) // fastest allowed tempo (~150ms/beat) keeps this test quick
        MetronomeEngine.setBeatsPerBar(4)
        MetronomeEngine.start()
        Thread.sleep(220) // land roughly mid-bar, not right at a boundary

        MetronomeEngine.toggleLatch()
        MetronomeEngine.setBeatsPerBar(6)
        MetronomeEngine.toggleLatch() // the unlatch tap, while still mid-bar

        assertEquals(6, MetronomeEngine.stagedBeatsPerBar.value)
        assertEquals(4, MetronomeEngine.state.value.beatsPerBar)

        Thread.sleep(900) // comfortably past the next bar boundary even with scheduling jitter

        assertEquals(6, MetronomeEngine.state.value.beatsPerBar)
        assertNull(MetronomeEngine.stagedBeatsPerBar.value)
    }

    @Test
    fun `stopping force-clears an in-progress hold and flushes staged bpm`() {
        MetronomeEngine.setBpm(100f)
        MetronomeEngine.toggleLatch()
        MetronomeEngine.setBpm(150f)

        MetronomeEngine.stop()

        assertEquals(MetronomeEngine.HoldMode.Off, MetronomeEngine.holdMode.value)
        assertEquals(150f, MetronomeEngine.state.value.bpm)
        assertNull(MetronomeEngine.stagedBpm.value)
    }
}
