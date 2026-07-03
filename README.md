# qMetronome

A tempo/beat visualizer and functional metronome for Nothing phones with a Glyph Matrix
(Phone (3), Phone (4a) Pro), built on the [Glyph Matrix Developer Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit).

This file is architecture/setup/testing. New contributors should start with
[`CONTRIBUTING.md`](CONTRIBUTING.md) — it covers building with and without
Android Studio, the project layout, and PR norms. Feasibility investigations,
manual test plans, and release readiness live in [`docs/`](docs/README.md);
decision records live in [`adr/`](adr/README.md).

## Architecture

- `engine/MetronomeEngine` — process-wide singleton holding tempo, beat position and the
  current Glyph frame as `StateFlow`s. It's the single source of truth so the in-app preview
  and the real Glyph Matrix always show the same thing, whether or not the app UI is open.
- `engine/ClockSource` — produces beat ticks. `InternalClockSource` drift-corrects against
  `System.nanoTime()` so tempo doesn't slip over a long session. `midi/MidiClockSource` is the
  external-clock implementation: the engine auto-switches to it the moment MIDI Clock activity
  arrives, and falls back to internal timing if that feed goes quiet for a few beats.
- `midi/` — MIDI Clock (24 ppqn) support, in both directions, over both virtual (inter-app) and
  USB transports. `MidiClockSource` parses real-time bytes and measures tempo from a smoothed
  rolling average of tick intervals, regardless of transport; `VirtualMidiClockService` exposes
  the app as a MIDI destination other apps can target with no hardware (see
  `res/xml/midi_device_info.xml`). Going the other way, `MidiClockSender` generates clock from
  `MetronomeEngine.state` and writes it to a registered set of destinations. `UsbMidiConnector`
  is the USB side of both directions - `connectForFollowing()`/`connectForSending()` are
  independent, so a device can be followed, sent to, both, or neither. It's a process-wide
  singleton like `MetronomeEngine`/`MidiClockSender`, which is what lets `StarredMidiDevices`
  work: starring a device persists which connection(s) were active for it, and a
  `MidiManager.DeviceCallback` registered at app startup restores them automatically the moment
  that device reappears on the USB bus, whether or not Settings is open. See
  [`docs/external-midi-clock.md`](docs/external-midi-clock.md) for the design rationale and
  [`docs/usb-midi-test-plan.md`](docs/usb-midi-test-plan.md) for how to verify either USB
  direction (including starring/auto-reconnect) against real hardware.
- `engine/ClickPlayer` — the audible click (`ToneGenerator`), so this also works as a real
  metronome for practicing/performing musicians, with or without the Glyph Matrix.
- `visualizers/` — the animation algorithms. See below.
- `glyph/GlyphMatrixToyService` — reusable Glyph Toy boilerplate (bind lifecycle, device
  registration, Glyph Button message handling). `glyph/MetronomeGlyphService` is the concrete
  toy: it starts/stops the engine with toy selection, taps tempo on Glyph Button touch-down,
  and cycles visualizers on long-press.
- `ui/` — Compose UI. `MainScreen` keeps the Glyph Matrix preview as the dominant, focal
  element with only tempo/tap/play-stop alongside it; everything else (beats-per-bar, click
  toggle, visualizer picker, visual timing offset, MIDI clock status/USB connection/clock-out)
  lives behind the bottom-right settings button in `SettingsSheet`, a full-screen translucent
  overlay (not a half-open bottom sheet) so the matrix preview's flashes still glow through
  dimly behind it. The preview shows a dim ghost of the current visualizer at rest even when the
  metronome is stopped (6% brightness idle frame), so the AMOLED screen never looks fully off.
  The settings button isn't the only way in: long-pressing the preview also opens settings;
  double-tapping the preview toggles play/stop; swiping left/right cycles visualizers; and the
  BPM number itself is triple-duty — tap it for tap-tempo, long-press it for a direct-entry
  dialog (range 1–400 BPM), or drag it left/right for continuous fine adjustment (a one-time
  hint the first time it's shown explains all three, then never appears again). The transport
  row is TAP / play-stop / HOLD left-to-right, with play-stop enlarged as the primary action;
  `HoldButton` queues BPM and beats-per-bar changes while pressed (a classic momentary
  "shift key"), and can also be *latched* — long-press or double-tap it to make staging sticky,
  indicated by turning "recording red," until a later tap on it flushes everything and unlatches.
  Latching a beats-per-bar change doesn't take effect mid-bar; it waits for the next bar's
  downbeat, the same way a live musician would. Settings → Layout → "Compact landscape layout"
  switches from the default full-size-overflow aesthetic to a side-by-side preview+controls
  arrangement that fits in landscape without clipping. `MatrixPreview` renders the exact same
  frames as the real hardware, so visualizers can be developed and demoed without a physical
  Nothing device. The theme (`ui/theme/`) is strictly monochrome — black/white only, matching the
  Glyph Matrix and Nothing's own design language — with two deliberate exceptions: a navy accent
  (`QmNavy`) for brand chrome (the two marks in `BrandMarks.kt` - `QmBrandMark` bottom-left,
  `AppBrandMark` bottom-center - long-press either to open its GitHub page), and "recording red"
  (`RecordingRed`) reserved for transient
  state/activity indicators — a latched hold, a staged-but-not-yet-applied change, active MIDI
  clock — in the spirit of a studio tally light, not a wash of color.
- `widget/MetronomeWidget` — a home screen widget (Jetpack Glance), BPM + play/stop only.
  Updates are event-driven, not polled: `QMetronomeApp` collects `MetronomeEngine.state`,
  filters it down to just `(bpm, isPlaying)` with `distinctUntilChanged()`, and calls
  `updateAll()` only when one of those actually changes - never on the render loop's ~40Hz
  phase ticks. See [`docs/home-screen-widget.md`](docs/home-screen-widget.md) for why a
  smoothly-animating widget was deliberately ruled out rather than attempted.

## Adding a new visualizer

Implement `GlyphVisualizer`:

```kotlin
class MyVisualizer : GlyphVisualizer {
    override val id = "my_visualizer"
    override val displayName = "My Visualizer"

    override fun render(matrixSize: Int, beat: BeatPhase): IntArray {
        val canvas = GlyphCanvas(matrixSize)
        // beat.phase is 0..1 progress through the current beat, beat.isAccent marks beat 1
        canvas.filledCircle(canvas.center, canvas.center, matrixSize * 0.3f, 255)
        return canvas.toIntArray()
    }
}
```

Add an instance to `VisualizerRegistry.all` and it's done — it shows up in the in-app picker
and becomes selectable via Glyph Button long-press. No service, threading, or SDK code needed;
`render()` is called continuously by the engine and is a pure function of `BeatPhase`.

Two requirements every visualizer must meet, enforced by `VisualizerRenderTest` (see
`GlyphVisualizer`'s docs for the full rationale):

1. **The beat must read without audio** — more total light at `phase == 0` than mid-decay
   (e.g. `phase == 0.5`).
2. **Bar 1 must read distinctly from the other beats** — more total light when `isAccent` is
   true than at the same phase with `isAccent` false.

Brightness alone usually can't carry requirement 2, since it's already pushed near maximum at
`phase == 0` to satisfy requirement 1 — scale the *size* of whatever's flashing instead (see any
built-in visualizer's `accentScale` pattern). `GlyphCanvas.line()` is available alongside
`filledCircle()`/`ring()` for arm/pendulum-style visualizers.

## Setting tempo

Several input methods on the main screen, layered for different precision needs:

- **Tap tempo**: tap the **TAP** button, or tap the BPM number itself, in rhythm; BPM is derived
  from a rolling average of the last few taps.
- **Step buttons** (either side of the BPM number): tap for ±1 BPM, hold for a
  geometrically-accelerating repeat - the longer you hold, the faster it moves.
- **Drag-to-scrub**: press and drag the BPM number itself left/right for continuous fine
  adjustment.
- **Long-press the BPM number**: type an exact value directly.

All of these write to the same tempo, so mixing them (tap to get close, then drag to fine-tune)
just works. **HOLD** (momentary or latched - see the Architecture section above) sits between
you and the engine for BPM and beats-per-bar: while held or latched, changes are staged and
shown in "recording red" instead of applying immediately, useful for setting up the next section
of a live performance without disturbing the current one. **Settings → Clock → "Send MIDI
clock"** turns qMetronome into a MIDI clock *source* for other apps or USB gear, the mirror
image of following an external clock - see
[`docs/external-midi-clock.md`](docs/external-midi-clock.md) for both directions.

## Using the widget

Long-press the home screen → **Widgets** → place qMetronome. It shows the
current BPM and a START/STOP control:

- **Tap START/STOP** to toggle the metronome — this is the same engine the
  app and the Glyph Toy use, so it stays in sync with all of them.
- **Tap anywhere else on the widget** to open the full app (for tempo,
  visualizer, or MIDI settings).
- The number updates on its own when BPM changes from the app, MIDI, or the
  widget itself — no need to remove and re-place it.

It's deliberately BPM + play/stop only, not a live mirror of the Glyph
Matrix animation — see [`docs/home-screen-widget.md`](docs/home-screen-widget.md)
for why.

## Setup notes

- `app/libs/glyph-matrix-sdk-2.0.aar` is the Glyph Matrix SDK from the Developer Kit.
- `minSdk` is 33, required by the SDK itself (the Glyph Matrix only exists on phones running
  recent Android anyway).
- The Glyph Toy preview icon (`drawable/toy_preview.xml`) is generated pixel art, not hand-drawn:
  it's produced directly from the same static-logo pose used by `ic_launcher_foreground.xml` and
  `BrandMarks.kt`'s `AppBrandMark` (see that file's doc), rasterized onto a matrix via
  `GlyphCanvas` so it reads as real Glyph Matrix pixel art rather than a smooth vector. It has
  **not** been checked against the Developer Kit's own spec images (`23112_spec.svg` /
  `25111_spec.svg`) for exact dimensions/format conventions - worth a look before shipping.

## Testing

`./gradlew test` runs the full unit test suite, including Robolectric-backed
tests for anything that touches Android framework classes (the engine's
self-healing render loop, `MetronomeSettings` persistence, MIDI clock source
arbitration) and plain-JUnit tests for the pure-Kotlin pieces (every
visualizer, `GlyphCanvas`, `VisualizerRegistry`). The visualizer tests double
as a contract check: every built-in visualizer is verified to produce a
correctly-sized, in-range frame, to flash brighter at the start of a beat
than mid-decay (the no-audio accessibility requirement - see
`GlyphVisualizer`'s docs), and to render fast enough not to lag the render
loop. `glyph/` and the real Glyph Matrix SDK aren't unit-testable here (a
closed third-party AAR with real Binder calls, not something Robolectric can
shadow) - see [`docs/usb-midi-test-plan.md`](docs/usb-midi-test-plan.md) for
how that side is verified on real hardware instead.

## Project governance

This is Quaternion Media's first mobile/cross-platform-device project, and
its decision-record discipline (`adr/`) is adopted from the org's
[`qm`](https://github.com/quaternionmedia/qm) constitution. Two real gaps
showed up between that constitution (built around self-hosted server
infrastructure) and a sideloaded app built against a closed hardware-vendor
SDK; rather than papering over them, they're named in
[`docs/governance-perspective.md`](docs/governance-perspective.md) and the
corresponding `adr/` drafts, and fed back to the org as an open question
rather than resolved unilaterally.

## CI

`.github/workflows/ci.yml` runs the Glyph SDK import-boundary check (enforcing
the isolation claimed in `adr/DRAFT-glyph-matrix-sdk-dependency.md`), then a
full `assembleDebug` + `testDebugUnitTest`, on every push to `main` and every
pull request.

## License

qmetronome's own source is MIT-licensed (see [`LICENSE`](LICENSE)). That
covers everything in this repository except `app/libs/glyph-matrix-sdk-2.0.aar`,
which is a closed-source binary distributed by Nothing Technology Limited
under its own terms (see [`adr/DRAFT-glyph-matrix-sdk-dependency.md`](adr/DRAFT-glyph-matrix-sdk-dependency.md)
for why that dependency exists and how it's isolated). Third-party
dependencies pulled in via Gradle (AndroidX, Kotlin, Robolectric, etc.) remain
under their own licenses, not relicensed by this project's MIT grant.

The app's privacy policy is [`PRIVACY.md`](PRIVACY.md) — short version: no data
collection, no analytics, no network calls, settings stay on-device. See
[`docs/app-store-checklist.md`](docs/app-store-checklist.md) for what's left
before this can actually be submitted to Google Play and what's confirmed
vs. genuinely unverified about Nothing's distribution channel.

## Known limitations / next steps

- Phone (4a) Pro (`DEVICE_25111p`) is AOD-only per the kit, which only refreshes once a minute —
  not useful for live tempo. The toy currently isn't registered with `aod_support`, so on that
  device it just won't show up in the toy list; full AOD support is future work.
- External MIDI clock sync is implemented for virtual (inter-app) and USB transports, in both
  directions (following, and sending our own clock out). Following and sending to the *same* USB
  device simultaneously is allowed rather than blocked, with a UI heads-up about devices that
  echo MIDI Thru - that combination hasn't been verified against real hardware yet. MIDI Song
  Position Pointer / proper Continue support is not implemented (Continue currently behaves like
  Start - resets to beat 0). Bluetooth LE MIDI is not implemented yet (see
  `docs/external-midi-clock.md`).
- The home screen widget is BPM + play/stop only, by design - no matrix-preview thumbnail (see
  [`docs/home-screen-widget.md`](docs/home-screen-widget.md) for why a live-pulsing widget was
  ruled out, and the manual test checklist, since a placed widget isn't unit-testable here).
- USB MIDI only scans `TRANSPORT_MIDI_BYTE_STREAM` devices; a USB-MIDI 2.0/UMP-only device
  wouldn't show up in the scan yet (see the troubleshooting section in
  `docs/usb-midi-test-plan.md`).
