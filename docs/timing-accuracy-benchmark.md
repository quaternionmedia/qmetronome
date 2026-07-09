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
| 1. Placement precision (steady-state) | Within a handful of samples (< 1ms) of the intended target, once genuinely lead-scheduled | What `StreamingClickEngine` already claims for beat 1+; Benchmark 1 checks whether real hardware actually delivers it. |
| 2. First-beat vs steady-state gap (the target this round is actually about) | **≤ 10ms**, stretch **≤ 5ms** | ~10ms is a commonly-cited practical bar for "tight" in live-performance/MIDI contexts; ~5ms approaches published just-noticeable-difference figures for onset asynchrony under critical listening by trained musicians. Neither number is this project's own novel research - both are standard reference points from audio/music-perception literature, cited here as the honest "below human perception" bar the original request asked for, since "one clock cycle" isn't a coherent one. |
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
| 2026-07-09 | Nothing A024 (SDK 36) | 100ms (shipped default) | 101.26 / 168.86 | 47.20 / 49.72 | Same loop-routed redesign, warm-keep **reverted** (coroutine cancelled/relaunched every session again). Excess over baseline: **~31-36ms (sessions 1-3: 31.28, 29.40, 33.91)** - back to, if anything marginally better than, the one-shot row above. This is the current shipped state. |

**Interpretation, honestly - three hypotheses, in the order they were actually tried**:

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
3. **Where that leaves the ~31-36ms floor**: still open. It survives across three independent
   on-device measurements (one-shot, loop-routed-with-relaunch, loop-routed-with-warm-keep-reverted)
   and both attempted mitigations left it essentially unchanged - which is itself informative: this
   residual isn't being caused by anything this round touched (one-shot vs. loop, warm vs. cold
   coroutine). The more likely remaining cause, not yet tested: see the independent finding below,
   and the proposed next step after it.

**An independent finding, and what it now points to**: steady-state beats on this device carry a
consistent ~47ms baseline `|error|` in *this benchmark's own measurement*, not just beat 0, stable
across all four runs above regardless of what changed. The working theory is still that this
reflects `StreamingClickEngine`'s real buffer-ahead depth (`leadMarginNanos()`) showing up in the
measurement rather than genuine placement imprecision - but that theory has a testable, unverified
half: `leadMarginNanos()` is *computed* from `AudioTrack.getMinBufferSize()`, not measured from
actual device behavior. If the real HAL/output pipeline holds more lead than that computed buffer
size implies, the scheduling loop would systematically under-estimate how early it needs to call
`scheduleBeat`, and *every* beat - steady-state included - would land a constant amount late, which
is exactly the shape of the ~47ms baseline. If that's right, it's also very plausibly why beat 0
specifically compounds worse: its very first scheduling decision has no prior beat's measured error
to lean on, while by beat 3-8 nothing about steady-state's *placement* has actually improved either
- they're just past the point where the benchmark starts averaging, not past the point where the
bias stops applying.

**Proposed next step (not implemented this round, given its size and risk - a candidate for the
next round, not a blind addition to this one)**: make the lead margin self-calibrating instead of a
static buffer-size estimate. `mixListenerForTesting`'s mechanism already computes, for every beat
actually mixed, the exact delta between intended and actual placement - in production (not just the
test), a rolling measurement of that same delta could feed back into `leadMarginNanos`'s effective
value, closing the loop between "how far ahead we think we need to schedule" and "how far ahead we
actually needed to." This is a materially bigger change than anything tried this round (it would
affect every beat's placement, not just beat 0's), and needs its own care against overshoot - firing
a click *early* is a more noticeable defect than firing it slightly late, so a feedback controller
here needs a deliberately asymmetric/conservative correction, not a naive average. Proposing it here
rather than building it blind, per this round's own lesson (mitigation #2 above) that a plausible-
sounding analogy to a previous fix isn't the same thing as a measured one.

| Date | Device | Count-in cap | Beat 0 \|error\| (mean / max, ms) | Steady-state \|error\| (mean / max, ms) | Notes |
|---|---|---|---|---|---|
| _pending_ | | | | | |

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
