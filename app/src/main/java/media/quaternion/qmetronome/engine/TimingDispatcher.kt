package media.quaternion.qmetronome.engine

import java.util.concurrent.Executors
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher

/**
 * A small, dedicated coroutine dispatcher named [threadName] (2 threads, not [Dispatchers.Default]'s
 * full core count) - used by [MetronomeEngine] and `MidiClockSender` to isolate timing-critical
 * work (beat firing, audio lookahead scheduling, outgoing clock ticks) from `Dispatchers.Default`'s
 * shared, multi-core general-purpose thread pool. Without this, a beat's firing time shares
 * scheduling with whatever *else* happens to be running on that same pool at the moment - unrelated
 * background work (widget updates, image decode, GC-adjacent housekeeping) can delay exactly the
 * callback a metronome can least afford to delay. Each subsystem gets its *own* pool (not a shared
 * one between engine and MIDI-out) precisely so isolating one from `Dispatchers.Default` doesn't
 * just recreate the same contention between the two of them instead.
 *
 * **Why 3 threads, not 1**: tried a genuine single thread first - measurably broke it. A single
 * engine already runs up to three independently-polling loops at once (the beat clock, the render
 * loop, and - when a negative audio offset is active - a tight ~1ms-slice lookahead poll while
 * waiting for the real beat to catch up). Serializing all of those onto one thread let the
 * lookahead loop's tight poll starve the actual beat-firing coroutine at fast tempos (measured:
 * zero beats fired in 700ms at 400 BPM with a single thread, where several were expected) - the
 * exact opposite of the goal. Two threads mostly fixed it but still measurably added scheduling
 * jitter to an unrelated bar-boundary timing test versus `Dispatchers.Default`'s much larger core
 * count; one thread per concurrent loop removes the contention those loops could still put on each
 * other while keeping the whole subsystem off `Dispatchers.Default`.
 *
 * Lives for the process's entire lifetime, same as the singleton object that owns it - there is no
 * shutdown path, matching how `MetronomeEngine`/`MidiClockSender` themselves are never torn down
 * during normal app life.
 */
fun newTimingDispatcher(threadName: String): ExecutorCoroutineDispatcher {
    // Propagate the creating thread's context classloader to each new thread - a freshly
    // constructed Thread otherwise defaults to inheriting its *parent* thread's classloader per
    // the JVM spec, which is normally fine, but under Robolectric's per-test sandboxed classloader
    // this matters concretely: without it, code running on these dedicated threads can silently
    // fail to resolve Robolectric's shadowed framework classes.
    val callerClassLoader = Thread.currentThread().contextClassLoader
    var threadIndex = 0
    return Executors.newFixedThreadPool(THREAD_COUNT) { runnable ->
        Thread(runnable, "$threadName-${threadIndex++}").apply { contextClassLoader = callerClassLoader }
    }.asCoroutineDispatcher()
}

private const val THREAD_COUNT = 3
