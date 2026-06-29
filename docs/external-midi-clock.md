# External MIDI clock: feasibility & lift

**Status: both directions, all three transport paths that don't need BLE, are implemented.**
Following an external clock - virtual (inter-app) and USB MIDI - via `midi/MidiClockSource`,
`midi/VirtualMidiClockService`, `midi/UsbMidiConnector`. Sending our own clock out, also via
virtual and USB, via `midi/MidiClockSender` and the same `UsbMidiConnector` (see "Sending clock
out" below). This doc is kept as the design rationale for *why* it's built the way it is, plus
what's intentionally still out of scope (Bluetooth LE MIDI, Song Position Pointer / proper
Continue support — see the bottom of this file). For how to verify either USB path against real
hardware, see [`docs/usb-midi-test-plan.md`](usb-midi-test-plan.md).

This was originally written as a feasibility investigation before any of it existed — the
analysis held up: feasible, no new dependency, and `engine/ClockSource.kt` was already shaped
for it as a sibling to `InternalClockSource`, requiring no changes to visualizers, the Glyph
service, or the UI's rendering path.

## The protocol

MIDI Clock is a System Real-Time message: a single byte, `0xF8`, sent 24 times per quarter
note (24 PPQN). A receiver derives tempo from the *interval between clock bytes*, not from any
explicit BPM value — there is no "set tempo" message. Related real-time bytes:

| Byte | Meaning |
|------|---------|
| `0xF8` | Clock tick (24 per quarter note) |
| `0xFA` | Start (from the beginning) |
| `0xFB` | Continue (resume from current position) |
| `0xFC` | Stop |
| `0xF2` | Song Position Pointer (in MIDI beats, i.e. 1/16 notes) |

A naive implementation that fires a beat on every 24th tick will look jittery, because real
hardware/DAWs don't space ticks perfectly evenly. This needs the same drift-tolerant thinking
already in `InternalClockSource`, just inverted: instead of generating evenly-spaced ticks,
average a rolling window of received tick intervals (e.g. last 24–48) to smooth out jitter
before deriving BPM and the beat callback.

## Android's MIDI API

`android.media.midi` is a platform package (API 23+) — **no new Gradle dependency**, and our
`minSdk` is already 33. It supports three transports, and only one of them needs hardware:

1. **USB MIDI** — a keyboard, groovebox, or computer/DAW connected via USB-OTG. Needs the user
   to grant a one-time system USB permission dialog per device (handled automatically by
   `MidiManager.openDevice(...)` - confirmed against Google's own `android-MidiScope` sample,
   which does no manual `UsbManager` permission handling at all); discovered via
   `MidiManager.getDevicesForTransport(TRANSPORT_MIDI_BYTE_STREAM)`.
2. **Bluetooth LE MIDI** — BLE peripherals that advertise the MIDI BLE service. Needs
   `BLUETOOTH_CONNECT`/`BLUETOOTH_SCAN` runtime permissions and a scan/pair flow before
   `MidiManager.openBluetoothDevice(...)`. The least consistent path in practice — BLE-MIDI
   peripheral support varies a lot across hardware.
3. **Virtual (inter-app) MIDI** — always available, **no hardware at all**. Our app declares
   itself as a `MidiDeviceService` with a MIDI input port; any other app on the same phone
   (a DAW, a looper, a sequencer) can then pick "qMetronome" as a MIDI output destination and
   send it clock directly. This is the cheapest to build *and* the only one I can fully test
   without external gear in hand.

In all three cases, received bytes land in the same place: a `MidiReceiver.onSend(byte[] msg,
int offset, int count, long timestampNanos)` override. The parsing logic (filter for `0xF8`,
roll up 24 into a beat, smooth the interval) is identical regardless of which transport
delivered the bytes — the transport only changes how the connection gets opened.

## Where it plugs into the existing architecture (as built)

The interface generalization anticipated below did turn out to be necessary, and is in place:

```kotlin
interface ClockSource {
    fun start(scope: CoroutineScope, bpm: Float, onBeat: (timestampNanos: Long, measuredBpm: Float?) -> Unit)
    fun setBpm(bpm: Float) // no-op for MidiClockSource - it measures tempo, isn't told it
    fun stop()
}
```

`MetronomeEngine.clock` is now a `@Volatile private var`, swapped via `useMidiClock()` /
`useInternalClock()`. It auto-switches to MIDI the moment `MidiClockSource.onExternalActivity`
fires (any real-time byte, not just a full beat), and the render loop's existing per-frame check
doubles as the silence watchdog — falls back to internal timing after ~4 beat-intervals with no
tick. `0xFA`/`0xFB` map to `MetronomeEngine.start()`, `0xFC` to `stop()`. `0xFB` Continue and
`0xF2` Song Position Pointer are *not* distinguished from Start yet — Continue just restarts
from beat 0 like Start does, which is fine for following along but wrong if you stop, rewind on
the hardware, and expect qMetronome to resume from the same bar.

As predicted, visualizers, the Glyph service, and the matrix preview needed **zero changes** —
they only ever see `BeatPhase`, which doesn't know or care where the beat came from.

## Sending clock out (the symmetric case)

`midi/MidiClockSender` is the mirror image: instead of measuring tempo from received ticks, it
generates them from `MetronomeEngine.state` and writes raw bytes to a registered set of
destinations. It doesn't need a seam of its own the way receiving needed `ClockSource` - it's
just one more reader of the same `BeatPhase` everything else already reads, so there's nothing
for visualizers/UI/the Glyph service to know about here either.

Because it always derives from `MetronomeEngine.state` rather than caring where that tempo
actually came from, enabling clock-out while the engine happens to be *following* an external
clock turns qmetronome into a repeater automatically - no special-casing needed, same as
predicted when `ClockSource` was originally designed as a swappable seam.

`VirtualMidiClockService` now declares both an input port (for `MidiClockSource`) and an output
port (for `MidiClockSender`) on one logical device, renamed from "qMetronome Clock In" to
"qMetronome Clock" now that the old name undersold what it does. The output port's receiver is
obtained via the platform's `MidiDeviceService.getOutputPortReceivers()` (confirmed against the
actual `android.jar` bytecode, not just docs - it's a real, `final`, non-overridable method)
and registered with `MidiClockSender.addDestination()` in `onCreate()`, unregistered in
`onClose()`.

USB clock-out (sending to a physical device's input port, not just other apps) is also wired up:
`UsbMidiConnector.connectForSending(deviceInfo)` opens the device's input port
(`device.openInputPort(0)`, which returns a `MidiInputPort` - confirmed via `javap` that it
`extends MidiReceiver`, so it registers through the exact same `MidiClockSender.addDestination()`
call as the virtual port) and `disconnectSending()` tears it down. It's deliberately a separate
connection from `connectForFollowing()`/`disconnectFollowing()` - you can follow one device while
sending to a different one, follow and send to the *same* device, or just do one direction.

That last combination - following and sending to the same physical device - is allowed rather
than blocked. On real gear with separate MIDI IN/OUT jacks this is normal and isn't a loop by
itself; it would only become one if that specific device has MIDI Thru/echo enabled, silently
re-transmitting whatever it receives out its own output - a per-device setting this app has no
way to query or detect. Blocking the combination outright would also incorrectly prevent the
common, non-looping case on devices without Thru enabled, so the settings UI surfaces a heads-up
text when both point at the same device instead of guessing. This specific combination is the one
part of clock-out I can't verify without real hardware that has a Thru setting to test against -
see [`docs/usb-midi-test-plan.md`](usb-midi-test-plan.md).

## Routing & extensibility: adding a new transport

`MidiClockSource` doesn't care which transport bytes arrive from - it exposes
`receiverFor(source: MidiClockSource.Source): MidiReceiver`, and any transport just needs to
feed raw MIDI bytes into the receiver for its tag:

```kotlin
enum class Source { VIRTUAL, USB }
```

To add a new transport (e.g. Bluetooth LE MIDI later):

1. Add a new entry to `MidiClockSource.Source` (e.g. `BLE`).
2. Write whatever connection/discovery code that transport needs (for BLE: scanning, pairing,
   `MidiManager.openBluetoothDevice(...)`).
3. Once connected, get its receiver with `MidiClockSource.receiverFor(Source.BLE)` and hand it
   to the platform exactly like `UsbMidiConnector` does with `outputPort.connect(receiver)`.

That's the entire integration surface - no changes to the engine, parsing, or smoothing logic.

**Only one source actively drives the clock at a time.** The first byte from any source claims
`activeSource`; a different source can only take over after the current one has gone quiet for
500ms (`SOURCE_TAKEOVER_SILENCE_MS`). This matters because more than one transport can easily be
live simultaneously in practice (a virtual MIDI cable *and* a USB device both sending clock, for
instance) - without arbitration, both would feed the same tick counter and the beat would fire
after however many *combined* ticks happen to add up to the threshold, which looks like "fires
way faster than the displayed BPM." That symptom is exactly what motivated adding this
arbitration; `Log.d("MidiClockSource", ...)` lines on every source switch and every beat (with
tick count and source) are there specifically so this is diagnosable from logcat next time,
rather than guessed at.

Deliberately not supported: merging or voting across two simultaneously-live clocks. Following
two different external clocks at once is incoherent for a metronome - there's no single "right"
tempo to derive - so the simple last-active-wins rule is the correct behavior, not a shortcut.

## Effort estimate (as originally planned)

| Piece | Adds |
|---|---|
| `MidiClockSource` (parsing + smoothing) | ~150–250 lines, the core of the work |
| `ClockSource` interface tweak + `MetronomeEngine` swap support | small, contained |
| Virtual MIDI device (`MidiDeviceService` + ports XML + manifest) | ~60 lines + manifest entries |
| Clock-source picker UI (Internal / virtual / USB list) | ~100 lines |
| USB device discovery/permission UI | + another ~80–100 lines on top of the above |
| BLE-MIDI scan/pair flow | the most UI/permission code, and the flakiest to get right |

Virtual MIDI and USB MIDI share essentially all of the clock-parsing code; BLE is the same
parsing core but with the most additional plumbing around it.

## Status and what's left

Virtual (inter-app) and USB MIDI are both built in both directions - following and sending out -
sharing the same `MidiClockSource` parsing core (receiving) or `MidiClockSender` destination set
(sending) regardless of transport. Remaining, in priority order:

1. **Real-hardware verification, round 2** — first real-gear test found the beat firing far
   faster than the displayed BPM while the BPM itself was correct. Root cause: testing used a
   virtual MIDI cable *and* USB simultaneously, both feeding the same tick counter with no
   isolation between sources - fixed by the source-arbitration logic above. Worth re-running
   [`docs/usb-midi-test-plan.md`](usb-midi-test-plan.md) to confirm the fix holds, ideally with
   only one source connected at a time first, then deliberately with both live to confirm
   arbitration behaves as expected rather than glitching.
2. **USB clock-out, real-hardware verification** — `connectForSending()`/the follow-and-send-to-
   the-same-device combination have never run against real gear. Particularly worth checking
   whether any available hardware has a MIDI Thru/echo setting, to see what the "could loop"
   heads-up text is warning about actually looks like in practice, and whether that's enough or
   needs an actual guard.
3. **Song Position Pointer / proper Continue** — only worth doing if you actually want
   stop-and-resume-from-position behavior; skip if "just follow along" is good enough.
4. **Bluetooth LE MIDI** — real demand from hardware-synth users, but the most fragile
   transport to support well across different peripherals; do this once the other two are
   proven solid.
