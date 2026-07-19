# Real-time audio/MIDI roadmap: multi-channel, multi-thread, per-beat-type actions

**Status: #3 implemented (2026-07-18); #1 and #2 remain scoping only, not an ADR.** This doc exists
so the longer-term direction was written down for deliberate future batching instead of being
forgotten or accidentally boxed out by today's single-track/single-clock design. Written alongside
the v0.0.25/v0.0.26-era fix for a real audio-timing bug at high tempo, later followed by a full
sample-clocked streaming rewrite of the audio path itself (see `docs/publication_checklist.md`'s
Technical Polish section, `engine/StreamingClickEngine.kt`, and
`MetronomeEngine.startAudioScheduling()`'s kdoc) - that work was a prerequisite for #3, not a step
toward it, and remains one for #1/#2. #3 shipped as `midi/MidiActionSender` - see
`governance/qm/adr/DRAFT-per-beat-type-midi-action-routing.md` for the ratified-pending decision
record and `readme/glossary.md`'s own `MidiActionSender` entry; the rest of this doc's #3 sections
are left as the original scoping analysis (confirmed accurate by the actual implementation, not
rewritten to narrate it) with a status note where the reality diverged.

## The direction

Three related capabilities, roughly in the order they'd naturally build on each other:

1. **Independent audio channel routing per beat type.** Today, `BAR`/`ACCENT`/`REGULAR` all play
   through the same output (whatever `AudioAttributes.USAGE_ASSISTANCE_SONIFICATION` resolves to
   on the device). The goal: let each beat type route to its own destination - e.g. a different
   output device, a different app-level "channel" a DAW or mixer could address independently, or
   distinct stereo panning/routing within the same output. **Still open** - `ClickSound` itself grew
   two more entries (`STRONG_ACCENT`, `CUSTOM`) as part of #3 below, but they still share one output
   the way `BAR`/`ACCENT`/`REGULAR` always have.
2. **Multiple simultaneous, independently-tempo'd beat "threads".** True polyrhythm/polymeter -
   more than one clock running concurrently (e.g. a 3-against-4 pattern, or an entirely separate
   tempo track layered on top of the main one), each with its own bar queue, its own audio
   routing, potentially its own visualizer. **Still open** - the multi-*phrase* bar queue shipped
   alongside #3 (`engine/Phrase.kt`) is a different thing: still one clock, one active phrase playing
   at a time, sequential song-form sections rather than concurrent polyrhythm. See that file's own
   kdoc for how it relates to (and doesn't presuppose) this item.
3. **Per-beat-type MIDI action routing - implemented.** `MidiClockSender` still only ever sends the
   shared clock pulse (`0xF8`); a *second*, independent sender (`midi/MidiActionSender`) now maps
   each beat's resolved `ClickSound` (`BAR`/`REGULAR`/`ACCENT`/`STRONG_ACCENT`/`CUSTOM`) to its own
   configurable Note-on/off or CC message, sent to the same `MidiReceiver` destinations
   `MidiClockSender` already uses. Reaching beat types beyond the downbeat needed an authoring UI
   that didn't exist before this round either - see `ui/TimeSignatureEntryDialog.kt`'s per-beat
   accent chips. The "or a beat from a specific thread, once #2 exists" framing below was written
   before #2 was scoped out as a separate, still-open item - #3 shipped independent of #2, keyed by
   beat type alone, with no dependency on multiple simultaneous threads ever existing.

## What today's design already accommodates

Not by original design for *this* specifically, but the shape happens to help:

- **`ClickSound` is already an open enum with independent per-sound `ClickSpec`s** - the *data
  model* for "each beat type has its own independently-configurable audio identity" already exists,
  shared by both `StreamingClickEngine` (the primary path, one continuously-running stream that
  mixes in whichever sound a beat resolves to) and `ClickPlayer` (the discrete-retrigger fallback,
  with its own per-sound `AudioTrack` pools). Either could extend to per-sound output routing
  without changing this data model.
- **The resolve-once-per-beat cache (`MetronomeEngine.ResolvedBeatAudio`) is keyed by an opaque
  beat counter (`totalBeats`), not a timestamp or a hardcoded "the one clock".** A second,
  independent beat counter (for a second simultaneous "thread") wouldn't conflict with the first
  one's cache by construction - the keying scheme doesn't assume there's only one sequence of beats
  in the universe.
- **The dedicated timing-dispatcher isolation (`engine/TimingDispatcher.kt`) already separates
  timing-critical work from `Dispatchers.Default`** - a second concurrent clock would want its own
  dispatcher instance for the same reason the first one does (isolation from unrelated app work),
  and the pattern for creating one is already a one-line call.

## What would need to change

- **`MetronomeEngine` and `MidiClockSender` are process-wide Kotlin `object` singletons; `ClickPlayer`
  and `StreamingClickEngine` are plain classes, but each instantiated exactly once as a field on the
  `MetronomeEngine` singleton.** Every one of them assumes exactly one instance/session for the
  entire app. True polyrhythm (#2) needs at least `MetronomeEngine`'s core (clock + bar queue + beat
  resolution) to become instantiable per "thread" rather than a singleton - a real refactor, not an
  incremental addition, since a lot of code (the UI, the widget, the Glyph service) currently reaches
  `MetronomeEngine.state`/`MetronomeEngine.frame` etc. directly as *the* singleton. `StreamingClickEngine`
  being a class rather than an object at least means a second instance is possible without fighting
  the singleton pattern the way a second `ClickPlayer` would have.
- **`StreamingClickEngine`/`ClickPlayer` each build exactly one `AudioAttributes`/output
  configuration for the whole class, shared by every `ClickSound`.** Per-beat-type channel routing
  (#1) means `AudioAttributes` (or an entirely different output API - `AudioTrack` routing to a
  specific device via `setPreferredDevice()`, or a multi-channel `AudioMixerAttributes` setup)
  becomes per-sound configuration, not a class-wide constant. For `StreamingClickEngine` specifically,
  since there's one continuously-running stream today, this likely means either multiple parallel
  streams (one per output/channel) or a genuinely multi-channel `AudioTrack` the writer mixes into
  per-channel rather than down to mono.
- ~~`MidiClockSender` has no per-beat-type concept at all today~~ **Done, but not by extending
  `MidiClockSender` itself** - `onBeat` now also calls `MidiActionSender.fireForBeat(beatType,
  timestampNanos)` as an independent, parallel dispatch, reusing `MetronomeEngine.beatTypeFor`'s
  classification rather than threading a beat-type parameter through `MidiClockSender`'s own
  24-ppqn tick loop (which still only ever sends `CLOCK_TICK`, unchanged). Keeping the two senders
  fully separate - down to each owning its own `MidiReceiverRegistry` instance rather than sharing
  one - was a deliberate choice to avoid coupling the new, less-proven code path to the existing,
  timing-critical one; see the ADR for the full reasoning.
- **The audio scheduling/resolve-once mechanism (`resolveBeatAudio`, `startAudioScheduling`,
  `StreamingClickEngine.scheduleBeat`) is written assuming one clock, one cache, one stream.**
  Multiple simultaneous threads (#2) would need either one cache/stream per thread or a compound key
  (thread ID + beat counter) - a small change in shape, but worth designing intentionally rather
  than bolting on after the fact.

## Superseded: isolating the actual `AudioTrack` trigger call

An earlier round of this doc deferred "isolate the actual trigger call onto its own execution
context" as a reasonable next step *if* on-device testing still showed residual jitter after a
bounded-slice-delay fix to the lookahead loop. On-device testing at 300+ BPM did still show
meaningful error, and the response wasn't to isolate the trigger call further - it was to stop
triggering discrete playback at all. `engine/StreamingClickEngine.kt` replaces the entire
discrete-retrigger/coroutine-wakeup model with a continuously-running `MODE_STREAM` `AudioTrack`:
clicks are mixed into the stream at a sample-frame offset computed from `AudioTrack`'s own
hardware-sampled frame<->nanoTime mapping, not from a coroutine waking up at approximately the
right wall-clock moment. This doesn't just isolate the old trigger call's latency - it removes the
mechanism that latency could ever affect. `ClickPlayer`'s discrete-retrigger path still exists as a
fallback (see `StreamingClickEngine.hasFailedWarmup`/`MetronomeEngine.usingStreamingClickEngine`)
for hardware/OEM configurations where `MODE_STREAM` construction or `AudioTrack.getTimestamp`
warm-up doesn't cooperate - the isolation problem this section used to describe is exactly the
scenario that fallback path is still in.

This also changes what "independent audio channel per beat type" (item 1 above) would extend: since
there's now one continuously-running stream doing the mixing, per-channel routing means routing
*within* the streaming writer (multiple output tracks, or a multi-channel `AudioMixerAttributes`
setup, each still fed by the same sample-clocked placement logic) rather than per-`ClickSound`
`AudioTrack` pools being individually routed the way `ClickPlayer`'s design would have implied.

## Open item: native AAudio/Oboe migration, for sub-millisecond placement

**Status: estimated, not started - a real scope increase, not a bug fix.** This project has no
NDK/C++ toolchain today; taking this on adds one. Written after `docs/timing-accuracy-benchmark.md`'s
own investigation closed the ≤10ms first-beat target (~2ms excess, measured) via a Java-side buffer-
sizing fix, but left Tier 1 (sub-ms steady-state placement precision) unreached - the research behind
that investigation (Web Audio's lookahead pattern, Android's own AAudio/Oboe guidance, commercial
metronome apps' reliance on native engines - full citations in that doc) is consistent that closing
the rest requires a genuine native audio callback, which no Java-only API can request.

**Why**: `Process.THREAD_PRIORITY_URGENT_AUDIO` (already used throughout via `newTimingDispatcher`)
is a nice-value boost under the ordinary CFS scheduler - real, but not the SCHED_FIFO real-time
class AudioFlinger's own fast mixer thread gets. Only an AAudio (API 26+) or Oboe callback, run with
`PERFORMANCE_MODE_LOW_LATENCY` + `SHARING_MODE_EXCLUSIVE`/MMAP, is scheduled on that class.

**Impact, and its limit**: reaching sub-ms placement precision - matching what apps like PolyNome
claim - needs this. It does **not** obviously fix "still noticeable" complaints on its own: the
current benchmark measures the engine's internal target-vs-actual-mixed-frame precision, not true
acoustic latency (speaker/DAC/room - still deferred, see `docs/timing-accuracy-benchmark.md`), and
not whether a perceived delay is actually the count-in's own deliberate pause (tunable today via
Settings, not a precision defect). **Rail 0, before spending any of the estimate below**: cheaply
disambiguate which of those three a fresh complaint actually is (toggle the count-in cap toward
zero; if the feeling is unchanged, it isn't the count-in) before assuming this migration is the
lever that fixes it.

**Rough estimate** (solo, focused, assuming no major surprises - JNI debugging usually has a long
tail): 0.5-1 day NDK/CMake/Oboe scaffolding (Oboe is a Maven Central dependency now, `com.google.
oboe:oboe`, not something to vendor from source) + 3-5 days porting `StreamingClickEngine`'s full
contract to a native callback + JNI bridge + 1-2 days test-strategy rework + 1-2 days full
regression pass + open-ended real-device tuning. **~1.5-2.5 weeks total**, not a session-scale task.

**Rails, for whoever picks this up**:

1. **`ClickPlayer`'s discrete-retrigger fallback must survive this unchanged.** "No click at all" is
   still worse than "the old, already-shipped mechanism" - the exact reasoning
   `StreamingClickEngine`'s own kdoc already states for the *existing* Java implementation. A native
   engine that fails to warm up/construct needs the identical graceful fallback, not a crash.
2. **Keep the JNI surface minimal - lifecycle + a lock-free schedule handoff, nothing else.**
   Business logic (`resolveBeatAudio`, mute probability, bar-queue interaction) stays in Kotlin,
   exactly as `StreamingClickEngine`'s own kdoc already separates "when a beat is due" (Kotlin) from
   "placing an already-decided click" (the engine). Don't let the migration blur that line by moving
   decision logic across the JNI boundary along with the placement mechanism.
3. **Don't lose Robolectric-testable coverage for anything that doesn't have to cross the JNI
   boundary.** `LeadMarginCalibratorTest`, `beatZeroCountInNanos`'s tests, etc. stay pure-Kotlin,
   pure-logic, JVM-testable - only the actual placement mechanism moves to native, mirroring
   `StreamingClickEngine`'s own current split.
4. **Multi-ABI build/test cost is real - budget for it up front, don't discover it late.** At minimum
   arm64-v8a (real devices) and x86_64 (emulator/CI) need to build and pass the on-device benchmark;
   don't assume single-ABI local testing generalizes.
5. **Re-run `FirstBeatTimingBenchmarkTest` before and after, on the same device(s), and treat the
   comparison as the actual acceptance test** - not a subjective "feels better." Extend it to cover
   the new Tier 1 (steady-state placement) target directly if the current ~13ms measurement doesn't
   already make the improvement obvious.
6. **This raises the contribution bar - name that cost instead of discovering it by omission**,
   matching this project's own governance precedent (`governance/qm/perspectives/`'s onramp
   retrospective, §2.5) for naming adoption costs explicitly rather than letting them surface as
   friction for the next contributor. C++/NDK becomes a real prerequisite for touching the audio
   engine; update `CONTRIBUTING.md`'s test-coverage section accordingly when this lands.
