# Cookbook

Quick-reference for day-to-day work. Assumes you're set up — if not, start
with [`onboarding.md`](onboarding.md).

Commands show `./gradlew` (macOS/Linux) and `gradlew.bat` (Windows) where
they differ. Android Studio's built-in terminal accepts `./gradlew` on all
platforms.

---

## Build commands

| Task | macOS/Linux | Windows |
|---|---|---|
| Run all tests | `./gradlew test` | `gradlew.bat test` |
| Build debug APK | `./gradlew assembleDebug` | `gradlew.bat assembleDebug` |
| Build + test (pre-PR check) | `./gradlew test assembleDebug` | `gradlew.bat test assembleDebug` |
| Clean build outputs | `./gradlew clean` | `gradlew.bat clean` |
| Run a single test class | `./gradlew test --tests "*.VisualizerRenderTest"` | same with `gradlew.bat` |

Debug APK lands at: `app/build/outputs/apk/debug/app-debug.apk`

---

## Device commands

```sh
# List connected devices
adb devices

# Install debug APK
adb install app/build/outputs/apk/debug/app-debug.apk

# Reinstall (keeps data)
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Stream logcat (filter to this app)
adb logcat --pid=$(adb shell pidof -s media.quaternion.qmetronome)

# Windows — if adb isn't in PATH
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install app\build\outputs\apk\debug\app-debug.apk
```

---

## Adding a visualizer

1. Create a class in `app/src/main/java/.../visualizers/`:

```kotlin
class MyVisualizer : GlyphVisualizer {
    override val id          = "my_visualizer"
    override val displayName = "My Visualizer"

    override fun render(matrixSize: Int, beat: BeatPhase): IntArray {
        val canvas = GlyphCanvas(matrixSize)
        // beat.phase   — 0.0..1.0 progress through the current beat
        // beat.isAccent — true on beat 1 of each bar
        canvas.filledCircle(canvas.center, canvas.center, matrixSize * 0.3f * beat.phase, 255)
        return canvas.toIntArray()
    }
}
```

2. Register it in `VisualizerRegistry.all`:

```kotlin
val all: List<GlyphVisualizer> = listOf(
    PulseVisualizer(),
    // ...
    MyVisualizer(),   // ← add here
)
```

Done. `VisualizerRenderTest` automatically covers every entry in `all` — run
`./gradlew test` to verify your new visualizer passes the two contracts:
- more total light at `phase == 0` than at `phase == 0.5` (beat reads without audio)
- more total light when `isAccent == true` than at same phase with `isAccent == false`

---

## Engine API quick-reference

```kotlin
// State (read-only, observe as StateFlow)
MetronomeEngine.state          // BeatPhase: bpm, phase, isAccent, isPlaying, beatsPerBar, beatIndex, totalBeats
MetronomeEngine.frame          // IntArray: current rendered frame (same as Glyph Matrix)

// Control
MetronomeEngine.setBpm(120f)      // clamped 1..400 by default, 0.1..3000 if extendedBpmRangeEnabled
MetronomeEngine.toggle()       // start if stopped, stop if playing
MetronomeEngine.tapTempo()     // tap-tempo input - decoupled from play (doesn't start playback)
                                // unless holdMode is Latched, in which case it commits + starts
MetronomeEngine.setVisualizer(myVisualizer)      // also pins it to whichever bar is active
MetronomeEngine.setVisualizerEnabled(false)      // hide the visualizer itself; independent of
                                                  // setQueueOverlayEnabled - run either, both, neither
MetronomeEngine.setBeatsPerBar(4)       // numerator - always edits whichever bar is active
MetronomeEngine.setUnitNoteValue(8)     // denominator - independent of beatCount, no UI staging;
                                         // rescales bpm to preserve tempo (bpm/unitNoteValue held
                                         // constant), e.g. 6/4@120 -> 3/2 becomes 3/2@60, same feel
MetronomeEngine.rescaledBpmForUnitNoteValueChange(120f, 4, 2)  // the rescale math, exposed for testing
MetronomeEngine.setVisualOffsetMs(50f)    // -500..+500 ms - phase-shifts the visual only
MetronomeEngine.setAudioOffsetMs(-30f)    // -500..+500 ms - negative leads via real lookahead
                                           // scheduling (MetronomeEngine.startAudioScheduling(),
                                           // StreamingClickEngine.scheduleBeat()), not a phase
                                           // shift; positive/zero schedules off the real beat
MetronomeEngine.setMuteProbability(0.3f)          // 0..1 chance a beat's click is skipped
MetronomeEngine.setProgressiveMuteEnabled(true)   // ramp that chance up over setProgressiveMuteRampBars
MetronomeEngine.setProgressiveMuteRampBars(8)     // 1..32, how many bars the ramp above takes
MetronomeEngine.setExtendedBpmRangeEnabled(true)  // unlocks 0.1..3000 bpm, shown as BPH/BPS outside 1..400
MetronomeEngine.setSymbolicControlsEnabled(true)  // main screen's tempo/transport controls go icon-only

// Hold/latch staging - while holdMode != Off, setBpm()/setBeatsPerBar() (and tapTempo(),
// which calls setBpm() internally) stage instead of applying immediately.
MetronomeEngine.beginHold()    // finger down: Off -> Momentary
MetronomeEngine.endHold()      // finger up: Momentary -> Off (flush); no-op while Latched
MetronomeEngine.toggleLatch()  // Off/Momentary -> Latched, or Latched -> Off (flush)
MetronomeEngine.holdMode       // StateFlow<HoldMode>: Off / Momentary / Latched
MetronomeEngine.stagedBpm      // StateFlow<Float?>, non-null while staging
MetronomeEngine.stagedBeatsPerBar  // StateFlow<Int?> - commits at the next bar's downbeat if
                                   // flushed while playing, since it can't apply mid-bar cleanly

// Bar queue - a single-entry queue (the default) behaves like a plain time signature and tempo.
// setBeatsPerBar()/setUnitNoteValue()/setBpm()/setVisualizer() always edit whichever bar is active.
MetronomeEngine.timeSignatureQueue  // StateFlow<List<TimeSignature>> - beatCount+unitNoteValue+bpm+
                                     // visualizerId each (visualizerId null = follow the global pick)
MetronomeEngine.queueIndex          // StateFlow<Int> - which bar is active
MetronomeEngine.queueMode           // StateFlow<QueueMode>: LOOP (default) / ONCE / MANUAL
MetronomeEngine.addBarToQueue()          // appends a copy of the active bar, jumps to it
MetronomeEngine.removeBarFromQueue(1)    // remove a specific bar by index; no-op if it's the only one
MetronomeEngine.removeCurrentBarFromQueue()  // shorthand for removeBarFromQueue(queueIndex.value)
MetronomeEngine.resetQueueToDefault()    // collapse back to a single default bar + LOOP mode
MetronomeEngine.nextQueueBar()           // manual navigation, clamped (not wrapping)
MetronomeEngine.previousQueueBar()
MetronomeEngine.goToQueueBar(2)          // jump directly (applies that bar's beats *and* bpm), clamped
MetronomeEngine.setQueueMode(MetronomeEngine.QueueMode.ONCE)
// The queue, queueIndex and queueMode all persist across restarts (see "Settings persistence"
// below) - setBpm()/setBeatsPerBar()/setUnitNoteValue()/goToQueueBar()/addBarToQueue()/
// removeBarFromQueue()/setQueueMode() each write through automatically.

// Click sounds - resolved per beat to a ClickSound.BAR / .ACCENT / .REGULAR (or null if muted/
// disabled) and handed to StreamingClickEngine.scheduleBeat(totalBeats, sound, targetNanos) - the
// primary playback path, one continuously-running sample-clocked AudioTrack. Each sound's own
// ClickSpec (waveform/frequency/duration/gain, tunable in Settings -> Click) is rendered to
// samples by ClickSynth. ClickPlayer.playClick() (a per-sound MODE_STATIC AudioTrack, discretely
// retriggered) is the automatic fallback if StreamingClickEngine fails to initialize.

// Glyph queue background - QueueOverlay.apply() blends a per-bar-row (sheet-music-like, one
// horizontal row per bar, beats ticking left to right), per-beat-tick ambient background into the
// rendered frame (max-blended, not overwritten, so it sits behind and interacts with whatever
// visualizer is selected) whenever timeSignatureQueue.size > 1 and setQueueOverlayEnabled(true);
// a no-op otherwise, applied once centrally in MetronomeEngine so every visualizer gets it free.

// Observe in Compose
val beat by MetronomeEngine.state.collectAsState()
val frame by MetronomeEngine.frame.collectAsState()
```

---

## Settings persistence

Settings survive app restarts via `MetronomeSettings` (SharedPreferences
under the hood). The engine loads them in `attach(context)` and writes
on every setter call. Keys live in `MetronomeSettings.kt` as constants.

---

## MIDI quick-reference

```
Virtual MIDI (inter-app):
  Following:  automatic — any incoming MIDI Clock is detected and adopted
  Sending:    Settings → Clock → "Send MIDI clock" toggle

USB MIDI:
  Following:  Settings → MIDI → tap a device → "Follow clock"
  Sending:    Settings → MIDI → tap a device → "Send clock"
  Starring:   long-press a device to star it → auto-reconnects on replug

Test tool: "MIDI Monitor" (iOS/macOS) or "MIDI Wrench" (Android) to observe clock bytes
```

See [`docs/usb-midi-test-plan.md`](usb-midi-test-plan.md) for the full
hardware test checklist.

---

## Releasing

```sh
# Alpha — debug APK, no signing needed, creates GitHub pre-release
git tag v0.1.0 -m "Alpha v0.1.0"
git push --tags

# Production — signed APK + AAB, requires 4 secrets in GitHub repo settings
git tag v1.0.0 -m "Release v1.0.0"
git push --tags
```

Both workflows run tests before building. Watch the Actions tab on GitHub.
Alpha builds appear as pre-releases; production builds appear as releases.

---

## Common errors

| Error | Cause | Fix |
|---|---|---|
| `JAVA_HOME is not set` | Gradle can't find the JDK | Set `$env:JAVA_HOME` (Windows) or `export JAVA_HOME=` (macOS/Linux) — see [onboarding.md](onboarding.md) |
| `SDK location not found` | No `local.properties` and no `ANDROID_HOME` | Create `local.properties` with `sdk.dir=` pointing to your SDK |
| `error: no devices/emulators found` | Phone not recognized by ADB | Accept the "Allow USB debugging?" dialog on the phone; try `adb kill-server && adb start-server` |
| `Glyph Toy button shows toast` | Not a Nothing Phone (3) or (4a) Pro | Expected — on other hardware the button tells you it's unsupported |
| CI fails on import boundary | `com.nothing.ketchum` imported outside `glyph/` | Move the import; Glyph SDK stays isolated in `app/.../glyph/` only |
| `Configuration cache problems` | Stale config cache after a build script change | `./gradlew --rerun-tasks` or delete `.gradle/configuration-cache` |

---

## Project layout cheat sheet

```
app/src/main/java/.../
  engine/
    MetronomeEngine.kt       ← singleton: all state, all control
    MetronomeSettings.kt     ← SharedPreferences wrapper
    BeatPhase.kt             ← data class: bpm, phase, isAccent, isPlaying
    ClockSource.kt           ← internal drift-corrected clock
    TimeSignature.kt         ← beatCount/unitNoteValue/bpm/accent pattern; engine holds a queue of these
    StreamingClickEngine.kt  ← primary click playback: one continuously-running, sample-clocked
                               MODE_STREAM AudioTrack; mixes each click at an exact sample-frame offset
    ClickPlayer.kt           ← fallback click playback: each ClickSound's waveform via a discretely
                               retriggered MODE_STATIC AudioTrack, used if StreamingClickEngine fails
    ClickSynth.kt            ← renders a ClickSpec (waveform/frequency/duration/gain) to samples
    ClickSound.kt            ← which click to play (BAR/ACCENT/REGULAR) - add sounds here
    TimingDispatcher.kt      ← one dedicated, elevated-priority thread per timing-critical role
                               (not a shared pool, not Dispatchers.Default)
  visualizers/
    GlyphVisualizer.kt       ← interface + GlyphCanvas
    VisualizerRegistry.kt    ← the list; add new ones here
    QueueOverlay.kt          ← bakes the bar-queue dot indicator into the rendered frame
    *Visualizer.kt           ← implementations
  midi/
    MidiClockSource.kt       ← follows external clock
    MidiClockSender.kt       ← sends clock out
    UsbMidiConnector.kt      ← USB device management + starring
    StarredMidiDevices.kt    ← persists starred devices + their follow/send state, keyed by deviceKey()
    VirtualMidiClockService  ← makes app appear as MIDI input
  glyph/
    GlyphMatrixToyService.kt ← Glyph SDK lifecycle (isolated here)
    MetronomeGlyphService.kt ← Glyph Button → engine wiring
  ui/
    MainScreen.kt            ← root Compose screen
    SettingsSheet.kt         ← full-screen settings overlay
    MatrixPreview.kt         ← on-screen LED preview
    HoldButton.kt            ← BPM/beats-per-bar staging - momentary hold or sticky latch
    HoldRepeatButton.kt      ← icon button that repeats on hold with ramping speed (queue +/- steppers)
    SteppedSlider.kt         ← standard slider: +/- steppers + long-press numeric entry
    NumericEntryDialog.kt    ← the numeric entry dialog itself (shared by BPM + sliders)
    TimeSignatureEntryDialog.kt ← beats + note value, two independent fields, one dialog
    BrandMarks.kt            ← QM + qMetronome brand marks, long-press to open GitHub
    icons/ExtraIcons.kt      ← locally-vendored icons material-icons-core doesn't include
  widget/
    MetronomeWidget.kt       ← Jetpack Glance home screen widget
    MetronomeWidgetReceiver.kt ← GlanceAppWidgetReceiver - the manifest-registered entry point
```
