package media.quaternion.qmetronome.engine

import android.os.Process
import android.util.Log
import java.util.concurrent.Executors
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher

/**
 * One dedicated, elevated-priority thread named [threadName] - used by [MetronomeEngine] (a
 * separate one each for its clock loop, render loop, audio-scheduling loop, and streaming audio
 * writer - see `StreamingClickEngine`) and `MidiClockSender` to isolate timing-critical work from
 * both `Dispatchers.Default`'s shared, multi-core general-purpose pool *and* from each other.
 *
 * **One dedicated thread per role, not a shared pool.** A shared pool sized to the number of
 * concurrent loops "usually" avoids contention, but nothing about a generic executor guarantees it
 * won't occasionally schedule two roles onto the same worker while another sits idle. Giving each
 * role its own thread removes that possibility entirely - full isolation, not probabilistic
 * isolation. (History: a genuine single *shared* thread measurably broke beat-firing outright - a
 * tight audio-lookahead poll starved the clock loop at fast tempos; a shared pool of 2 mostly
 * fixed it but still measurably added jitter to an unrelated timing test versus
 * `Dispatchers.Default`'s much larger core count. One dedicated thread per role is the version of
 * this fix that doesn't depend on how many roles happen to exist today.)
 *
 * **Elevated OS thread priority.** `Process.THREAD_PRIORITY_URGENT_AUDIO` is the platform's own
 * answer for exactly this class of work - a thread that can't tolerate normal scheduling
 * preemption. Best-effort: wrapped in `try`/`catch` since some OEM/cgroup configurations may not
 * honor it, and a metronome thread that can't get elevated priority should still run at normal
 * priority rather than crash.
 *
 * Lives for the process's entire lifetime, same as the singleton object that owns it - there is no
 * shutdown path, matching how `MetronomeEngine`/`MidiClockSender` themselves are never torn down
 * during normal app life.
 */
fun newTimingDispatcher(threadName: String): ExecutorCoroutineDispatcher {
    // Propagate the creating thread's context classloader to the new thread - a freshly
    // constructed Thread otherwise defaults to inheriting its *parent* thread's classloader per
    // the JVM spec, which is normally fine, but under Robolectric's per-test sandboxed classloader
    // this matters concretely: without it, code running on this dedicated thread can silently
    // fail to resolve Robolectric's shadowed framework classes.
    val callerClassLoader = Thread.currentThread().contextClassLoader
    return Executors.newSingleThreadExecutor { runnable ->
        Thread({
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            } catch (e: Exception) {
                Log.w("TimingDispatcher", "Couldn't elevate '$threadName' to urgent-audio priority; continuing at normal priority", e)
            }
            runnable.run()
        }, threadName).apply { contextClassLoader = callerClassLoader }
    }.asCoroutineDispatcher()
}
