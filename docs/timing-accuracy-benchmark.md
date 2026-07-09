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

**Run it**:
```sh
./gradlew connectedDebugAndroidTest --tests "*.FirstBeatTimingBenchmarkTest"
```
Results are logged (`adb logcat -s FirstBeatBenchmark`) and printed to the instrumentation
runner's own stdout - both the per-session beat-0 error and the steady-state (beats 3-8) mean/max,
plus every raw sample.

**Results**: *pending first on-device run* - no device was connected in the session that wrote
this benchmark. Update this section with real numbers (both the count-in-enabled and
count-in-disabled runs, ideally across more than one physical device) the next time one is
available, and track them here over time rather than overwriting - a single run is a data point,
not a verified fix.

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
