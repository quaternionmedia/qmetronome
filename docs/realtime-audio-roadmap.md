# Real-time audio/MIDI roadmap: multi-channel, multi-thread, per-beat-type actions

**Status: not implemented, not an ADR - a scoping document.** Nothing here is a ratified decision;
it exists so the longer-term direction you named is written down for deliberate future batching
instead of being forgotten or accidentally boxed out by today's single-track/single-clock design.
Written alongside the v0.0.25/v0.0.26-era fix for a real audio-timing bug at high tempo, later
followed by a full sample-clocked streaming rewrite of the audio path itself (see
`docs/publication_checklist.md`'s Technical Polish section, `engine/StreamingClickEngine.kt`, and
`MetronomeEngine.startAudioScheduling()`'s kdoc) - that work is a prerequisite for any of this, not
a step toward it, but it's the reason this doc exists now rather than later.

## The direction

Three related capabilities, roughly in the order they'd naturally build on each other:

1. **Independent audio channel routing per beat type.** Today, `BAR`/`ACCENT`/`REGULAR` all play
   through the same output (whatever `AudioAttributes.USAGE_ASSISTANCE_SONIFICATION` resolves to
   on the device). The goal: let each beat type route to its own destination - e.g. a different
   output device, a different app-level "channel" a DAW or mixer could address independently, or
   distinct stereo panning/routing within the same output.
2. **Multiple simultaneous, independently-tempo'd beat "threads".** True polyrhythm/polymeter -
   more than one clock running concurrently (e.g. a 3-against-4 pattern, or an entirely separate
   tempo track layered on top of the main one), each with its own bar queue, its own audio
   routing, potentially its own visualizer.
3. **Per-beat-type MIDI action routing.** Today, `MidiClockSender` sends one shared clock pulse
   (`0xF8`) regardless of which beat type is playing - it has no concept of "beat type" at all,
   only "a beat happened." The goal: a `BAR`/`ACCENT`/`REGULAR` beat (or a beat from a specific
   "thread", once #2 exists) triggering its own distinct MIDI message (a note-on, a CC, a program
   change) - letting qMetronome drive external gear/software differently per beat type, not just
   act as a shared tempo reference.

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
- **`MidiClockSender` has no per-beat-type concept at all today** - `onBeat` only reaches it as
  "a beat happened, here's the measured/target bpm." Per-beat-type MIDI actions (#3) need the
  beat's resolved `ClickSound` (or whichever beat-type/thread identifier) threaded through to
  `MidiClockSender`, plus a mapping from beat type to MIDI message that doesn't exist yet (today
  it's hardcoded to always send `CLOCK_TICK`).
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
