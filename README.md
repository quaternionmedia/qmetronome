# qMetronome

A metronome and tempo visualizer for performing and practicing musicians, built for any
Android 13+ phone: tap tempo, drag-to-scrub, a bar queue for lining up a set's tempo/meter
changes, MIDI clock sync, and a home screen widget - no special hardware required. On a
Nothing Phone (3) or Phone (4a) Pro, it additionally lights up the physical Glyph Matrix as a
second, glanceable display, via the [Glyph Matrix Developer Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit).

<p align="center">
  <img src="docs/images/generated/screenshots/bpm-drag-scrub.png" width="220" alt="Main screen: Glyph Matrix preview, BPM, transport">
  <img src="docs/images/generated/videos/bpm-drag-scrub.gif" width="220" alt="Dragging the BPM number to scrub tempo, in motion">
  <img src="docs/images/generated/screenshots/settings-jump-to-unit.png" width="220" alt="Settings, Tempo & Bars expanded">
</p>

**New here?** [`docs/user-guide.md`](docs/user-guide.md) is the visual, gesture-by-gesture guide -
every drag, tap, and toggle this app has, each with a screenshot and a short video showing it in
motion. The exact same content is also built into the app itself - tap the **?** icon next to
Settings for a live, interactive version of the same walkthrough.

This file is architecture/setup/testing. New contributors should start with
[`CONTRIBUTING.md`](CONTRIBUTING.md) — it covers building with and without
Android Studio, the project layout, and PR norms. Feasibility investigations,
manual test plans, and release readiness live in [`docs/`](docs/README.md);
decision records live in [`governance/qm/adr/`](governance/qm/adr/README.md),
inside the `governance/qm` submodule (this project's own branch of the org's
constitution repo).

## Requirements

- **Any Android 13 (API 33)+ phone, from any manufacturer** — the whole core metronome (tap
  tempo, BPM/beats-per-bar, the bar queue, random mute, on-screen visualizer preview, audible
  click, home screen widget) works here, no special hardware needed. `minSdk` 33 is driven by the
  Glyph Matrix SDK itself rather than anything in this app's own code (see "Setup notes" below),
  but applies whether or not you have Glyph hardware.
- **A Nothing Phone (3) or Phone (4a) Pro adds the physical Glyph Matrix display** - an
  enhancement, not a requirement. On any other device the Glyph Toy button just shows a toast
  saying it's unsupported; nothing crashes.
- **MIDI clock sync** (virtual in-app-to-app, or USB) degrades gracefully on devices without MIDI
  support (`android.software.midi` is declared optional in the manifest). USB MIDI additionally
  needs a device with USB host/OTG support.

## Using qMetronome

The screenshots and short videos for every gesture below live in
[`docs/user-guide.md`](docs/user-guide.md) (also built into the app itself behind the **?** icon) -
this section is the narrative version of the same ground, for reading rather than watching.
Class/file names and the mechanics behind each gesture are collected separately in the
[Glossary](#glossary).

### Dialing in a tempo

Every tempo control writes to the same underlying value, so you can mix them freely - tap to get
roughly the right speed, then drag to fine-tune, without ever fighting the last method you used.

Before a set, or between songs, tap the **TAP** button or the BPM number itself in rhythm and
qMetronome derives a BPM from a rolling average of your last few taps. Tapping while stopped only
dials in a tempo - it doesn't start playback, so you can settle on the right number before
committing to a downbeat. The one exception is HOLD: latch it (below) and tapping out a tempo more
than once both commits the tapped value *and* starts playback immediately, at the current time
signature - a deliberate "count it in and go" gesture for starting a song cold.

Need more precision than your thumb can tap? Press and drag the BPM number left or right for
continuous fine adjustment, or long-press it to type an exact value. That same long-press dialog
is unit-aware: chips switch between BPM (1–400, or 0.1–12000 with Extended range on), BPH, and BPS
mid-entry, landing on a sensible value in the new unit rather than a literal, often-nonsensical
arithmetic conversion of what you'd typed - switching chips *is* the "convert between units"
gesture. Settings' own "Jump to unit" chip row is the same idea one level up, for jumping the
*live* tempo straight into BPH/BPS range without dragging all the way there. The step buttons
flanking the BPM number move it ±1 BPM per tap, or accelerate the longer you hold them, for coarse
adjustment without leaving the keyboard-free main screen.

HOLD itself is worth knowing well: press and hold it while adjusting BPM or beats-per-bar, and the
change stages instead of applying immediately - shown in "recording red" until you let go, at
which point everything you changed commits at once. That's useful mid-performance, for lining up
the next section without disturbing what's currently playing. Long-press or double-tap HOLD to
latch it instead, so staging stays active without holding a finger down, until a later tap on HOLD
flushes everything and unlatches. A latched beats-per-bar change specifically waits for the next
bar's downbeat before taking effect, rather than cutting the current bar short - the same courtesy
a live musician bringing in a meter change would extend the rest of the band.

### Planning a set with the bar queue

Below the tempo controls, the time signature shows as two independently steppable numbers stacked
vertically (beat count over note value - a real time signature, not a fraction), and just beneath
that is a second, smaller row for queuing up a sequence of differently metered *and* differently
paced bars. Say a song has three bars of 4/4 at 90 BPM before a one-bar 3/4 turnaround at 140 BPM -
build that once, ahead of time, and qMetronome cycles through it live rather than you riding the
tempo/meter controls in real time and hoping you land the change on the beat.

Every bar in the queue remembers its own beats-per-bar, note value, and tempo - tap a bar to jump
to it and its settings recall exactly as you left them; adjust the active bar the normal way
(tap/steppers/drag/long-press on the BPM number) and only that bar changes. The `+` button appends
a copy of whichever bar is active and jumps to it; `−` removes the active bar; both are no-ops if
it's the only one left. The trash icon at the far left - flagged with a small red dot as a
destructive, unrecoverable action - clears the whole queue back to a single default bar, for
starting over rather than trimming bars one at a time.

Each bar renders as a rectangle sized to carry information at a glance: width scales with beat
count relative to the rest of the queue (the longest bar reads as the widest rectangle), height
scales with tempo (faster bars read taller), and each bar is divided into one segment per beat so
the count reads directly off the shape. Only the active bar's current-beat segment pulses, and the
active bar itself reads brighter than the rest. Long-press any bar to remove it directly, in
addition to the `−` button.

A mode icon at the far right controls how the queue advances at each bar boundary during playback:
**Loop** (default) wraps back to the first bar after the last; **Once** stops advancing once it
reaches the last bar, holding there rather than stopping playback outright; **Manual** never
auto-advances, so only tapping a bar's dot moves it - useful for a set where you want to trigger
the next section on cue rather than on a fixed schedule. With only one bar - the default - this
whole row is inert; there's nothing to queue yet.

The same "which bar, which beat" information is echoed ambiently on the Glyph Matrix itself,
blended in behind whichever visualizer is selected rather than replacing it - loosely a line of
sheet music, one horizontal row per bar stacked in queue order, ticking left to right. It's a
passive cue, not a second control surface: once a queue gets busy enough for individual bars to
blur together on the small shared canvas, the dedicated bar row above is still the precise way to
navigate it. The queue, which bar is active, and the advance mode all persist across restarts, same
as tempo and beats-per-bar always have.

### Living on the Glyph Matrix

Activate qMetronome once from Settings → **Activate as Glyph Toy**, which registers it with
Nothing's Glyph Button toy carousel; after that it's selected and deselected like any other toy.
Selecting or deselecting it on the Glyph Button starts or stops the metronome - intentionally, not
a bug - which also means unlocking the phone while the toy is showing stops playback too, since the
Nothing OS Glyph Interface itself closes on unlock and there's no way to tell that apart from a
deliberate toy swap.

If you just want playback to survive the screen turning off, raising your phone's own screen
timeout (or disabling screen-off) while keeping qMetronome open works today with no extra setup.
For backgrounded, screen-locked, or switched-away-from-the-toy cases, Settings → Playback →
"Persistent playback" keeps the engine running independent of the toy's bind state, via a quiet
foreground-service notification - opt-in, off by default, and the notification/battery-optimization
prompts it may trigger are nudges rather than requirements.

While the toy is showing, touching the Glyph Button taps tempo the same way tapping the BPM number
does, and a long-press cycles through visualizers. The on-screen preview mirrors the exact same
gestures: swiping it left or right also cycles visualizers, double-tapping toggles play/stop, and
long-pressing it opens Settings - all in addition to, not instead of, the dedicated buttons. Even
at rest, the preview (and the real Glyph Matrix) shows a faint ghost of the current visualizer
rather than going fully dark, so the display always reads as "on." Random mute, in Settings, is a
practice tool in the same spirit - it skips the audible click on a probabilistic subset of beats
(ramping up gradually if you enable progressive start) without ever touching the underlying beat,
so you can wean off leaning on every click without the tempo itself drifting. Want your own
animation on the matrix instead of the built-in ones? See "Adding a new visualizer" below.

### Staying in sync with other gear

Settings → Clock → "Send MIDI clock" turns qMetronome into a MIDI clock source (24 ppqn) for other
apps or USB gear - the mirror image of following an external clock, which happens automatically
the instant MIDI Clock activity arrives from another app or a connected USB device, falling back to
internal timing if that feed goes quiet. USB devices get their own row in Settings → MIDI, with
independent "Follow clock" and "Send clock" toggles per device, so a device can be followed, sent
to, both, or neither; long-press a device to star it, and it reconnects automatically - restoring
whichever connections were active - the next time it's plugged in, whether or not Settings happens
to be open at the time. "Outgoing clock feel" (Mechanical vs. Organic) only affects the clock
*sent* to other gear, not this app's own click or flash: Mechanical actively corrects it for the
truest, most locked-in beat; Organic lets a followed clock's own natural timing variance through
unfiltered. See [`docs/external-midi-clock.md`](docs/external-midi-clock.md) for the full design
rationale behind both directions.

### A widget for the home screen

Long-press the home screen → **Widgets** → place qMetronome, and it shows the current BPM with a
START/STOP control. Tapping START/STOP toggles the same engine the app and the Glyph Toy use, so
it's always in sync with both; tapping anywhere else on the widget opens the full app for tempo,
visualizer, or MIDI settings. The number updates on its own whenever BPM changes from the app,
MIDI, or the widget itself - no need to remove and re-place it. It's deliberately BPM + play/stop
only, not a live mirror of the Glyph Matrix animation - see
[`docs/home-screen-widget.md`](docs/home-screen-widget.md) for why that was ruled out rather than
attempted.

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

Compose UI gestures go further than plain assertions: every major user-facing gesture (drag-to-
scrub, long-press-to-type, HOLD's staging, the bar queue, Settings' chip rows, the Glyph Matrix
preview's swipe/double-tap/long-press, layout toggles) has a test that drives the real production
composable through an actual simulated touch gesture, asserts genuine behavior, *and* captures a
screenshot - plus a short GIF for gestures a single still can't convey - in the same test. Those
captures are what [`docs/user-guide.md`](docs/user-guide.md) and the in-app Help screen are built
from (see the `TutorialTopic` glossary entry below); `./gradlew generateUserGuide` regenerates the
doc and fails outright if any topic's screenshot or video is missing, so it can never silently go
stale. See CONTRIBUTING.md's "Test coverage" section for how a new gesture gets one of these.

## Project governance

This is Quaternion Media's first mobile/cross-platform-device project, and
its decision-record discipline (`governance/qm/adr/`) is adopted from the
org's [`qm`](https://github.com/quaternionmedia/qm) constitution — vendored
as a submodule at `governance/qm`, checked out on this project's own branch
(`project/qmetronome`), which is where this project's own decision records
live (see `governance/qm/README.md`'s "Forking a new project" section for
why). Two real gaps showed up between that constitution (built around
self-hosted server infrastructure) and a sideloaded app built against a
closed hardware-vendor SDK; rather than papering over them, they're named in
[`docs/governance-perspective.md`](docs/governance-perspective.md) and the
corresponding `governance/qm/adr/` drafts, and fed back to the org as an open
question rather than resolved unilaterally.

## CI

`.github/workflows/ci.yml` runs the Glyph SDK import-boundary check (enforcing
the isolation claimed in `governance/qm/adr/DRAFT-glyph-matrix-sdk-dependency.md`),
then a full `assembleDebug` + `testDebugUnitTest`, on every push to `main` and
every pull request.

## Glossary

Reference for the classes, singletons, and mechanisms named above and elsewhere in this project -
alphabetical, one entry per term, each pointing at its actual file.

**`BpmUnit` / `BpmUnitEntryDialog`** (`ui/BpmUnit.kt`, `ui/BpmUnitEntryDialog.kt`) — BPM's
long-press dialog, unit-aware: chips switch between BPM (1–400, or 0.1–12000 with Extended range
on), BPH, and BPS mid-entry. Switching units lands on a sensible starting value in the new unit
rather than a literal, often-nonsensical arithmetic conversion of what was typed in the old one —
switching chips *is* the "convert between units" gesture. Replaces the generic
`NumericEntryDialog` for BPM specifically, since no other numeric field needs unit conversion.

**Brand marks** (`ui/BrandMarks.kt`) — `QmBrandMark` (bottom-left) and `AppBrandMark`
(bottom-center); long-press either to open its GitHub page. See "Theme colors" below for the
accent color used here.

**`ClickPlayer`** (`engine/ClickPlayer.kt`) — the older, discrete `MODE_STATIC`-retrigger click
implementation (`PERFORMANCE_MODE_LOW_LATENCY`), kept as the automatic fallback for whatever
device/OEM audio stack doesn't cooperate with `StreamingClickEngine`'s `MODE_STREAM` construction
or timestamp warm-up.

**`ClickSound` / `ClickSynth`** (`engine/ClickSound.kt`, `engine/ClickSynth.kt`) — `ClickSound`
separates *which* click to play (a bar's first beat gets a bigger, longer tone than every other
beat) from the playback plumbing; a new sound is a new tone-table row, not a rewrite. `ClickSynth`
renders each `ClickSound`'s `ClickSpec` (waveform/frequency/duration/gain, tunable in Settings →
Click) to samples.

**`HelpScreen`** (`ui/HelpScreen.kt`) — the in-app counterpart to `docs/user-guide.md`, reached via
the **?** icon next to the Settings gear on `MainScreen`. Reads the same `TutorialTopics` content
as the generated doc, but instead of a static screenshot per topic, embeds the *real, live*
production composable — the same shared-instance pattern `SettingsSheet` already established (one
composed instance, wired to the actual `MetronomeEngine`, not a disconnected demo copy), so trying
a control here really does change your tempo/settings, exactly like touching one in Settings would.

**`HoldButton`** (`ui/HoldButton.kt`) — the momentary/latching "shift key" for BPM and
beats-per-bar. While held, or latched, `MetronomeEngine.setBpm()`/`setBeatsPerBar()` stage instead
of applying immediately (shown in `RecordingRed`); a latched beats-per-bar change specifically
waits for the next bar's downbeat rather than applying mid-bar.

**`InternalClockSource`** (`engine/ClockSource.kt`) — the default beat-tick source. Drift-corrects
against `System.nanoTime()` so tempo doesn't slip over a long session, and re-checks the live BPM
every 30ms while waiting rather than committing to one long sleep sized for whatever tempo was in
effect when the wait began — otherwise a drastic tempo change made mid-wait had no effect (and the
beat/animation looked frozen) until that stale wait finished.

**`MainScreen`** (`ui/MainScreen.kt`) — the root Compose screen. Keeps the Glyph Matrix preview
(`MatrixPreview`) as the dominant, focal element with tempo/tap/play-stop and beats-per-bar
alongside it; everything else lives behind `SettingsSheet` or `HelpScreen`.

**`MatrixPreview`** (`ui/MatrixPreview.kt`) — renders the exact same frames as the real Glyph
Matrix hardware, so visualizers can be developed and demoed without a physical Nothing device.
Shows a dim ghost of the current visualizer at rest even when stopped (6% brightness idle frame),
so the AMOLED screen never looks fully off.

**`MetronomeEngine`** (`engine/MetronomeEngine.kt`) — the process-wide singleton holding tempo,
beat position, and the current Glyph frame as `StateFlow`s; the single source of truth so the
in-app preview and the real Glyph Matrix always show the same thing, whether or not the app UI is
open. Also owns random mute (`setMuteProbability`/`setProgressiveMuteEnabled`, which skips the
audible click on a probabilistic subset of beats without touching beat position, phase, or visuals
— a practice tool for not leaning on every click; progressive start ramps the chance up linearly
from 0 over `setProgressiveMuteRampBars`, default 8 bars, instead of applying at full strength
immediately) and the bar queue (see `TimeSignature`, `QueueMode`).

**Glyph Toy service** (`glyph/GlyphMatrixToyService.kt`, `glyph/MetronomeGlyphService.kt`) —
`GlyphMatrixToyService` is reusable Glyph Toy boilerplate (bind lifecycle, device registration,
Glyph Button message handling); `MetronomeGlyphService` is the concrete toy, starting/stopping the
engine with toy selection, tapping tempo on Glyph Button touch-down, and cycling visualizers on
long-press.

**`MetronomeWidget`** (`widget/MetronomeWidget.kt`) — the home screen widget (Jetpack Glance),
BPM + play/stop only. Updates are event-driven, not polled: `QMetronomeApp` collects
`MetronomeEngine.state`, filters it down to just `(bpm, isPlaying)` with `distinctUntilChanged()`,
and calls `updateAll()` only when one of those actually changes — never on the render loop's ~40Hz
phase ticks.

**`MidiClockSender`** (`midi/MidiClockSender.kt`) — generates MIDI clock (24 ppqn) from
`MetronomeEngine.state` and writes it to a registered set of destinations, turning qMetronome into
a clock *source*.

**`MidiClockSource`** (`midi/MidiClockSource.kt`) — the external-clock implementation: parses
real-time MIDI bytes and measures tempo from a smoothed rolling average of tick intervals,
regardless of transport. `MetronomeEngine` auto-switches to it the moment MIDI Clock activity
arrives, and falls back to `InternalClockSource` if that feed goes quiet for a few beats.

**`QueueMode`** — how the bar queue advances at each bar boundary during playback: `LOOP` (default)
wraps back to the first bar after the last; `ONCE` stops advancing once it reaches the last bar
(holding there rather than stopping playback outright); `MANUAL` never auto-advances — tapping a
bar's dot is the only way to move. Queue *position* isn't staged the way editing a bar's own values
is (staging-aware exactly the same as everything else in `MetronomeEngine`) — navigating which bar
you're looking at is closer to "which page am I on" than a pending settings change.

**`QueueOverlay`** (`visualizers/QueueOverlay.kt`) — an ambient version of "which bar, which beat"
baked directly into the Glyph Matrix frame itself (and its on-screen `MatrixPreview` mirror).
Loosely emulates a line of sheet music: the usable circle splits into one horizontal row per bar,
stacked top-to-bottom in queue order (taller rows for faster bars), with beats ticking left to
right and only the active bar's current beat pulsing. Blended in behind whichever visualizer is
selected rather than clipping it, so the two interact — deliberately a passive, ambient cue rather
than a second control surface.

**`SettingsSheet`** (`ui/SettingsSheet.kt`) — the full-screen translucent overlay (not a half-open
bottom sheet, so the matrix preview's flashes still glow through dimly behind it) holding
everything not on the main screen: a "Tempo & Bars" section embedding the *exact same*
`TempoTransportCluster` shown on the main screen (not a second copy that could drift), plus the
extended-BPM-range toggle, random mute, click toggle, visualizer picker, independent
beat-visualizer/bar-queue-background toggles, visual and audio timing offsets, a symbol-only-
controls toggle, and MIDI clock status/USB connection/clock-out. `MainScreen` stops composing its
own tempo/transport cluster while Settings is open, so there's only ever one live instance of it
rather than an invisible duplicate still recomposing underneath.

**`StarredMidiDevices`** (`midi/StarredMidiDevices.kt`) — persists which connection(s) (following,
sending, or both) were active for a starred USB MIDI device; a `MidiManager.DeviceCallback`
registered at app startup restores them automatically the moment that device reappears on the USB
bus, whether or not Settings is open.

**`StreamingClickEngine`** (`engine/StreamingClickEngine.kt`) — the primary audible-click
implementation. Rather than discretely retriggering audio per beat (which can only ever be timed
by a coroutine waking up at approximately the right wall-clock moment), one continuously-running
`MODE_STREAM` `AudioTrack` plays for the whole session; a dedicated writer thread mixes each
click's waveform into the stream at an exact sample-frame offset, computed by self-calibrating
`AudioTrack`'s frame-position/timestamp reporting against `System.nanoTime()` — timed by the audio
hardware's own sample clock, not a wakeup.

**Theme colors** (`ui/theme/`) — strictly monochrome (black/white only, matching the Glyph Matrix
and Nothing's own design language), with two deliberate exceptions: a navy accent (`QmNavy`) for
brand chrome (see "Brand marks") and `RecordingRed`, reserved for transient state/activity
indicators (a latched hold, a staged-but-not-yet-applied change, active MIDI clock) — the spirit of
a studio tally light, not a wash of color.

**`TimeSignature`** (`engine/TimeSignature.kt`) — a real "1×2 matrix" time signature: `beatCount`
(numerator) and `unitNoteValue` (denominator, e.g. the "4" in 4/4), edited independently, plus its
own `bpm` and `accentPattern` (reserved for later). Changing `unitNoteValue` rescales `bpm` to
preserve the underlying tempo (`bpm / unitNoteValue` held constant — the same "half note = 60 /
quarter note = 120" equivalence real notation uses), so switching a bar between, say, 6/4 and 3/2
redistributes the same bar duration into 3 clicks instead of 6 rather than silently doubling the
felt tempo. `MetronomeEngine` holds a *queue* of these (`timeSignatureQueue`) rather than just one
— a single-entry queue (the default) behaves exactly like a plain, unchanging time signature and
tempo, but adding more lines up a sequence of differently-metered, differently-paced bars the
engine cycles through at each bar boundary, applying each bar's own beat count *and* BPM as it
goes.

**`TimingDispatcher`** (`engine/TimingDispatcher.kt`) — `newTimingDispatcher()` hands out one
dedicated, elevated-priority (`THREAD_PRIORITY_URGENT_AUDIO`) thread per timing-critical role,
isolated from `Dispatchers.Default`'s shared, general-purpose pool *and* from every other role.
`MetronomeEngine` alone uses four of these (clock loop, render loop, audio-scheduling loop, and
`StreamingClickEngine`'s sample-clocked writer); a shared pool was tried first — a genuine single
thread measurably broke it, a fast tempo's audio-scheduling poll starved the actual beat-firing
coroutine entirely — before settling on one dedicated thread per role rather than a pool sized to
however many roles happen to exist today.

**`TutorialTopic`** (`tutorial/TutorialTopic.kt`) — the shared source of *content* for both
`docs/user-guide.md` and `HelpScreen`: id, title, end-user-facing description, category, and
whether it has a video. Each topic has exactly one Compose UI test that drives the real gesture,
asserts real behavior, and captures the screenshot (and, for motion-heavy gestures, a short GIF)
the doc embeds — see [Testing](#testing).

**`UsbMidiConnector`** (`midi/UsbMidiConnector.kt`) — the USB side of both MIDI directions;
`connectForFollowing()`/`connectForSending()` are independent, so a device can be followed, sent
to, both, or neither. A process-wide singleton like `MetronomeEngine`/`MidiClockSender`.

**`VirtualMidiClockService`** (`midi/`) — exposes the app as a MIDI destination other apps can
target with no hardware (see `res/xml/midi_device_info.xml`).

## Inspired by

- [Avi Bortnick](https://play.google.com/store/apps/developer?id=Avi+Bortnick) on the Play Store.
- [TimeGuru](https://play.google.com/store/apps/details?id=com.adambellard.timeguru) by Adam Bellard.

## License

qmetronome's own source is MIT-licensed (see [`LICENSE`](LICENSE)). That
covers everything in this repository except `app/libs/glyph-matrix-sdk-2.0.aar`,
which is a closed-source binary distributed by Nothing Technology Limited
under its own terms (see [`governance/qm/adr/DRAFT-glyph-matrix-sdk-dependency.md`](governance/qm/adr/DRAFT-glyph-matrix-sdk-dependency.md)
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
