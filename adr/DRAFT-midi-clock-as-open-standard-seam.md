# ADR-XXXX — MIDI Clock as the External-Sync Seam

| | |
|---|---|
| **Status** | Proposed |
| **Date** | 2026-06-27 |
| **Pends on** | Nothing architectural - filed Proposed rather than Accepted only because ratification is a human action per the org's own process, not because an input is outstanding |

## Context

qmetronome's "eventual goal" from the start was external clock integration,
so the metronome can follow a DAW, a hardware sequencer, or another musician's
gear instead of (or in addition to) its own internal timer. Unlike the Glyph
Matrix hardware, this domain has a forty-year-old, multi-implementation,
fully open standard: MIDI Clock (System Real-Time `0xF8`, 24 pulses per
quarter note), part of the MIDI 1.0 specification. Three independent
transports carry it on Android - USB, Bluetooth LE, and inter-app virtual
MIDI - all reachable through `android.media.midi`, a platform (AOSP,
Apache-2.0) package present since API 23.

## Decision

External clock sync is built entirely on `android.media.midi`: no
third-party MIDI library, no vendor SDK, no new Gradle dependency at all.
`engine/ClockSource` is the seam - `InternalClockSource` and
`midi/MidiClockSource` are interchangeable implementations behind it, and a
future transport (e.g. a different protocol entirely) would be a third
implementation, not a rewrite of the engine, visualizers, or UI.

## Consequences

- Full compliance with the org's seams doctrine (P3) and license rule (P1)
  without any exception or remediation needed: the protocol has multiple
  independent implementations (any MIDI-capable device or DAW), the API is
  AOSP platform code, and the dependency surface added is zero.
- The replaceability test passes directly: if Android's MIDI API changed
  incompatibly, or a project needed to support a non-MIDI external clock
  (e.g. raw audio click tracking), only `midi/MidiClockSource` and its
  transport-specific connectors (`VirtualMidiClockService`,
  `UsbMidiConnector`) would need to change - `MetronomeEngine` and every
  `GlyphVisualizer` are written against `BeatPhase`, which carries no
  knowledge of where the beat came from.
- This ADR is filed for the record despite requiring no exception, because
  P6 ("decisions are documented or they didn't happen") applies equally to
  decisions that turned out clean - an undocumented good decision is just as
  invisible to a future reader as an undocumented compromise.

## Alternatives considered

1. **A third-party MIDI library** (e.g. for higher-level message parsing) -
   rejected: the platform API is sufficient for System Real-Time byte
   parsing, and adding a dependency for what `android.media.midi` already
   provides would be an unforced seam violation in the other direction (a
   library between us and a platform API that already does the job).
2. **A vendor-specific sync protocol** (if one existed) - never seriously
   considered, for the same reason the SDK-dependency ADR explains why one
   would be a problem: single-vendor protocols are exactly what the seams
   doctrine exists to avoid, and here an open alternative already existed.

## Revision triggers

- Android deprecates or materially changes `android.media.midi` - re-evaluate
  the platform-API choice (unlikely; this is a long-stable AOSP package).
- A non-MIDI external clock source becomes a real request (e.g. raw audio
  tap-tempo from a microphone) - implement as a new `ClockSource`, which this
  ADR's own Decision predicts should require no engine/UI changes; that
  prediction is falsifiable against whatever actually happens.

## Amendments

*None.*
