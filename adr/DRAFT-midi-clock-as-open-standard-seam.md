# ADR-XXXX — MIDI Clock as the Bidirectional Sync Seam

| | |
|---|---|
| **Status** | Proposed |
| **Date** | 2026-06-28 (revised same day to add USB clock-out) |
| **Pends on** | Nothing architectural - filed Proposed rather than Accepted only because ratification is a human action per the org's own process, not because an input is outstanding |

## Context

qmetronome's "eventual goal" from the start was external clock integration -
following a DAW, a hardware sequencer, or another musician's gear - and that
grew into the symmetric case too: letting *other* things follow qmetronome's
tempo as their clock source. Unlike the Glyph Matrix hardware, this domain
has a forty-year-old, multi-implementation, fully open standard in both
directions: MIDI Clock (System Real-Time `0xF8`, 24 pulses per quarter
note), part of the MIDI 1.0 specification. Three independent transports
carry it on Android - USB, Bluetooth LE, and inter-app virtual MIDI - all
reachable through `android.media.midi`, a platform (AOSP, Apache-2.0)
package present since API 23, in both directions: `MidiReceiver` for
receiving, and an open output port (`MidiDeviceService.getOutputPortReceivers()`,
or any `MidiReceiver`-typed destination such as a `MidiInputPort` opened on
another device) for sending.

## Decision

MIDI clock sync, in either direction, is built entirely on
`android.media.midi`: no third-party MIDI library, no vendor SDK, no new
Gradle dependency at all. Two independent, symmetric pieces share that one
platform API:

1. **Following an external clock** (`engine/ClockSource` is the seam) -
   `InternalClockSource` and `midi/MidiClockSource` are interchangeable
   implementations behind it, and a future transport (e.g. a different
   protocol entirely) would be a third implementation, not a rewrite of the
   engine, visualizers, or UI.
2. **Sending our clock out** (`midi/MidiClockSender`) - reads
   `MetronomeEngine.state` directly (it doesn't need a seam of its own; it's
   a single consumer of the same `BeatPhase` everything else already reads)
   and writes raw clock bytes to a registered set of destinations:
   `VirtualMidiClockService`'s declared output port (always registered, no
   hardware needed), and zero or more USB devices' `MidiInputPort`s,
   registered/unregistered as `UsbMidiConnector.connectForSending()` /
   `disconnectSending()` are called. Both destination types are just
   `MidiReceiver`s to `MidiClockSender` - it doesn't know or care which is
   which.

Because it always derives from `MetronomeEngine.state` rather than from
wherever that tempo originated, a qmetronome instance that's currently
*following* an external clock and also has clock-out enabled becomes a
repeater for it automatically, with no special-casing - the same `BeatPhase`
both paths already share does the work.

## Consequences

- Full compliance with the org's seams doctrine (P3) and license rule (P1)
  without any exception or remediation needed, in both directions: the
  protocol has multiple independent implementations (any MIDI-capable device
  or DAW), the API is AOSP platform code, and the dependency surface added is
  zero.
- The replaceability test passes directly: if Android's MIDI API changed
  incompatibly, or a project needed to support a non-MIDI external clock
  (e.g. raw audio click tracking), only `midi/MidiClockSource` (receiving) or
  `midi/MidiClockSender` (sending) and their transport-specific connectors
  would need to change - `MetronomeEngine` and every `GlyphVisualizer` are
  written against `BeatPhase`, which carries no knowledge of where the beat
  came from or where it's going.
- The virtual device (`VirtualMidiClockService`) now declares both an
  input and an output port under one logical device ("qMetronome Clock",
  renamed from "qMetronome Clock In" now that the name undersold what it
  does) - this is a single MIDI device with a port in each direction, the
  same shape as the AOSP CTS test fixture for `MidiDeviceService`, not two
  separate devices.
- `UsbMidiConnector` deliberately allows following and sending to the same
  physical device simultaneously rather than blocking it. On real hardware
  with separate IN/OUT jacks this is normal and not a loop; it would only
  become one if that specific device has MIDI Thru/echo enabled, which is a
  device setting this app has no way to detect. The UI surfaces a heads-up
  when both are pointed at the same device rather than guessing at whether
  to block it - the alternative (a hard block) would also wrongly prevent
  the common, non-looping case on devices without Thru enabled.
- This ADR is filed for the record despite requiring no exception, because
  P6 ("decisions are documented or they didn't happen") applies equally to
  decisions that turned out clean - an undocumented good decision is just as
  invisible to a future reader as an undocumented compromise.

## Alternatives considered

1. **A third-party MIDI library** (e.g. for higher-level message parsing) -
   rejected: the platform API is sufficient for System Real-Time byte
   parsing in both directions, and adding a dependency for what
   `android.media.midi` already provides would be an unforced seam violation
   in the other direction (a library between us and a platform API that
   already does the job).
2. **A vendor-specific sync protocol** (if one existed) - never seriously
   considered, for the same reason the SDK-dependency ADR explains why one
   would be a problem: single-vendor protocols are exactly what the seams
   doctrine exists to avoid, and here an open alternative already existed.
3. **Routing clock-out through `ClockSource`** (treating sending as just
   another `ClockSource` implementation) - rejected: `ClockSource` models
   "where do *our* ticks come from," and sending is a consumer of ticks, not
   a producer of them. Forcing it into the same interface would mean either
   a fake `setBpm`/no-op `start` (the same awkward shape `MidiClockSource`
   already has to use *because* it measures rather than is told tempo) or
   splitting the interface - `MidiClockSender` reading `BeatPhase` directly
   is simpler and just as decoupled.

## Revision triggers

- Android deprecates or materially changes `android.media.midi` - re-evaluate
  the platform-API choice (unlikely; this is a long-stable AOSP package).
- A non-MIDI external clock source becomes a real request (e.g. raw audio
  tap-tempo from a microphone) - implement as a new `ClockSource`, which this
  ADR's own Decision predicts should require no engine/UI changes; that
  prediction is falsifiable against whatever actually happens.
- Real-hardware testing of USB clock-out (especially the follow-and-send-to-
  the-same-device combination) finds that some device's Thru/echo setting
  *does* cause a loop in practice - re-evaluate whether the heads-up text is
  sufficient or whether that combination needs an actual guard.

## Amendments

*None.*
