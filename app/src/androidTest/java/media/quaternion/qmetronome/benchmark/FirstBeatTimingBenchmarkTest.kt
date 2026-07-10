package media.quaternion.qmetronome.benchmark

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import media.quaternion.qmetronome.engine.DEFAULT_AUDIO_OFFSET_MS
import media.quaternion.qmetronome.engine.MetronomeEngine
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import kotlin.math.abs

/**
 * Measures qMetronome's *real* first-beat placement error, on real hardware - the one thing
 * Robolectric's `AudioTrack` shadow structurally cannot do (see `StreamingClickEngineTest.kt`'s
 * own kdoc: "a genuinely sample-accurate placement claim can't be verified against Robolectric's
 * AudioTrack shadow, which doesn't model real HAL/buffer timing"). No microphone/acoustic capture
 * needed: [StreamingClickEngine.setMixListenerForTesting] (reached via
 * [MetronomeEngine.setStreamingMixListenerForTesting]) reports, for every beat actually mixed into
 * the real stream, its intended target nanoTime against the real nanoTime it landed at - computed
 * from the *same* self-calibrated frame<->nanoTime mapping the engine uses to decide placement in
 * the first place, but now running against real `AudioTrack.getTimestamp()`/HAL behavior instead of
 * a shadow that never models it. The delta *is* this engine's real placement error.
 *
 * This does not measure true acoustic latency (speaker-to-ear propagation, DAC settling, etc.) -
 * see `docs/timing-accuracy-benchmark.md` for why that's a deliberately deferred, separate
 * follow-up (it needs a microphone-loopback self-test, a real new permission surface worth its own
 * decision) and for what this benchmark's numbers do and don't cover. What this *does* capture
 * directly is the exact question this round of work is about: is beat 0 of a session placed as
 * precisely as the beats after it, on real hardware, across repeated play/stop sessions - not just
 * the very first one after a fresh install.
 *
 * Run via `./gradlew connectedDebugAndroidTest
 * -Pandroid.testInstrumentationRunnerArguments.class=media.quaternion.qmetronome.benchmark.FirstBeatTimingBenchmarkTest`
 * with a device attached (AGP's connected-test task doesn't take `test`'s `--tests` filter);
 * results are logged (tag `FirstBeatBenchmark`) and printed to stdout, visible via
 * `adb logcat -s FirstBeatBenchmark` or the instrumentation test runner's own output. See
 * `docs/timing-accuracy-benchmark.md` for the actual measured results this has produced so far.
 */
@RunWith(AndroidJUnit4::class)
class FirstBeatTimingBenchmarkTest {

    private data class Sample(val session: Int, val totalBeats: Long, val errorNanos: Long)

    @Before
    fun setUp() {
        MetronomeEngine.attach(InstrumentationRegistry.getInstrumentation().targetContext)
        if (MetronomeEngine.state.value.isPlaying) MetronomeEngine.stop()
    }

    @After
    fun tearDown() {
        MetronomeEngine.stop()
        MetronomeEngine.setStreamingMixListenerForTesting(null)
    }

    @Test
    fun measureFirstBeatAcrossRepeatedSessions_shippedDefault() {
        runBenchmark(countInCapMs = 100f, label = "shipped default (100ms count-in cap)")
    }

    @Test
    fun measureFirstBeatAcrossRepeatedSessions_countInDisabled() {
        // The "opt back out" case (0 = today's pre-count-in behavior for beat 0's audio lead) -
        // run side by side with the shipped default so the report shows the actual, measured
        // difference the count-in makes, not just an assertion that it's "better."
        runBenchmark(countInCapMs = 0f, label = "count-in disabled (0ms cap - old behavior)")
    }

    private fun runBenchmark(countInCapMs: Float, label: String) {
        val samples = Collections.synchronizedList(mutableListOf<Sample>())
        MetronomeEngine.setClickEnabled(true)
        MetronomeEngine.setBpm(BPM)
        // The real shipped default (see DEFAULT_AUDIO_OFFSET_MS's own kdoc - true zero, not a
        // guessed pre-roll) rather than a hardcoded lead value, so this benchmark measures what a
        // fresh install actually behaves like, not a hand-picked scenario.
        MetronomeEngine.setAudioOffsetMs(DEFAULT_AUDIO_OFFSET_MS)
        MetronomeEngine.setFirstBeatCountInCapMs(countInCapMs)

        repeat(SESSION_COUNT) { session ->
            MetronomeEngine.setStreamingMixListenerForTesting { totalBeats, targetNanos, actualNanos ->
                samples.add(Sample(session, totalBeats, actualNanos - targetNanos))
            }
            MetronomeEngine.start()
            Thread.sleep(SESSION_DURATION_MS)
            MetronomeEngine.stop()
            MetronomeEngine.setStreamingMixListenerForTesting(null)
            Thread.sleep(SETTLE_BETWEEN_SESSIONS_MS) // like a real user glancing at the screen
        }

        logReport(label, samples)
    }

    private fun logReport(label: String, samples: List<Sample>) {
        val beatZeroErrorsMs = samples.filter { it.totalBeats == 0L }.map { it.errorNanos / 1_000_000.0 }
        val steadyStateErrorsMs = samples
            .filter { it.totalBeats in STEADY_STATE_BEAT_RANGE }
            .map { abs(it.errorNanos) / 1_000_000.0 }

        val report = buildString {
            appendLine("=== First-beat timing benchmark: $label ===")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Sessions: $SESSION_COUNT, ${BPM.toInt()} BPM, samples captured: ${samples.size}")
            appendLine("Beat 0 |error| per session (ms): ${beatZeroErrorsMs.map { "%.2f".format(abs(it)) }}")
            if (beatZeroErrorsMs.isNotEmpty()) {
                appendLine("Beat 0 |error|: mean=%.2fms max=%.2fms".format(beatZeroErrorsMs.map { abs(it) }.average(), beatZeroErrorsMs.maxOf { abs(it) }))
            }
            if (steadyStateErrorsMs.isNotEmpty()) {
                appendLine(
                    "Steady-state (beat index $STEADY_STATE_BEAT_RANGE) |error|: mean=%.2fms max=%.2fms n=%d"
                        .format(steadyStateErrorsMs.average(), steadyStateErrorsMs.max(), steadyStateErrorsMs.size),
                )
            }
            appendLine("Raw samples (session, totalBeats, errorMs):")
            samples.sortedWith(compareBy({ it.session }, { it.totalBeats })).forEach {
                appendLine("  session ${it.session}, beat ${it.totalBeats}: %.2fms".format(it.errorNanos / 1_000_000.0))
            }
        }
        Log.i(TAG, report)
        println(report) // also captured by the instrumentation runner's own stdout
    }

    private companion object {
        const val TAG = "FirstBeatBenchmark"
        const val BPM = 120f
        const val SESSION_COUNT = 4
        const val BEATS_PER_SESSION = 10
        const val SESSION_DURATION_MS = (BEATS_PER_SESSION * (60_000L / 120L)) + 1_000L // + settle margin
        const val SETTLE_BETWEEN_SESSIONS_MS = 800L
        val STEADY_STATE_BEAT_RANGE = 3L..8L
    }
}
