package media.quaternion.qmetronome.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Lifecycle/API-safety coverage only - see `docs/realtime-audio-roadmap.md`'s plan notes: a
 * genuinely sample-accurate placement claim can't be verified against Robolectric's `AudioTrack`
 * shadow, which doesn't model real HAL/buffer timing. What *is* worth guarding here is that the
 * engine never throws across its public lifecycle, and that [StreamingClickEngine.scheduleBeat]
 * behaves safely (doesn't crash) whether called before, during, or after a session.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StreamingClickEngineTest {

    private lateinit var engine: StreamingClickEngine
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        engine = StreamingClickEngine()
        scope = CoroutineScope(Dispatchers.Default)
    }

    @After
    fun tearDown() {
        engine.stop()
        scope.cancel()
    }

    @Test
    fun `has not failed warmup immediately after construction, before start is ever called`() {
        assertFalse(engine.hasFailedWarmup())
    }

    @Test
    fun `scheduleBeat before start is a harmless no-op`() {
        engine.scheduleBeat(0L, ClickSound.BAR, System.nanoTime())
    }

    @Test
    fun `start-stop-start is safe and idempotent`() {
        engine.start(scope)
        engine.start(scope) // second call while already running - should just return true, not rebuild
        engine.stop()
        engine.stop() // second stop - should be a harmless no-op
        engine.start(scope)
    }

    @Test
    fun `setSpec and getSpec round-trip for every sound, before and after start`() {
        val customSpec = ClickSpec(ClickWaveform.SQUARE, frequencyHz = 880f, durationMs = 30, gain = 0.5f)
        engine.setSpec(ClickSound.ACCENT, customSpec)
        assert(engine.getSpec(ClickSound.ACCENT) == customSpec)

        engine.start(scope)
        engine.setSpec(ClickSound.REGULAR, customSpec)
        assert(engine.getSpec(ClickSound.REGULAR) == customSpec)
    }

    @Test
    fun `scheduleBeat while running does not throw for any sound including null (muted)`() {
        engine.start(scope)
        engine.scheduleBeat(0L, ClickSound.BAR, System.nanoTime())
        engine.scheduleBeat(1L, null, System.nanoTime() + 100_000_000L)
        engine.scheduleBeat(2L, ClickSound.ACCENT, System.nanoTime() + 200_000_000L)
    }

    @Test
    fun `scheduleBeat is safe to call repeatedly for the same beat, refining the target`() {
        engine.start(scope)
        val base = System.nanoTime()
        engine.scheduleBeat(5L, ClickSound.REGULAR, base + 50_000_000L)
        engine.scheduleBeat(5L, ClickSound.REGULAR, base + 40_000_000L)
        engine.scheduleBeat(5L, ClickSound.REGULAR, base + 30_000_000L)
    }

    @Test
    fun `leadMarginNanos is zero before start, non-negative after`() {
        assertEquals(0L, engine.leadMarginNanos())
        engine.start(scope)
        assertTrue(
            "expected a non-negative lead margin once running, got ${engine.leadMarginNanos()}",
            engine.leadMarginNanos() >= 0L,
        )
    }

    @Test
    fun `leadMarginNanos returns to zero after stop`() {
        engine.start(scope)
        engine.stop()
        assertEquals(0L, engine.leadMarginNanos())
    }
}
