# Timing accuracy: targets, metrics, and benchmarks

Tracks a concrete, measured accuracy goal for the first beat (and, going forward, the engine's
timing generally) - not a vibe, a number, benchmarked against real hardware and revisited as this
gets re-measured. Companion to [`realtime-audio-roadmap.md`](realtime-audio-roadmap.md) (the
longer-term architecture direction) and [`publication_checklist.md`](publication_checklist.md)
(the manual on-device verification log for every round of this work so far).

## Where "+/- one clock cycle" actually lands

The request that prompted this doc was framed as an accuracy goal of "+/- a clock cycle." Taken
literally that's not a target this project can meet, and naming why matters more than quietly
substituting a different number:

- A clock cycle on a modern mobile SoC (~2-3 GHz) is roughly 0.3-0.5 **nanoseconds**. Audio itself
  doesn't have a finer resolution than one sample period - at 44.1 kHz, one sample is ~22.7
  **microseconds**, about 50,000x coarser than a CPU cycle. "Better than one sample" describes a
  digital audio signal that doesn't exist; there is no operational meaning to placing a click
  more precisely than the sample it occupies. Professional studio word-clock hardware - the gold
  standard this app's own `StreamingClickEngine` doc explicitly compares itself to (DAWs, Web
  Audio API, Ableton Link) - targets **sample-accurate** placement, not clock-cycle placement,
  for exactly this reason.
- So the real, physically-meaningful ceiling is **sample-accurate placement** (~23 microseconds at
  44.1 kHz), not one clock cycle. That ceiling is achievable *in principle* - it's what
  `StreamingClickEngine.mixPendingBeatIfDue` already computes toward - and Benchmark 1 below
  measures how close this engine actually gets to it on real hardware.
- The gap this whole investigation (Gap A: cold-rebuild-per-session; Gap B: beat 0's missing
  lead-scheduling window) has been chasing was never about that ceiling. It's entirely about
  **scheduling lead time** - whether the engine gets enough advance notice to *use* the
  sample-accurate mechanism it already has, not whether the mechanism itself is precise enough.
  That distinction is the actual finding worth carrying forward: the hardware is not the
  bottleneck here; the software's own scheduling model is.

## Research: how other systems solve this

Before trying a fourth guess at the residual gap (see Benchmark 1's hypotheses 1-2 below, both
already tried and measured), a round of research into prior art - not just reasoning from this
codebase alone:

- **Web Audio's "lookahead scheduler" pattern** ([Chris Wilson, "A Tale of Two Clocks"](https://web.dev/articles/audio-scheduling))
  is the direct industry precedent for `StreamingClickEngine`'s own approach: resolve a beat's
  audio ahead of a wall-clock timer, but place it against the audio context's own sample-accurate
  clock, not the timer's approximate firing time. `MetronomeEngine`/`StreamingClickEngine`'s split
  (resolve-ahead vs. place-precisely) already matches this pattern structurally - confirms the
  architecture chosen here isn't the wrong shape, which narrowed this round's search to *why a
  correctly-shaped mechanism was still landing late* rather than to redesigning the mechanism itself.
- **Android's own high-performance-audio guidance** ([AAudio](https://developer.android.com/ndk/guides/audio/aaudio/aaudio),
  [Oboe](https://developer.android.com/games/sdk/oboe/low-latency-audio)) is explicit that a
  **native callback** (not a Java `AudioTrack.write()` loop) is how professional apps get onto
  AudioFlinger's *fast* mixer path (SCHED_FIFO, a documented 2-3ms period -
  [source.android.com](https://source.android.com/docs/core/audio/latency/design)), and states
  outright: *"Audio latency is high for blocking write() ... Use a callback to get lower latency."*
  Oboe's own reference sample for exactly this use case
  ([RhythmGame](https://github.com/google/oboe/tree/main/samples/RhythmGame)) uses the identical
  "track a frame<->nanoTime reference pair, check if an event's frame falls in this callback's
  range" technique `StreamingClickEngine` already implements - independent confirmation the
  mechanism is right, the execution context (Kotlin thread vs. native audio callback) is the open
  question.
- **How actual commercial metronome apps solve this**: research into Pro Metronome, True
  Metronome, PolyNome (claiming ±20µs), and Flashtronome's own public statements all converge on
  the same answer - custom native audio engines, not stock SDK sample code, because (per
  Flashtronome's own description) "most app-store metronomes... fail to account for all the
  factors that can cause small inaccuracies to build up over time."
- **`AudioTrack.getMinBufferSize()` is explicitly documented as an estimate, not a latency
  guarantee** - both for the legacy Java API and its AAudio-side equivalent
  (`PROPERTY_OUTPUT_FRAMES_PER_BUFFER`, itself only "a hint... devices that report a very low
  buffer size may not necessarily be honest about their performance," per the
  [Oboe buffer-terminology wiki](https://github.com/google/oboe/wiki/TechNote_BufferTerminology)).
  Crucially, `getMinBufferSize()` takes no performance-mode parameter at all - it has no way to
  know a caller wants the low-latency path, and reports a size sized for the *ordinary* mixer.

That last point turned out to be the actual, concrete bug - see Benchmark 1's third hypothesis
below.

## Two things this measures, not one

**Placement precision** - once a beat is scheduled with adequate lead, how close does it land to
its intended sample? This is close to the Tier 0 ceiling by construction and is what Benchmark 1
(below) checks directly, using the engine's own frame<->nanoTime calibration running against real
`AudioTrack`/HAL behavior.

**Scheduling adequacy** - was there enough advance notice for that precise mechanism to engage at
all? This is what Gap A and Gap B were about, and what the first-beat-vs-steady-state comparison in
Benchmark 1 is actually measuring.

## Targets

| Tier | Target | Basis |
|---|---|---|
| 0. Physical ceiling | Sample-accurate (~23µs @ 44.1kHz) | Not a design choice - the finest resolution a digital audio signal has. Cited as a fact, not a target to hit. |
| 1. Placement precision (steady-state) | Within a handful of samples (< 1ms) of the intended target, once genuinely lead-scheduled | What `StreamingClickEngine` already claims for beat 1+. **Not yet met** - measured at ~11-13ms after the buffer-sizing fix below (down from ~47ms), a huge improvement but still short of sub-ms; closing the rest likely needs the native-callback path research points to (see "Research," above). |
| 2. First-beat vs steady-state gap (the target this round is actually about) | **≤ 10ms**, stretch **≤ 5ms** | ~10ms is a commonly-cited practical bar for "tight" in live-performance/MIDI contexts; ~5ms approaches published just-noticeable-difference figures for onset asynchrony under critical listening by trained musicians. Neither number is this project's own novel research - both are standard reference points from audio/music-perception literature, cited here as the honest "below human perception" bar the original request asked for, since "one clock cycle" isn't a coherent one. **Met**, 2026-07-09: ~2ms excess measured (down from ~128ms at the start of this investigation) - see Benchmark 1's results table. |
| 3. True acoustic ground truth (speaker → ear) | Not yet measured | Needs a microphone-loopback self-test - deferred, see below. |

Tier 2 is the number this round's fix (the warm-keep + count-in work) was actually aimed at, and
the one [Benchmark 1](#benchmark-1-first-beat-vs-steady-state-placement-error-on-device) reports
directly, before/after the count-in, across repeated sessions - not just a fresh install's very
first press.

## Benchmark 1: first-beat vs steady-state placement error, on-device

`app/src/androidTest/java/media/quaternion/qmetronome/benchmark/FirstBeatTimingBenchmarkTest.kt`.
Requires a connected device (Robolectric's `AudioTrack` shadow doesn't model real HAL timing -
this is exactly the thing it can't verify, per `StreamingClickEngineTest.kt`'s own kdoc).

**What it measures**: for every beat actually mixed into the real audio stream across four
back-to-back play/stop sessions (10 beats each, 120 BPM, matching real usage - not just a single
cold-start session), the delta between its intended target nanoTime and the real nanoTime it
landed at, read directly from the engine's own self-calibrated frame<->nanoTime mapping - no
microphone needed, since the mixing decision itself already carries the ground truth this
benchmark cares about. Runs twice: once at the shipped default (100ms count-in cap) and once with
the count-in disabled (0ms cap, today's pre-fix behavior) - same device, same session shape, so the
report shows the actual measured difference, not an assertion of one.

**Run it** (AGP's connected-test task takes its class filter differently from `test`'s `--tests`):
```sh
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=media.quaternion.qmetronome.benchmark.FirstBeatTimingBenchmarkTest
adb logcat -d -s FirstBeatBenchmark:I *:S   # after the run, to read the report back
```
Results are logged (`adb logcat -s FirstBeatBenchmark`) and printed to the instrumentation
runner's own stdout - both the per-session beat-0 error and the steady-state (beats 3-8) mean/max,
plus every raw sample.

**Results**:

| Date | Device | Count-in cap | Beat 0 \|error\| (mean / max, ms) | Steady-state \|error\| (mean / max, ms) | Notes |
|---|---|---|---|---|---|
| 2026-07-09 | Nothing A024 (SDK 36) | 0ms (old behavior) | 174.94 / 176.32 | 46.88 / 51.54 | Excess over steady-state baseline: **~128ms**. Remarkably consistent across all 4 sessions (173.6-176.3ms) - this is a real, repeatable gap, not noise. |
| 2026-07-09 | Nothing A024 (SDK 36) | 100ms (shipped default) | 83.16 / 103.12 | 47.44 / 51.37 | One-shot `StreamingClickEngine.scheduleBeat` push. Excess over steady-state baseline: **~36ms** (session 0 was an outlier at 103ms - likely a cold-run/first-session-of-the-test-process effect; sessions 1-3 clustered tightly at 76-77ms). |
| 2026-07-09 | Nothing A024 (SDK 36) | 100ms (shipped default) | 115.04 / 187.50 | 47.91 / 51.96 | Redesign attempt: beat 0 routed through the same `startAudioScheduling`/`refreshPredictedSchedule` loop as every later beat, **plus** kept that loop's coroutine warm/idle across sessions (mirroring `StreamingClickEngine`'s own warm-keep fix). Excess over baseline: **~43ms (sessions 1-3)** - a regression, not an improvement. Root cause and revert below. |
| 2026-07-09 | Nothing A024 (SDK 36) | 100ms (shipped default) | 101.26 / 168.86 | 47.20 / 49.72 | Same loop-routed redesign, warm-keep **reverted** (coroutine cancelled/relaunched every session again). Excess over baseline: **~31-36ms (sessions 1-3: 31.28, 29.40, 33.91)** - back to, if anything marginally better than, the one-shot row above. |
| 2026-07-09 | Nothing A024 (SDK 36) | 100ms (shipped default) | 103.45 / 179.62 | 46.94 / 48.28 | Added `LeadMarginCalibrator` (self-calibrating margin, hypothesis 3 below). Excess over baseline: **~31ms (sessions 1-3: 30.6, 29.9, 32.5)** - no measurable change despite the correction growing substantially over the run (confirmed via a temporary diagnostic, see hypothesis 3). |
| 2026-07-09 | Nothing A024 (SDK 36) | 100ms (shipped default) | 44.85 / 133.38 | 13.16 / 16.99 | **Buffer sized from `AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER` instead of `getMinBufferSize()`** (hypothesis 4 - the actual fix, see below). Excess over baseline: **~2ms (sessions 1-3: 1.52, 4.88, 0.14)**. Steady-state placement precision itself also improved ~3.6x (47ms → 13ms). **Target met.** Sample count dropped slightly (49 vs. the usual 52) - sessions 1-3 each lost their very last beat (12); with far less lag, the last beat now sometimes lands fractionally after the benchmark's own fixed `SESSION_DURATION_MS` window closes and `stop()` is called - a benign benchmark-harness margin artifact, not a dropped click in the app itself. |
| 2026-07-09 | Nothing A024 (SDK 36) | 0ms (old behavior) | 63.94 / 66.60 | 11.07 / 12.05 | Same buffer fix, count-in still disabled - confirms the buffer fix alone doesn't substitute for the count-in (Gap B is a separate, structural cause): excess is still ~53ms without it. |

**Interpretation, honestly - four hypotheses, in the order they were actually tried**:

1. **"One-shot schedule vs. the repeated-refinement polling loop"** - hypothesized that beat 0's
   one-shot `scheduleBeat` call underperformed because, unlike steady-state beats, it never got
   re-checked/refined as the deadline approached. Redesigned `start()` to feed beat 0 through the
   *exact same* `startAudioScheduling`/`refreshPredictedSchedule` loop via a synthetic
   `lastBeatNanos` (row 2 above). **Measured result: no difference** (~36ms excess either way).
   Reason, traced afterward: the synthetic anchor is fixed, so every poll of the loop recomputes
   the identical target - "repeated refinement" only does anything when the *predicted value itself*
   changes between polls (real bpm/tempo drift), which a one-time synthetic anchor never does.
   Kept the redesign anyway (row 3/4) since routing every beat through one mechanism is still better
   architecture than a bespoke parallel formula, even without a measured win.
2. **"Cold-dispatch tax on the scheduling coroutine itself"** - reasoning by analogy to
   `StreamingClickEngine`'s own fix (a real, expensive `AudioTrack` rebuild-per-session, closed by
   keeping it warm): if `startAudioScheduling`'s coroutine was also being torn down and relaunched
   every session, maybe *that* relaunch cost was the residual. Made it idempotent and stopped
   cancelling it in `stop()` (row 3). **Measured result: a regression** (~43ms excess, worse than
   ~36ms, with a wider spread - one session at 187ms vs. the prior row's tightest cluster at
   76-77ms). Traced why: this coroutine, unlike the `AudioTrack`/writer, owns no expensive resource
   to protect - keeping it "warm" just means it's asleep inside its own `AUDIO_LOOKAHEAD_IDLE_POLL_MS`
   (25ms) `delay()` while gated off between sessions, and a `delay()` already in flight can't be
   woken early just because `start()` flips `isPlaying` true - it only notices on its *next* wake, up
   to 25ms later. A fresh relaunch has no such in-flight sleep to wait out: its first iteration runs
   immediately, already seeing the new state. **Reverted** (row 4) - this was the wrong lifecycle for
   this specific coroutine, the opposite lesson from `StreamingClickEngine`'s, not a smaller version
   of the same fix. `startAudioScheduling`'s own kdoc now documents this explicitly so the mistake
   isn't repeated.
3. **"Self-calibrating lead margin"** - the research above confirmed `getMinBufferSize()` is only
   an estimate, so the natural next guess was to correct it empirically: `LeadMarginCalibrator`
   (new class) tracks an exponential moving average of `actualNanos - targetNanos` for every beat
   actually mixed, and adds that correction on top of the raw buffer estimate wherever
   `MetronomeEngine` asks for lead margin. Pure logic, unit-tested in isolation
   (`LeadMarginCalibratorTest`), no Robolectric/`AudioTrack` involvement needed. **Measured result:
   no change at all** - row 5 above shows the same ~31ms excess as before, despite a temporary
   diagnostic (added to `mixPendingBeatIfDue`, then removed) confirming the correction *was*
   growing as designed (from ~27ms up past ~65ms of added margin over the course of the run) and
   *every single beat*, steady-state included, was landing in `mixPendingBeatIfDue`'s "already past
   the earliest available frame" clamp branch, regardless of how much extra lead was requested.
   That last fact is what broke the theory open: if more lead genuinely doesn't help, the problem
   isn't *how early we ask* - something downstream ignores how early we ask. Traced why: the
   writer's own `AudioTrack.write()` calls, timestamped via the same diagnostic, were blocking for
   ~40-45ms each - eight to nine times longer than the ~5ms chunk period the code was designed
   around - meaning the writer could only ever *notice* a freshly-scheduled beat once every ~40-45ms,
   no matter how far ahead `scheduleBeat` had been called. Not reverted (a correction that tracks
   real per-device behavior is harmless and still theoretically useful if the buffer-size estimate
   is ever wrong in the other direction), but it wasn't the fix.
4. **"Why is `write()` blocking for 40-45ms instead of ~5ms"** - the research above supplied the
   answer directly: Android only routes a track through AudioFlinger's *fast* mixer (a documented
   2-3ms period) if it qualifies for the fast path, and `leadMarginNanos()`'s own logged value
   during the same diagnostic run read **120ms** - `AudioTrack.getMinBufferSize()`'s reported
   buffer, on this device, was two orders of magnitude larger than a real low-latency burst (a few
   hundred frames, a handful of ms), and `getMinBufferSize()` has no `PERFORMANCE_MODE_LOW_LATENCY`
   parameter at all - it has no way to size itself for the fast path even when the `AudioTrack`
   requests it. A track requesting *that* large a buffer is squarely in "ordinary mixer" territory
   (documented ~20-40ms period) regardless of the performance-mode flag also being set. **The fix**:
   `StreamingClickEngine.configureFromDevice(context)` (new method, called once from
   `MetronomeEngine.attach`) queries `AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER` - the actual
   native low-latency burst size - and sizes the buffer to twice that (double buffering, the same
   choice Oboe automates internally), falling back to the original `getMinBufferSize()`-based sizing
   if the property is unavailable. **Measured result: row 6 above** - steady-state placement
   precision improved from ~47ms to ~13ms (a 3.6x improvement, still short of the sub-ms Tier 1
   target but a different order of magnitude), and combined with the existing count-in fix, beat
   0's excess dropped from ~31-36ms to **~2ms** - inside the ≤10ms target with room to spare, close
   to the ≤5ms stretch goal. Row 7 (count-in disabled, same buffer fix) confirms the two fixes are
   independent: without the count-in, excess is still ~53ms, since Gap B (beat 0's missing
   lead-scheduling window) is a structurally separate cause from this buffer-sizing bug.

**What's left, honestly**: Tier 2 (the target this whole investigation was actually about) is met.
Tier 1 (steady-state placement precision) is not - ~13ms is a large, real improvement over ~47ms but
still far from the sub-ms ceiling `StreamingClickEngine`'s own design already reaches for. The
research above is consistent about what the next lever would be: a Java `AudioTrack.write()` loop,
however well-tuned its buffer size, is still not the audio HAL's own callback thread - AudioFlinger's
fast mixer runs at SCHED_FIFO priority Kotlin/Java code cannot request for itself (`Process.
THREAD_PRIORITY_URGENT_AUDIO`, already used by this codebase's `newTimingDispatcher`, is a nice-value
boost under the ordinary CFS scheduler, not the real-time SCHED_FIFO class the fast mixer itself
gets). Reaching sub-ms placement precision - and, per the professional-metronome-app research above,
matching what apps like PolyNome claim - would mean migrating `StreamingClickEngine` to a native
AAudio/Oboe callback (`PERFORMANCE_MODE_LOW_LATENCY` + `SHARING_MODE_EXCLUSIVE`/MMAP), which no
Java-only API can request. That's a real scope increase (this project has no NDK/C++ toolchain
today) - flagged here as the honest ceiling of what this round's approach can reach, not undertaken
without the maintainer explicitly choosing to take it on.

## Deferred: true acoustic ground truth

Benchmark 1 measures the engine's own scheduling/mixing precision against real hardware - it does
not measure true speaker-to-ear latency (DAC settling, physical propagation, room acoustics). A
future `androidTest` could add that by capturing the device's own microphone (`AudioRecord`) while
the click is playing, detecting the click's onset in the recorded PCM buffer, and comparing against
the same target timestamps Benchmark 1 already reads. This is deliberately **not implemented in
this round**: `RECORD_AUDIO` is a real, user-visible permission, and while an `androidTest`-only
declaration doesn't touch the shipped app's own manifest or `PRIVACY.md` claims (test permissions
don't merge into the production APK), a mic-based correlator also needs real signal-processing
tuning (onset-detection thresholding against ambient noise, speaker/mic hardware variance across
devices) that deserves its own scoped pass rather than being bundled in as an afterthought here.
Proposed, not built - the same "scoped, not boxed out" precedent `realtime-audio-roadmap.md`
already established for this project's other not-yet-built timing work.

## Why this doc exists separately from `realtime-audio-roadmap.md`

That doc is architecture direction for *new* capability (multi-channel routing, polyrhythm,
per-beat-type MIDI actions). This one is the opposite direction: verifying, with numbers, that the
capability already shipped is delivering what it claims to, and keeping a running log of that
verification rather than trusting the last round's fix indefinitely. See
[`governance/qm/perspectives/`](../governance/qm/perspectives/) for a broader, non-binding
reflection on why this gap (real device compute vs. delivered scheduling precision) exists at all
across the software stack, not just in this app.
