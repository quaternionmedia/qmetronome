package media.quaternion.qmetronome.engine

import media.quaternion.qmetronome.midi.MidiClockSource
import media.quaternion.qmetronome.visualizers.GlyphVisualizer
import media.quaternion.qmetronome.visualizers.VisualizerRegistry
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
    fun `setBeatsPerBar clamps to 1 through 24`() {
        MetronomeEngine.setBeatsPerBar(0)
        assertEquals(1, MetronomeEngine.state.value.beatsPerBar)

        MetronomeEngine.setBeatsPerBar(99)
        assertEquals(24, MetronomeEngine.state.value.beatsPerBar)
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
    fun `midi transport start does not force a fresh bar flash mid-beat`() {
        MetronomeEngine.start()
        // Let phase progress partway through the first beat (120 BPM default = 500ms/beat) before
        // any external clock gets involved.
        Thread.sleep(200)
        val phaseBeforeMidiStart = MetronomeEngine.state.value.phase
        assertTrue(
            "expected phase to have progressed partway through the beat, was $phaseBeforeMidiStart",
            phaseBeforeMidiStart > 0.1f,
        )

        MidiClockSource.receiverFor(MidiClockSource.Source.USB)
            .send(byteArrayOf(0xFA.toByte()), 0, 1, 0L)

        // A MIDI transport Start must not reset the render clock and force a fresh "bar" flash
        // before the real first tick-driven beat (24 ticks later) actually arrives - doing so
        // plays out one full bar-flash decay animation immediately, then a second, identical-
        // looking one when the real beat lands. Phase should simply keep progressing from where
        // it already was, not snap back down to a freshly-reset 0.
        assertTrue(MetronomeEngine.state.value.phase >= phaseBeforeMidiStart)
        assertTrue(MetronomeEngine.clockStatus.value is MetronomeEngine.ClockStatus.Midi)
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
    fun `a drastic tempo increase takes effect promptly, not after the old slow tempo's stale wait`() {
        // Regression test for a real bug: InternalClockSource used to commit to a single sleep
        // sized for whatever bpm was in effect when the wait began. Dropping to a very slow
        // tempo and then speeding back up left the beat - and therefore the animation, which
        // derives its phase from time-since-last-beat - looking frozen for however long was left
        // of the *old* slow interval (up to ~60s at MIN_BPM), only clearing once that stale wait
        // finally elapsed or the metronome was stopped and restarted.
        MetronomeEngine.setBpm(MetronomeEngine.MIN_BPM) // 1 bpm = a 60-second interval
        MetronomeEngine.start()
        Thread.sleep(100) // let the clock commit to its long wait for the second beat

        val totalBeatsBeforeSpeedUp = MetronomeEngine.state.value.totalBeats
        MetronomeEngine.setBpm(MetronomeEngine.MAX_BPM) // 400 bpm = a 150ms interval

        // If the clock were still honoring the stale ~60s wait, this would time out long before
        // a new beat ever arrived. With the fix, one should land within roughly the new tempo's
        // interval plus polling slack, not anywhere close to the old interval.
        Thread.sleep(500)

        assertTrue(
            "expected at least one beat to fire promptly after a drastic speed-up, not only " +
                "after the old slow tempo's full ~60s interval - got " +
                "totalBeats=${MetronomeEngine.state.value.totalBeats} (was $totalBeatsBeforeSpeedUp)",
            MetronomeEngine.state.value.totalBeats > totalBeatsBeforeSpeedUp,
        )
    }

    @Test
    fun `effective mute probability is the flat target when progressive start is off`() {
        MetronomeEngine.setMuteProbability(0.5f)
        MetronomeEngine.setProgressiveMuteEnabled(false)

        assertEquals(0.5f, MetronomeEngine.effectiveMuteProbability(barsElapsed = 0), 0.001f)
        assertEquals(0.5f, MetronomeEngine.effectiveMuteProbability(barsElapsed = 100), 0.001f)
    }

    @Test
    fun `effective mute probability ramps linearly across the progressive-start window`() {
        MetronomeEngine.setMuteProbability(0.8f)
        MetronomeEngine.setProgressiveMuteEnabled(true)

        assertEquals(0f, MetronomeEngine.effectiveMuteProbability(barsElapsed = 0), 0.001f)
        assertEquals(0.8f * 4 / 8, MetronomeEngine.effectiveMuteProbability(barsElapsed = 4), 0.001f)
        assertEquals(0.8f, MetronomeEngine.effectiveMuteProbability(barsElapsed = 8), 0.001f)
        assertEquals(0.8f, MetronomeEngine.effectiveMuteProbability(barsElapsed = 20), 0.001f)
    }

    @Test
    fun `setMuteProbability clamps to 0 through 1`() {
        MetronomeEngine.setMuteProbability(-0.5f)
        assertEquals(0f, MetronomeEngine.muteProbability.value, 0.001f)

        MetronomeEngine.setMuteProbability(1.5f)
        assertEquals(1f, MetronomeEngine.muteProbability.value, 0.001f)
    }

    @Test
    fun `the default queue has a single entry and never auto-advances`() {
        assertEquals(1, MetronomeEngine.timeSignatureQueue.value.size)
        assertEquals(0, MetronomeEngine.queueIndex.value)
        assertNull(MetronomeEngine.nextQueueIndexAfterBar(currentIndex = 0, queueSize = 1, MetronomeEngine.QueueMode.LOOP))
    }

    @Test
    fun `addBarToQueue appends a copy of the active bar and jumps to it`() {
        MetronomeEngine.setBeatsPerBar(5)

        MetronomeEngine.addBarToQueue()

        val queue = MetronomeEngine.timeSignatureQueue.value
        assertEquals(2, queue.size)
        assertEquals(1, MetronomeEngine.queueIndex.value)
        assertEquals(5, queue[0].beatCount)
        assertEquals(5, queue[1].beatCount)
    }

    @Test
    fun `setBeatsPerBar edits only the currently active queue entry`() {
        MetronomeEngine.setBeatsPerBar(4)
        MetronomeEngine.addBarToQueue() // now on a second entry, a copy with beatCount 4

        MetronomeEngine.setBeatsPerBar(7)

        val queue = MetronomeEngine.timeSignatureQueue.value
        assertEquals(4, queue[0].beatCount)
        assertEquals(7, queue[1].beatCount)
    }

    @Test
    fun `rescaledBpmForUnitNoteValueChange preserves the whole-note pulse across denominators`() {
        assertEquals(240f, MetronomeEngine.rescaledBpmForUnitNoteValueChange(120f, 4, 8), 0.01f)
        assertEquals(60f, MetronomeEngine.rescaledBpmForUnitNoteValueChange(120f, 4, 2), 0.01f)
        assertEquals(120f, MetronomeEngine.rescaledBpmForUnitNoteValueChange(120f, 4, 4), 0.01f)
        // A jump big enough to want more than MAX_BPM clamps rather than overflowing the pulse.
        assertEquals(
            MetronomeEngine.MAX_BPM,
            MetronomeEngine.rescaledBpmForUnitNoteValueChange(120f, 4, 32),
            0.01f,
        )
    }

    @Test
    fun `setUnitNoteValue from 4 to 8 doubles bpm, preserving the same underlying tempo`() {
        MetronomeEngine.setBpm(120f)
        MetronomeEngine.setUnitNoteValue(8)
        assertEquals(240f, MetronomeEngine.state.value.bpm, 0.01f)
        assertEquals(8, MetronomeEngine.timeSignature.value.unitNoteValue)
    }

    @Test
    fun `setUnitNoteValue from 4 to 2 halves bpm - the 6-4 vs 3-2 same-tempo scenario`() {
        MetronomeEngine.setBpm(120f)
        MetronomeEngine.setBeatsPerBar(6) // 6/4 at 120 - six evenly-spaced clicks per bar
        MetronomeEngine.setUnitNoteValue(2) // -> 3/2, same bar duration, three clicks instead
        assertEquals(60f, MetronomeEngine.state.value.bpm, 0.01f)
        assertEquals(2, MetronomeEngine.timeSignature.value.unitNoteValue)
    }

    @Test
    fun `setUnitNoteValue to the current value does not touch bpm`() {
        MetronomeEngine.setBpm(120f)
        MetronomeEngine.setUnitNoteValue(4) // already 4 - the TimeSignature.DEFAULT denominator
        assertEquals(120f, MetronomeEngine.state.value.bpm, 0.01f)
    }

    @Test
    fun `setBeatsPerBar alone does not touch bpm`() {
        MetronomeEngine.setBpm(120f)
        MetronomeEngine.setBeatsPerBar(7)
        assertEquals(120f, MetronomeEngine.state.value.bpm, 0.01f)
    }

    @Test
    fun `setUnitNoteValue edits only the currently active queue entry's unitNoteValue and bpm`() {
        MetronomeEngine.setBpm(120f)
        MetronomeEngine.addBarToQueue() // second entry, a copy at bpm 120

        MetronomeEngine.setUnitNoteValue(8)

        val queue = MetronomeEngine.timeSignatureQueue.value
        assertEquals(4, queue[0].unitNoteValue)
        assertEquals(120f, queue[0].bpm, 0.01f)
        assertEquals(8, queue[1].unitNoteValue)
        assertEquals(240f, queue[1].bpm, 0.01f)
    }

    @Test
    fun `a fresh default bar has no pinned visualizer - null means follow the global selection`() {
        assertNull(MetronomeEngine.timeSignatureQueue.value[0].visualizerId)
    }

    @Test
    fun `setVisualizer pins the choice to the currently active queue entry, not every bar`() {
        val first = VisualizerRegistry.all[0]
        val second = VisualizerRegistry.all[1]
        MetronomeEngine.setVisualizer(first)
        MetronomeEngine.addBarToQueue() // second entry, a copy - inherits bar 0's pin (first)

        MetronomeEngine.setVisualizer(second) // re-pins only the now-active bar 1

        val queue = MetronomeEngine.timeSignatureQueue.value
        assertEquals(first.id, queue[0].visualizerId)
        assertEquals(second.id, queue[1].visualizerId)
    }

    @Test
    fun `goToQueueBar restores each bar's own pinned visualizer`() {
        val first = VisualizerRegistry.all[0]
        val second = VisualizerRegistry.all[1]
        MetronomeEngine.setVisualizer(first)
        MetronomeEngine.addBarToQueue()
        MetronomeEngine.setVisualizer(second)

        MetronomeEngine.goToQueueBar(0)
        assertEquals(first.id, MetronomeEngine.visualizer.value.id)

        MetronomeEngine.goToQueueBar(1)
        assertEquals(second.id, MetronomeEngine.visualizer.value.id)
    }

    @Test
    fun `setQueueOverlayEnabled defaults to true and round-trips`() {
        assertTrue(MetronomeEngine.queueOverlayEnabled.value)

        MetronomeEngine.setQueueOverlayEnabled(false)
        assertFalse(MetronomeEngine.queueOverlayEnabled.value)

        MetronomeEngine.setQueueOverlayEnabled(true)
        assertTrue(MetronomeEngine.queueOverlayEnabled.value)
    }

    @Test
    fun `setVisualizerEnabled defaults to true and round-trips independently of the queue overlay`() {
        assertTrue(MetronomeEngine.visualizerEnabled.value)

        MetronomeEngine.setVisualizerEnabled(false)
        assertFalse(MetronomeEngine.visualizerEnabled.value)
        assertTrue("disabling the visualizer must not also disable the queue overlay", MetronomeEngine.queueOverlayEnabled.value)

        MetronomeEngine.setVisualizerEnabled(true)
        assertTrue(MetronomeEngine.visualizerEnabled.value)
    }

    @Test
    fun `disabling the visualizer still lets the queue overlay draw onto a blank frame`() {
        MetronomeEngine.setVisualizerEnabled(false)
        MetronomeEngine.setBeatsPerBar(4)
        MetronomeEngine.addBarToQueue() // a queue, so the overlay isn't a no-op

        MetronomeEngine.start()
        Thread.sleep(60)

        assertTrue("a frame should still be emitted with the visualizer disabled", MetronomeEngine.frame.value.isNotEmpty())
        assertTrue(
            "the overlay should still be visibly drawing onto the blank base frame",
            MetronomeEngine.frame.value.any { it > 0 },
        )
    }

    @Test
    fun `changing unit note value while held stages the rescaled bpm instead of applying it immediately`() {
        MetronomeEngine.setBpm(120f)

        MetronomeEngine.beginHold()
        MetronomeEngine.setUnitNoteValue(8)
        assertEquals(120f, MetronomeEngine.state.value.bpm, 0.01f)
        assertEquals(240f, MetronomeEngine.stagedBpm.value)

        MetronomeEngine.endHold()
        assertEquals(240f, MetronomeEngine.state.value.bpm, 0.01f)
    }

    @Test
    fun `changing unit note value after already staging a bpm rescales from the staged value, not the stale committed one`() {
        MetronomeEngine.setBpm(120f)

        MetronomeEngine.beginHold()
        MetronomeEngine.setBpm(150f) // stages 150, committed bpm is still 120
        MetronomeEngine.setUnitNoteValue(8) // should rescale from 150, not the stale 120

        assertEquals(300f, MetronomeEngine.stagedBpm.value)
        assertEquals(120f, MetronomeEngine.state.value.bpm, 0.01f) // still uncommitted
    }

    @Test
    fun `goToQueueBar clamps to a valid index and updates the active time signature`() {
        MetronomeEngine.setBeatsPerBar(3)
        MetronomeEngine.addBarToQueue()
        MetronomeEngine.setBeatsPerBar(6)

        MetronomeEngine.goToQueueBar(-5)
        assertEquals(0, MetronomeEngine.queueIndex.value)
        assertEquals(3, MetronomeEngine.state.value.beatsPerBar)

        MetronomeEngine.goToQueueBar(99)
        assertEquals(1, MetronomeEngine.queueIndex.value)
        assertEquals(6, MetronomeEngine.state.value.beatsPerBar)
    }

    @Test
    fun `removeCurrentBarFromQueue never drops below one entry`() {
        MetronomeEngine.removeCurrentBarFromQueue()
        assertEquals(1, MetronomeEngine.timeSignatureQueue.value.size)

        MetronomeEngine.addBarToQueue()
        MetronomeEngine.removeCurrentBarFromQueue()
        assertEquals(1, MetronomeEngine.timeSignatureQueue.value.size)
        assertEquals(0, MetronomeEngine.queueIndex.value)
    }

    @Test
    fun `removeBarFromQueue keeps the same bar active when removing a different bar`() {
        MetronomeEngine.setBeatsPerBar(3) // bar 0
        MetronomeEngine.addBarToQueue() // bar 1, active
        MetronomeEngine.setBeatsPerBar(5)
        MetronomeEngine.addBarToQueue() // bar 2, active
        MetronomeEngine.setBeatsPerBar(7)
        MetronomeEngine.goToQueueBar(0) // back to bar 0, now active

        MetronomeEngine.removeBarFromQueue(1) // remove the middle bar, not the active one

        val queue = MetronomeEngine.timeSignatureQueue.value
        assertEquals(2, queue.size)
        assertEquals(3, queue[0].beatCount)
        assertEquals(7, queue[1].beatCount) // old bar 2 shifted down to index 1
        assertEquals(0, MetronomeEngine.queueIndex.value) // stayed on the same (bar-0) bar
        assertEquals(3, MetronomeEngine.state.value.beatsPerBar)
    }

    @Test
    fun `removeBarFromQueue does nothing for an out-of-range index`() {
        MetronomeEngine.addBarToQueue()

        MetronomeEngine.removeBarFromQueue(99)

        assertEquals(2, MetronomeEngine.timeSignatureQueue.value.size)
    }

    @Test
    fun `tempo is per-bar - switching bars recalls each bar's own bpm`() {
        MetronomeEngine.setBpm(90f) // bar 0
        MetronomeEngine.addBarToQueue() // bar 1, a copy - starts at bpm 90 too
        MetronomeEngine.setBpm(150f) // now bar 1 is 150

        MetronomeEngine.goToQueueBar(0)
        assertEquals(90f, MetronomeEngine.state.value.bpm, 0.01f)

        MetronomeEngine.goToQueueBar(1)
        assertEquals(150f, MetronomeEngine.state.value.bpm, 0.01f)
    }

    @Test
    fun `a queue advance's tempo change governs the very next beat, not one beat late`() {
        // Regression test: InternalClockSource used to reuse the pre-beat interval to schedule
        // the beat *after* next, so a tempo change made during onBeat() itself (the bar queue
        // advancing to a bar with a different bpm) took effect one beat later than it should.
        MetronomeEngine.setBpm(60f) // bar 0: 1000ms/beat
        MetronomeEngine.setBeatsPerBar(1) // every beat is a bar boundary
        MetronomeEngine.addBarToQueue() // bar 1, a copy of bar 0
        MetronomeEngine.setBpm(MetronomeEngine.MAX_BPM) // bar 1: 150ms/beat
        MetronomeEngine.goToQueueBar(0) // start on bar 0 (slow)

        MetronomeEngine.start()
        // Beat 1 fires ~immediately on bar 0's slow tempo (the queue never advances on the very
        // first beat). Beat 2 fires ~1000ms later, still using bar 0's tempo for that interval -
        // but *at* beat 2 the queue advances to bar 1's fast tempo, which must govern the
        // interval before beat 3, not the interval after it (which would push beat 3 out to
        // ~2000ms instead of ~1150ms if the bug were still present). Both bars are 1 beat long,
        // so the queue flips back to bar 0 *at* beat 3 too - beat 4 correctly waits out bar 0's
        // full 1000ms (due ~2150ms), so 1500ms is chosen to land after a fixed beat 3 (~1150ms)
        // but well before both a buggy beat 3 (~2000ms) and the always-correctly-delayed beat 4.
        // state.totalBeats is 0-indexed (it's captured before the post-beat increment), so
        // "a third beat has fired" reads as totalBeats == 2, not 3.
        Thread.sleep(1500)

        assertTrue(
            "expected a third beat within 1500ms of starting - totalBeats=" +
                "${MetronomeEngine.state.value.totalBeats}",
            MetronomeEngine.state.value.totalBeats >= 2,
        )
    }

    @Test
    fun `setUnitNoteValue edits only the active bar's denominator, clamped`() {
        MetronomeEngine.setUnitNoteValue(8) // bar 0 -> 4/8... well, x/8
        MetronomeEngine.addBarToQueue() // bar 1, a copy (unitNoteValue 8)
        MetronomeEngine.setUnitNoteValue(2)

        val queue = MetronomeEngine.timeSignatureQueue.value
        assertEquals(8, queue[0].unitNoteValue)
        assertEquals(2, queue[1].unitNoteValue)

        MetronomeEngine.setUnitNoteValue(0)
        assertEquals(1, MetronomeEngine.timeSignatureQueue.value[1].unitNoteValue)

        MetronomeEngine.setUnitNoteValue(999)
        assertEquals(MetronomeEngine.MAX_UNIT_NOTE_VALUE, MetronomeEngine.timeSignatureQueue.value[1].unitNoteValue)
    }

    @Test
    fun `nextQueueIndexAfterBar loops back to the first bar after the last in LOOP mode`() {
        assertEquals(1, MetronomeEngine.nextQueueIndexAfterBar(0, queueSize = 3, MetronomeEngine.QueueMode.LOOP))
        assertEquals(2, MetronomeEngine.nextQueueIndexAfterBar(1, queueSize = 3, MetronomeEngine.QueueMode.LOOP))
        assertEquals(0, MetronomeEngine.nextQueueIndexAfterBar(2, queueSize = 3, MetronomeEngine.QueueMode.LOOP))
    }

    @Test
    fun `nextQueueIndexAfterBar stays on the last bar once reached in ONCE mode`() {
        assertEquals(1, MetronomeEngine.nextQueueIndexAfterBar(0, queueSize = 3, MetronomeEngine.QueueMode.ONCE))
        assertNull(MetronomeEngine.nextQueueIndexAfterBar(2, queueSize = 3, MetronomeEngine.QueueMode.ONCE))
    }

    @Test
    fun `nextQueueIndexAfterBar never advances in MANUAL mode`() {
        assertNull(MetronomeEngine.nextQueueIndexAfterBar(0, queueSize = 3, MetronomeEngine.QueueMode.MANUAL))
        assertNull(MetronomeEngine.nextQueueIndexAfterBar(2, queueSize = 3, MetronomeEngine.QueueMode.MANUAL))
    }

    @Test
    fun `a multi-bar queue in LOOP mode actually advances during real playback`() {
        MetronomeEngine.setBeatsPerBar(1) // one beat per bar, so every beat is a bar boundary
        MetronomeEngine.addBarToQueue()
        MetronomeEngine.goToQueueBar(0)
        MetronomeEngine.setQueueMode(MetronomeEngine.QueueMode.LOOP)
        MetronomeEngine.setBpm(MetronomeEngine.MAX_BPM) // 150ms/beat = 150ms/bar, keeps this quick

        MetronomeEngine.start()
        // The first beat fires immediately without advancing (totalBeats == 0 guard); the second
        // beat, around the 150ms mark, is the first real bar-boundary advance (0 -> 1). Land well
        // after that but comfortably before the third beat would flip it back to 0.
        Thread.sleep(230)

        assertTrue(
            "expected at least the first advance (queue index 0 -> 1) to have happened by now - " +
                "totalBeats=${MetronomeEngine.state.value.totalBeats} queueIndex=${MetronomeEngine.queueIndex.value}",
            MetronomeEngine.state.value.totalBeats >= 1,
        )
        assertEquals(1, MetronomeEngine.queueIndex.value)
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
