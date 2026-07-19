# Publication Checklist

Tracks what's needed before qMetronome ships. Updated in place as items
complete — see [`docs/README.md`](README.md) for how this relates to the
feature docs and `governance/qm/adr/` decision records it references. This
doc is repo/code readiness; for the separate question of actually submitting to
Google Play and Nothing's distribution channel, see
[`app-store-checklist.md`](app-store-checklist.md).

There are two release tracks with different bars:

- **Alpha (`v0.x.x`)**: debug APK, no signing secrets, sideload-only. The
  `alpha-release.yml` workflow handles it automatically — `git tag v0.0.1 && git push --tags`
  is the entire process. Good for developer testing without any Google Play account.
- **Production (`v1.x.x`)**: signed APK + AAB, requires four signing secrets
  configured in GitHub. The `release.yml` workflow handles it. Needed for Play
  Store; the final manual verification items below apply here.

## Technical Polish

- [x] **Application identity**: `applicationId`/`namespace` were still
      Android Studio's `com.example.qmetronome` template default. Renamed to
      `media.quaternion.qmetronome` (package directories, all source files,
      `app/build.gradle.kts`, the one stale path in
      `governance/qm/adr/DRAFT-glyph-matrix-sdk-dependency.md`) before any beta install
      happens, since changing it later means every existing install has to
      be uninstalled and reinstalled from scratch.
- [x] **Thread safety review**: `MidiClockSender`'s destination set is
      mutated from the main thread (UI buttons, service `onCreate`/`onClose`)
      but iterated from the tick loop's background dispatcher - was a plain
      `MutableSet` (risk of `ConcurrentModificationException`), now
      `CopyOnWriteArraySet`. `wasPlaying` is written from both threads too,
      now `@Volatile`.
- [x] **USB device identity review**: the Settings USB device list matched
      "is this row the connected one" by display name string. Two devices
      can share a name (including the generic "USB MIDI device" fallback),
      which would make the UI show the wrong row as connected. Now matches
      by `MidiDeviceInfo.id`, the platform's actual stable identity.
- [x] **BPM persistence debounce**: the new press-and-hold/drag-to-scrub
      tempo controls can call `MetronomeEngine.setBpm()` dozens of times a
      second; it was persisting to `SharedPreferences` on every single call.
      Engine state stays instant, the disk write is now debounced (250ms).
- [x] **`MidiDevice.close()` exception safety**: it declares `throws
      IOException`; `UsbMidiConnector`'s disconnect paths weren't all
      guarded, so closing an already-unplugged device could crash whatever
      UI action triggered it. All three close sites now go through one
      `closeQuietly()`.
- [x] **Branch/CI mismatch**: `.github/workflows/ci.yml` triggers on
      `main`, but the repo's only branch was `master` - meaning CI would
      never have run on a push to the actual default branch. Renamed the
      branch to `main` (nothing had been pushed yet, so this was free).
- [x] **Tracked IDE session noise**: `.idea/deploymentTargetSelector.xml`
      (last-selected run device + timestamp), `.idea/studiobot.xml` (AI
      assistant sharing opt-in), and `.idea/planningMode.xml` (a session
      approval-state UUID) were committed in the previous round - all
      per-machine/per-session state, not shared project config. Untracked
      and added to `.gitignore`.
- [x] **ProGuard/R8 rules**: Release `optimization` is currently disabled
      (`app/build.gradle.kts`), so nothing is obfuscated today — but added
      forward-looking keep rules anyway (`app/src/main/keepRules/rules.keep`)
      for the Glyph Matrix SDK and Glance's reflection-dispatched
      `ActionCallback`/`GlanceAppWidget`/`GlanceAppWidgetReceiver` classes, so
      enabling optimization later doesn't silently break either.
- [x] **Final log cleanup**: `Log.d`/`Log.e` calls in `MetronomeWidget` and
      `QMetronomeApp` were preserved for stability during on-device testing.
      With R8 now enabled for release builds, these can be stripped
      automatically via ProGuard rules if desired, but they are safe to
      leave as-is for the first beta.
- [x] **Resource optimization**: Removed the unused legacy raster launcher
      icons (`mipmap-*/ic_launcher*.webp`) — `minSdk` 33 always resolves the
      adaptive vector icon (`mipmap-anydpi-v26/`), so they were dead weight
      left over from the default Android Studio template, never updated past
      the stock green-robot art.
- [x] **v0.0.25 dependency/APK size cleanup**: `androidx.compose.material:
      material-icons-extended` was an 87.5MB dependency (confirmed via the
      Gradle cache) whose ~2000+ generated icon classes were all fully dexed
      in the unshrunk debug build the alpha track ships — for a total of 14
      icons actually used. Replaced with the much smaller `material-icons-
      core` plus 8 locally-vendored `ImageVector`s
      (`ui/icons/ExtraIcons.kt`) built from simple geometry rather than
      hand-copied bezier data. Also dropped `androidx.appcompat` and
      `com.google.android.material` (the View-based Material Components
      library, not `material3`) — both were fully unused in code, pulled in
      only because `themes.xml`'s style inherited
      `Theme.MaterialComponents.NoActionBar`; switched to a plain platform
      theme instead. Net effect: the built debug APK dropped from ~64MB to
      ~29MB (the remainder is legitimate — Glance's widget stack transitively
      needs WorkManager/DataStore/Room-util).
- [x] **v0.0.25 audio output latency**: `ClickPlayer.buildTrack()`'s
      `AudioTrack.Builder` now sets `PERFORMANCE_MODE_LOW_LATENCY` — the
      standard platform API for a short, timing-sensitive one-shot trigger
      like this, previously left at the platform's default output path.
- [x] **v0.0.25 dedicated timing dispatcher**: `MetronomeEngine` and
      `MidiClockSender` previously ran their beat-firing/render/audio-
      lookahead/clock-tick loops on the implicit `Dispatchers.Default` shared
      thread pool, with no isolation from whatever else happened to be
      running on it. `engine/TimingDispatcher.kt`'s `newTimingDispatcher()`
      gives each subsystem its own small dedicated pool instead. Tried a
      genuine single thread first — measurably broke it (a tight lookahead
      poll starved the actual beat-firing coroutine at fast tempos, zero
      beats fired in 700ms at 400 BPM where several were expected); settled
      on 3 threads (matching the number of concurrent loops a single engine
      can run) after that regression and a follow-up, harder-to-reproduce
      scheduling-jitter flake in an unrelated bar-boundary timing test both
      pointed the same direction. **Superseded later in this same round** by
      the sample-clocked streaming audio engine entry below: `newTimingDispatcher()`
      now hands out exactly one dedicated thread per role rather than a
      shared pool, and `MetronomeEngine` grew a 4th role (the streaming
      writer) - this entry is kept as the historical record of why per-role
      isolation mattered in the first place, not a description of the
      dispatcher's current shape.
- [x] **v0.0.25 render-path allocation reduction**: `GlyphCanvas` used to
      allocate a fresh `IntArray(size*size)` on every visualizer `render()`
      call (40×/sec while playing) — now pooled/reused (`GlyphCanvas.
      BufferPool`), with one defensive `.copyOf()` kept in
      `MetronomeEngine.renderFrame()` right before anything reaches the
      published `StateFlow` (preserving the pre-existing "never republish a
      mutated array" safety guarantee this same file already documented and
      relied on). `MetronomeGlyphService`'s frame collector also now skips
      pushing to the real Glyph Matrix hardware when the new frame is
      pixel-identical to the last one actually pushed.
- [x] **Audio-lookahead busy-spin at high tempo (real bug, reported on-device
      at 300 BPM)**: `MetronomeEngine.startAudioLookahead()` (renamed
      `startAudioScheduling()` in the streaming-engine rewrite below)'s loop,
      after a beat's audio was resolved/fired, looped straight back to the
      top with **no delay at all** for the rest of the offset window (up to
      the full `|audioOffsetMs|`, default 30ms) — one of the engine's 3
      dedicated timing threads (a shared pool at the time; see the dedicated-
      timing-dispatcher entry above) pegged at 100% CPU every single beat. At
      60 BPM that's
      3% of the beat interval, easy to miss; at 300 BPM it's 15%, real
      scheduling contention with the tick/render loops sharing that same
      small pool — the actual cause of the reported timing breakdown, not a
      flaw in the drift-corrected clock math itself. Fixed: every loop
      iteration now delays a bounded slice regardless of whether it just
      fired, mirroring the pattern already correct in the "still waiting"
      branch and in `MidiClockSender`'s own resync loop. New regression test
      asserts click-to-click spacing stays even at 400 BPM.
- [x] **Per-sound `AudioTrack` pool**: `ClickPlayer` used to retrigger the
      same single `AudioTrack` instance per `ClickSound` every beat — now 2
      alternating instances per sound (`GlyphCanvas.BufferPool`'s round-robin
      precedent), so a retrigger never has to interrupt its own
      still-decaying predecessor. Bounded, precedented hardening alongside
      the busy-spin fix above, not a response to a separately-confirmed bug
      of its own.
- [x] **`docs/realtime-audio-roadmap.md`**: scoped (not implemented) the
      longer-term direction named alongside the bug report above -
      independent audio-channel routing per beat type, multiple simultaneous
      beat "threads" (true polyrhythm), per-beat-type MIDI actions - and what
      in today's design already accommodates vs. would need to change.
- [x] **v0.0.25 sample-clocked streaming audio engine (real rewrite, not a
      further mitigation)**: on-device testing after the busy-spin fix above
      still showed real placement error at 300+ BPM - the discrete
      `MODE_STATIC` retrigger model rides the OS scheduler's own granularity
      for *when the trigger call happens*, which isn't the same thing as
      *when the audio is produced*, no matter how well-isolated the thread
      is. `engine/StreamingClickEngine.kt` replaces it with one continuously-
      running `MODE_STREAM` `AudioTrack`; a dedicated writer thread mixes each
      click's waveform into the stream at an exact sample-frame offset,
      computed by self-calibrating `AudioTrack.getTimestamp()`'s
      frame<->nanoTime mapping against `System.nanoTime()` (the two-arg
      overload that names a specific timebase turned out to be `@SystemApi`,
      not in the public SDK - confirmed against the actual android-35/36 stub
      jars). `ClickPlayer`'s old discrete-retrigger path is kept as an
      automatic fallback (`StreamingClickEngine.hasFailedWarmup`,
      `MetronomeEngine.usingStreamingClickEngine`) if `MODE_STREAM`
      construction or timestamp warm-up doesn't cooperate on a given device.
      `MetronomeEngine` also moved from one shared timing dispatcher to four
      dedicated ones (clock/render/audio-schedule/audio-writer - the writer
      specifically can never share a thread with anything else, since its
      `AudioTrack.write()` calls block for real), and the render loop
      tightened from 25ms to 10ms ticks. Found and fixed two genuine
      concurrency bugs uncovered by this rewrite along the way: a beat-
      resolution cache that could clobber a validly-resolved future beat when
      accessed from truly parallel threads for the first time (fixed by
      switching from a single nullable slot to a small map keyed by beat
      number), and a resolve-ahead window that was firing a full beat early
      instead of just the configured offset's lead time (both caught by the
      existing Robolectric regression tests, not by inspection).
- [x] **v0.0.25 tempo-stability tightening pass, round 2**: a follow-up review
      of the streaming rewrite above found the predictive scheduling loop
      pushed a beat's schedule to `StreamingClickEngine` only once within the
      configured `audioOffsetMs`'s own lead window - correct for the old
      discrete-trigger model, but not enough for the new one: the writer
      thread's own `AudioTrack` buffer keeps its `framesWritten` cursor
      running some amount *ahead* of real time (steady-state, from its
      blocking `write()` calls), so a push arriving only `|offsetMs|` early
      could still land *after* the writer had already committed that frame -
      silently losing precise placement for exactly the leading case this
      rewrite was for, falling back to "fires at the earliest frame the
      writer can still place it" instead. Fixed: `StreamingClickEngine.
      leadMarginNanos()` exposes the buffer's own duration; the scheduling
      loop now pushes that much earlier still, capped by *both* an absolute
      ceiling (100ms - generous for a real low-latency buffer) and a fraction
      of the current beat interval (25% - what actually binds at fast tempos,
      where the absolute cap alone could still encroach into resolving more
      than one beat ahead, re-triggering the exact class of bug the rewrite's
      own resolve-ahead fix above was for; caught by the same regression
      tests going flaky again at 400 BPM before the fractional cap was
      added). Also tightened `InternalClockSource`'s own live-bpm poll
      granularity (`POLL_SLICE_MS`) from 30ms to 3ms, matching the audio-
      scheduling loop's own cadence - safe now that the clock loop has a
      genuinely dedicated, uncontended thread, and negligible overhead even
      at the very slow end of the new BPH range (a cheap nanoTime() poll,
      not a delay affecting the eventual beat's own firing precision).
- [x] **v0.0.25 BPH floor bug**: `EXTENDED_MIN_BPM` was a bare `0.1f` constant
      - exactly 6 BPH (0.1 × 60) - so the extended range could never go below
      6 beats/hour no matter how far a press-and-hold or drag went, regardless
      of the generous-sounding "0.1 BPM" label. Now derived from a new
      `MIN_BPH = 0.1f` (one beat per *ten hours*) instead of the other way
      around, so the number that actually matters is the explicit one.
- [x] **v0.0.25 logarithmic BPH/BPS stepping**: outside the normal 1-400 BPM
      range, both the +/- hold-repeat buttons and the BPM drag gesture used to
      apply the same flat step size the normal range uses - imperceptible
      near the old 3000 BPM ceiling, a giant single leap near 0.1 BPH.
      `MainScreen.kt`'s new `steppedBpm()` switches to a multiplicative
      formula outside the normal range instead (unchanged, flat stepping
      inside it - later tuned from 5%- to 10%-per-step, see the "BPM drag
      responsiveness cliff" entry below), so traversal feels far more
      equally responsive at either extreme of the 0.1-12000 BPM span.
- [x] **v0.0.25 drag-to-scrub on time signature numbers**: `TimeSignatureNumberRow`
      (both beats-per-bar and unit-note-value) had steppers and long-press-to-
      type but no drag gesture, unlike the BPM number. Added a second
      `pointerInput` block with a locally-remembered pixel accumulator (same
      shape as `PreviewBox`'s existing swipe accumulator - needed since these
      are `Int`-backed engine state, unlike BPM's `Float`, so reading the value
      back between drag events would silently discard sub-step progress) at
      the same 6dp-per-step sensitivity as BPM's own drag.
- [x] **v0.0.25 BPM/BPH/BPS unit switcher + unit-aware direct entry**: there
      was no way to type or jump directly to a value in BPH/BPS terms - the
      long-press dialog only ever accepted raw BPM (typing "30 BPH" meant
      mentally converting to "0.5" first), and reaching BPH/BPS at all meant
      dragging the whole way there through the log-scale steps. `ui/BpmUnit.kt`
      adds an explicit `BpmUnit` (BPM/BPH/BPS) with conversion helpers
      (`bpmToUnitValue`/`bpmFromUnitValue`/`bpmRangeFor`/`bpmDefaultUnitValue`),
      matching `bpmDisplayValue`/`bpmDisplayUnit`'s own thresholds exactly so
      the two can never disagree. `ui/BpmUnitEntryDialog.kt` (replacing the
      generic `NumericEntryDialog` for BPM specifically) adds unit chips to
      the long-press dialog - switching units is the "convert between BPH/BPM/
      BPS" affordance itself. Settings gets a matching "Jump to unit" chip row
      (mirroring the "Outgoing clock feel" chips) that jumps straight to a
      representative value in whichever unit is picked, auto-enabling extended
      range if needed - a quick escape from having to drag the whole way there.
- [x] **v0.0.25 BPM drag responsiveness cliff at the BPM=1 boundary**: right at
      the boundary between flat/additive stepping (inside 1-400) and
      multiplicative/log stepping (outside it), a log step's *absolute* size
      at bpm≈1 was `1 * (LOG_BPM_STEP_FACTOR-1)` - at the original 5% factor,
      a 20x smaller step than the flat range's fixed `+1`, so dragging across
      that exact boundary felt like the control suddenly barely responding.
      Bumped the factor 5%→10%, narrowing (not eliminating - a single constant
      factor can't be continuous at both the 1 *and* 400 boundary at once,
      since they differ by 400x) the cliff to ~10x. The new unit
      switcher/long-press-convert dialog above exist as the precise,
      non-drag path into BPH/BPS specifically because of this remaining
      tension - see `LOG_BPM_STEP_FACTOR`'s kdoc for the full reasoning.
- [x] **v0.0.25 BPS ceiling raised 3000→12000 BPM (50→200 BPS)**: an estimate,
      not a measured device limit - `StreamingClickEngine`'s sample-frame
      placement has no hard ceiling of its own, so the real constraint is
      `InternalClockSource`'s tick loop keeping up with its own overhead
      rather than repeatedly hitting its stale-wait resync path. A 5ms floor
      interval is deliberately generous headroom on a dedicated
      `THREAD_PRIORITY_URGENT_AUDIO` thread - see `EXTENDED_MAX_BPM`'s kdoc,
      and revisit from real on-device profiling if it's off in either
      direction for a given device.
- [x] **v0.0.25 "Outgoing clock feel" Settings copy clarified**: the
      Mechanical/Organic explanation didn't say this setting only affects the
      MIDI clock *sent to other apps/gear*, not this app's own click/flash -
      easy to test by just listening to the phone and conclude "doesn't do
      much," when the real test needs a device actually receiving the
      outgoing clock. Added a sentence making that explicit.

## Documentation

- [x] **Tempo controls & MIDI clock-out**: README's "Using qMetronome" section covers the
      press-and-hold step buttons, drag-to-scrub, and bidirectional MIDI clock (virtual + USB,
      both ways) as narrative; its Glossary covers the same ground by class/file name (`ui/` and
      `midi/`). `docs/external-midi-clock.md` and the MIDI ADR cover the design rationale,
      including the deliberate choice to allow rather than block
      following-and-sending-to-the-same-USB-device.
- [x] **Widget implementation**: `docs/home-screen-widget.md` documents the
      `collectAsState` reactivity pattern and the round-by-round path to it.
- [x] **User guide**: README's "Using qMetronome" section has "A widget for the home screen"
      (placement, toggle, the background-tap app shortcut, why it's not a live preview).
- [x] **Developer handover**: the standalone `walkthrough.md` has been
      folded into `docs/home-screen-widget.md` as that doc's fourth test
      round, instead of living as a second, overlapping narrative of the same
      debugging thread. [`docs/README.md`](README.md) is the new top-level
      index tying every doc to its corresponding ADR.

## Repository Governance

These are GitHub repo-settings changes, not code - deliberately left as manual
steps for whoever has admin access, rather than attempted via `gh api` against
a guessed ruleset.

- [ ] **Branch protection on `main`**: no ruleset has been decided yet. Typical
      starting point to consider: require the `ci.yml` status check to pass
      before merging, require at least one PR review, disallow force-push and
      branch deletion. Decide the exact rules and apply them via GitHub's
      Settings → Branches UI (or `gh api repos/:owner/:repo/branches/main/protection`
      once the ruleset is decided) - not something to configure blind.

## Compliance & Meta

- [x] **Iconography**: Launcher icon finalized (navy/white monochrome
      metronome mark matching the app's theme) and the widget now sets
      `previewImage` (reusing the existing `toy_preview` asset rather than
      adding a redundant new one).
- [x] **Permissions**: Reviewed the merged manifest. Beyond the Glyph Matrix
      SDK's own permission, everything else (`WAKE_LOCK`,
      `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`,
      the dynamic-receiver signature permission) is auto-merged by
      WorkManager, a transitive dependency of Glance — not declared by this
      app directly, and not safely removable without breaking the widget
      update reliability the last several rounds of work were about. Nothing
      unnecessary found.
- [x] **Nothing key**: Hardcoded to the SDK's development placeholder (`"test"`).
      No production key is needed or planned — shipping with `"test"` is intentional.

## Final Manual Verification

All of these need a physical device and are tracked here rather than
attempted blind. See `docs/home-screen-widget.md`'s manual test checklist
for the widget-specific ones (cold start, app-shortcut tap, idle-period
reactivity) and `docs/usb-midi-test-plan.md` for the USB MIDI ones.

- [ ] **Cold start**: place widget on a fresh install before opening the app.
- [ ] **Multi-widget**: place two widgets and verify they stay in sync.
- [ ] **Dark/light mode**: verify widget readability against different
      wallpapers/launcher themes — the widget (like the app) is intentionally
      black/white regardless of system theme, so there's no light/dark code
      branch to test, just legibility in practice.
- [ ] **Tempo controls on-device**: press-and-hold acceleration feel and
      drag-to-scrub sensitivity were tuned by reading the code, not by
      holding a phone - confirm both feel right (not too twitchy, not too
      sluggish) on real hardware, and that all three input methods (tap
      tempo, step buttons, drag) agree with each other and the Glyph Matrix.
- [ ] **MIDI clock-out, virtual**: confirm another app on the same phone can
      pick "qMetronome Clock" as a MIDI input and actually receives clock
      when "Send MIDI clock" is on - this path has never run end-to-end on a
      device, only compiled and unit-tested against a fake `MidiReceiver`.
- [ ] **MIDI clock-out, USB**: per `docs/usb-midi-test-plan.md` section 5 -
      sending to a USB device, following one device while sending to
      another, and the deliberately-allowed-not-blocked case of following
      and sending to the *same* device (the one combination with a real,
      documented, unverified loop risk if that device echoes MIDI Thru).
- [ ] **MIDI device starring/auto-reconnect**: per `docs/usb-midi-test-plan.md`
      section 6 - star a device, unplug/replug it (with Settings both open
      and closed) and confirm it reconnects and restores its follow/send
      state automatically, and that unstarring actually stops that from
      happening rather than just hiding the star icon.
- [ ] **Idle glyph**: with the metronome stopped, the preview should show a
      very faint version of the current visualizer's resting pose rather than
      a black square. Confirm it's visible but not distracting, and that it
      updates when the visualizer is changed from Settings.
- [x] **Double-tap and long-press on preview**: now covered end-to-end by
      `PreviewGestureScreenshotTest` (double-tap toggles play/stop, long-press
      opens Settings, swipe cycles visualizers - see
      [`docs/user-guide/`](user-guide/README.md)'s Glyph Matrix section). Remaining
      manual nuance, if ever revisited: real-finger gesture disambiguation
      timing (a brief swipe not accidentally read as a long-press) isn't
      something a synthetic touch sequence can fully stand in for.
- [ ] **BPM direct entry**: long-pressing the BPM number should open a
      numeric entry dialog. Confirm the keyboard auto-focuses, the current
      BPM is pre-selected, values outside 1–400 are rejected, and the
      running engine immediately reflects the new tempo on confirm.
- [x] **HOLD staging**: now covered by `HoldButtonScreenshotTest`'s momentary
      test - stages a tempo change while held (display shows the staged
      value, engine's real bpm untouched), then flushes it in one step on
      release.
- [ ] **Visual timing offset feel**: set a large positive or negative offset
      (e.g. ±200 ms) in Settings → Visual timing offset, run the metronome,
      and confirm the flash/animation visibly leads or lags the audible click
      by a perceptible amount. Reset to 0 ms and confirm alignment restores.
- [ ] **Compact landscape layout**: rotate to landscape with the toggle off
      (default) — controls should overflow as before. Enable compact mode in
      Settings, rotate again — preview and controls should sit side-by-side,
      all controls reachable without scrolling.
- [ ] **Random mute + progressive mute ramp**: enable random mute at a mid
      probability and confirm clicks actually drop out audibly and
      unpredictably (not on a fixed pattern); separately, enable progressive
      mute and confirm the mute probability visibly ramps over the configured
      duration rather than jumping straight to its target.
- [ ] **Latch mode - stop() force-clear only**: the promote-to-latch/stays-
      staged-without-holding/subsequent-tap-flushes-and-exits sequence is now
      covered by `HoldButtonScreenshotTest`'s sticky-latch test. Not covered
      (still needs a real device/session): confirm `stop()` force-clears an
      active latch rather than leaving it stuck engaged across a stop/start
      cycle.
- [ ] **Bar queue end-to-end**: add several bars with different beats-per-bar/
      BPM/unit-note-value combinations, confirm each recalls its own tempo
      correctly on tap-to-jump, confirm long-press removes a bar, confirm the
      trash/reset button (always showing its red destructive-action badge)
      requires a long-press (a plain tap should do nothing) before it clears
      the whole queue back to a single default bar, and cycle through
      all three `QueueMode`s (confirm playback order/looping behavior matches
      each mode's intent).
- [ ] **Per-bar visualizer pinning**: pin a specific visualizer to one bar in
      the queue (distinct from the global visualizer pick), jump to that bar
      via tap and via normal playback advance, and confirm the pinned
      visualizer is the one that actually renders both times rather than the
      global default.
- [ ] **Tempo-preserving denominator changes**: change a bar's unit note
      value (e.g. 6/4 ↔ 3/2 style) and confirm the audible tempo is unchanged
      (BPM rescales to compensate) rather than the metronome suddenly
      speeding up or slowing down.
- [ ] **Ambient glyph background (`QueueOverlay`) on hardware**: with a
      multi-bar queue active, confirm the per-bar row overlay actually renders
      on the physical Glyph Matrix (not just the on-screen `MatrixPreview`)
      and blends *behind* the active visualizer (brightens, never masks or
      clips it) — this pixel-blending behavior is unit-tested against
      `IntArray` output but has never been confirmed against the real LED
      hardware's brightness/gamma response.
- [ ] **v0.0.21 visualizer toggles**: in Settings, exercise all four
      combinations of "Bar queue background" and "Beat visualizer" (both on,
      both off, each on alone) and confirm the Glyph Matrix output matches
      each combination — in particular, confirm "both off" shows a blank/idle
      glyph rather than a stale frame from before the toggle was flipped.
- [ ] **v0.0.23 visual offset default**: on a fresh install (or after
      clearing app data), confirm Settings → Visual timing offset shows -50 ms
      rather than 0, and that the flash/click feel reasonably in sync out of
      the box - adjust from there by feel per device.
- [ ] **v0.0.23 MIDI transport start**: connect an external MIDI clock
      source that sends Start (`0xFA`) before its clock ticks, hit play on
      that source, and confirm the Glyph Matrix shows exactly one "bar"
      flash before the first regular beat - not two in a row.
- [ ] **v0.0.23 tunable click sounds**: in Settings → Click, expand the
      section and switch between the Bar/Beat/Accent tabs, changing each
      one's waveform, frequency and duration; enable "Audible click" and
      confirm all three sound distinct and update live as the sliders move.
      Confirm a fast tempo (short beat interval) doesn't glitch or drop
      clicks when a sound retriggers before its previous decay finished.
- [ ] **v0.0.23 Firework visualizer**: select "Firework" in Settings →
      Visualizer and confirm it renders a radiating spark burst on both the
      on-screen preview and the physical Glyph Matrix, bigger/brighter on
      bar 1 than on other beats.
- [ ] **v0.0.23 Mechanical/Organic clock-out feel**: with "Send MIDI clock"
      on and following an external clock (repeater case), toggle Settings →
      Clock → "Outgoing clock feel" between Mechanical and Organic and
      confirm a listening device/DAW can perceive the difference - Mechanical
      should feel steadier, Organic should let the followed clock's own
      natural imperfection through rather than ironing it flat. **This mode
      has no effect on this app's own click/flash** - it only changes the
      MIDI bytes sent out, so this genuinely can't be evaluated by ear on the
      phone alone; a real receiving device/DAW is the only way to hear a
      difference (confirmed as working-as-designed this round when reported
      as "not much effect" - the report was testing without a receiver).
- [ ] **v0.0.23 outgoing clock phase lock**: run a long session (several
      minutes) with "Send MIDI clock" on in Mechanical mode, internal clock
      (not following anything), and confirm a device receiving our clock
      stays aligned to our own audible/visual beat throughout, rather than
      gradually drifting out of phase - this was the bug Mechanical mode's
      per-beat resync fixes; Organic mode is expected to drift more freely.
- [ ] **v0.0.23 collapsible Settings UI**: open Settings fresh and confirm
      every section shows collapsed by default with exactly one control
      visible (its title's single most relevant switch/slider), expands on
      tap without also toggling that control, and that the Click section's
      Bar/Beat/Accent tabs switch cleanly without losing any tab's edits.
- [ ] **v0.0.23 long-press-to-reset**: confirm a plain tap on the bar-queue
      trash/reset icon does nothing, and only a long-press actually clears
      the queue back to a single default bar.
- [ ] **v0.0.23 Glyph Toy unlock behavior**: with persistent playback OFF
      (default), start playback via the Glyph Toy, lock and unlock the
      phone, and confirm playback stops (today's existing, intentional
      behavior - unchanged). Separately, confirm deliberately swiping to a
      different Glyph Toy also still stops it.
- [ ] **v0.0.23 persistent playback, happy path**: enable Settings →
      Playback → "Persistent playback", grant both prompts (notification,
      battery exemption) when they appear, start playback via the Glyph Toy,
      then lock/unlock the phone (and separately, background the app
      entirely) - confirm playback keeps running both times, a "qMetronome"
      notification is visible showing BPM while playing, and tapping the
      notification's "Stop" action actually stops playback and dismisses it.
- [ ] **v0.0.23 persistent playback, permissions declined**: repeat the
      above but decline the notification permission and/or the battery-
      optimization exemption prompt - confirm persistent mode still keeps
      playback running through lock/unlock and backgrounding either way,
      just without a visible notification if that permission was declined.
- [ ] **v0.0.23 persistent playback, turned back off**: with persistent
      playback on and the notification showing, turn the setting back off -
      confirm the notification disappears promptly, and that Glyph Toy
      unlock/toy-switch now stops playback again (today's default behavior
      resumes).
- [ ] **Drastic mid-beat tempo changes (both directions)**: while playing,
      make a large, sudden manual tempo change mid-beat - both a big jump up
      (e.g. numeric-entry from 60 to 300) and a big jump down (e.g. 300 to
      60) - and confirm the very next click/flash lands on the new tempo's
      schedule in both cases, not just when speeding up. Also confirm the
      visual flash and audible click stay in sync with each other throughout
      (no early-decay-then-frozen flash, no backward phase jump) immediately
      after a drastic change.
- [ ] **v0.0.24 audio timing offset, lead**: in Settings → Audio timing
      offset, set a negative value (e.g. -100 ms beyond the -30 ms default)
      and confirm the click is audibly *ahead* of the visual flash by roughly
      that amount - this is genuine lookahead scheduling, not a delay trick,
      so confirm it holds up across a range of tempos (slow and fast) without
      stuttering, skipping, or doubling a click.
- [ ] **v0.0.24 audio timing offset, lag**: set a positive value and confirm
      the click now trails the visual flash instead, and that switching
      between a negative and a positive value while playing takes effect
      cleanly on the next beat or two, not with a stuck/duplicated click.
- [ ] **v0.0.24 audio offset vs. visual offset, independent**: confirm the
      two offsets (Settings → Visual timing offset vs. Audio timing offset)
      can be set to different values and both apply independently - e.g. a
      more-negative audio offset than visual offset should make the click
      lead the flash, not just both leading the true beat by the same amount.
- [ ] **v0.0.24 audio offset while following external MIDI clock**: with
      "Send MIDI clock" off and instead *following* an external MIDI clock
      source, set a negative audio offset and confirm the click does **not**
      lead the incoming clock (there's nothing of ours to predict there) -
      it should just play reactively, same as a 0ms offset would.
- [ ] **v0.0.24 real version string on a sideloaded/dev build**: check
      Settings' version footer on a build installed directly from Android
      Studio (not a tagged CI release) - confirm it shows a real
      `git describe`-derived string (e.g. `v0.0.23-4-gabc1234-dirty`) rather
      than the old hardcoded `1.0 (build 1)`.
- [ ] **v0.0.24 Settings "Tempo & Bars" live mirror**: open Settings while
      the metronome is playing, expand "Tempo & Bars", and confirm the BPM,
      time signature and bar-queue dots shown there update live in step with
      the main screen and the Glyph Matrix - including which bar is
      highlighted as active - rather than only updating on open/close.
      Confirm editing tempo/beats-per-bar from *within* Settings also updates
      the main screen and Glyph Matrix immediately.
- [ ] **v0.0.24 tap tempo decoupled from play**: with the metronome stopped,
      tap the BPM number/TAP button several times - confirm the tempo number
      updates but playback does **not** start. Separately, engage HOLD's
      sticky latch (long-press or double-tap HOLD), tap out a tempo (more
      than once), and confirm that tapping while latched both commits the
      tapped tempo *and* starts playback at that tempo, using the current
      time signature.
- [ ] **v0.0.24 extended BPM range - re-clamp-on-disable only**: the
      below-1-BPM/above-400-BPM display switch itself is now covered by
      `BpmDragBoundaryScreenshotTest` and `SettingsJumpToUnitScreenshotTest`.
      Not covered (still needs a device): turning the toggle back off with
      tempo out of the normal range snaps it back within 1-400 BPM rather than
      getting stuck, and that the Glyph Matrix/audible click still track
      correctly at both extremes.
- [ ] **v0.0.24 progressive mute ramp length**: in Settings → Random mute,
      enable "Progressive start", set a short ramp (e.g. 2 bars) and a long
      one (e.g. 24 bars) on separate runs, and confirm the mute probability
      audibly reaches full strength faster with the short ramp and more
      gradually with the long one, rather than the ramp length having no
      audible effect.
- [ ] **v0.0.25 APK size**: sanity-check the installed app's size (Settings →
      Apps → qMetronome on the device) against the previous alpha - expect a
      dramatic drop (measured ~64MB → ~29MB in the build that produced this
      round) now that `material-icons-extended`/`appcompat`/`material` are
      gone.
- [ ] **v0.0.25 vendored icons render correctly**: every icon that used to
      come from `material-icons-extended` is now hand-built - confirm each
      one still reads clearly at its actual on-screen size: the Settings
      section expand/collapse chevrons, the bar-queue LOOP/ONCE/MANUAL mode
      icons (repeat arrows / skip-next / tap), the play/pause icon, the +/-
      steppers everywhere (BPM, time signature, sliders), and the USB MIDI
      star/star-outline. None should look clipped, misaligned, or overly
      crude compared to the old library icons.
- [ ] **v0.0.25 theme/background unchanged**: launch the app fresh (cold
      start, not resumed) and confirm the splash/window background is still
      solid black with no flash of an unexpected color or system theme
      bleeding through, now that the theme no longer inherits from
      AppCompat/MaterialComponents.
- [ ] **v0.0.25 low-latency audio**: with "Audible click" on, run a fast
      tempo (e.g. 300+ BPM) for an extended stretch and confirm no clicks
      glitch, drop, or double-trigger - `PERFORMANCE_MODE_LOW_LATENCY` changes
      the platform's audio path and is worth specifically stress-testing.
- [ ] **v0.0.25 unified Settings "Tempo & Bars" control surface**: open
      Settings while playing - confirm TAP, play/stop, and HOLD now appear
      there too (not just BPM/bars), centered, and fully functional (tapping
      TAP in Settings actually taps tempo, HOLD stages/latches, play/stop
      toggles playback) - not just a read-only mirror anymore.
- [ ] **v0.0.25 no redundant recomposition while Settings is open**: with the
      metronome playing, open Settings and confirm the main screen's own BPM/
      transport controls are no longer visibly live-updating underneath the
      translucent backdrop (only the Glyph Matrix preview's flash should still
      glow through) - closing Settings should immediately show the main
      screen's controls resume, correctly caught up to the current state.
- [ ] **v0.0.25 symbol-only control mode**: enable "Symbol-only controls" in
      Settings → Layout and confirm every targeted label disappears from the
      main screen (and Settings' "Tempo & Bars" mirror): TAP becomes a tap
      icon, HOLD becomes a lock icon, the BPM unit text (BPM/BPH/BPS)
      disappears, and both "• staged" texts (BPM and beats-per-bar) become a
      small red dot instead. Turn it back off and confirm every label returns
      exactly as before. With a screen reader on, confirm the icon-only
      elements still announce something meaningful (not silent).
- [ ] **v0.0.25 visualizers after buffer-reuse change**: cycle through every
      visualizer (swipe left/right on the preview) while playing, including
      switching mid-decay and stopping/restarting repeatedly, and confirm none
      of them show ghosting, stale pixels, or a frozen/corrupted frame - the
      classic symptom a buffer-reuse bug would produce. Also confirm a
      multi-bar queue's ambient background (`QueueOverlay`) still renders
      correctly layered under the active visualizer.
- [ ] **v0.0.25 audio timing at 300+ BPM (the actual reported bug, now against
      the rewritten sample-clocked path)**: run at 300 BPM and faster (up to
      `MAX_BPM`) with the audible click on and the default audio offset, and
      confirm click placement feels steady and even, with error well under
      the originally-requested 1/300 tolerance - not the audible breakdown
      originally reported, and tighter than the earlier busy-spin fix alone
      achieved. This is the deciding proof for the whole rewrite: a JVM/
      Robolectric test can verify the *decision* logic (which beat, which
      sound, resolved once) but its `AudioTrack` shadow doesn't model real
      HAL/buffer timing, so it cannot itself confirm sample-accurate
      placement - only a real device can.
- [ ] **v0.0.25 streaming engine reliability over a long session**: run the
      audible click continuously for several minutes, changing tempo and
      switching click sounds/waveforms mid-playback several times, and
      confirm no glitches, dropouts, or a click that goes silent/stuck - the
      continuously-running `MODE_STREAM` `AudioTrack` (unlike the old
      per-beat retrigger) never stops between beats, so this is the scenario
      most likely to expose a writer-thread stall or an unbounded
      buffer/latency drift the shorter existing tests wouldn't catch.
- [ ] **v0.0.25 graceful fallback to discrete retrigger playback**: this can't
      be forced from the UI, but watch logcat for `StreamingClickEngine`
      warnings (`MODE_STREAM AudioTrack construction failed`, `AudioTrack.play()
      failed`, or `failed to warm up in time`) on whatever device is used for
      the rest of this checklist - confirm that if any of these fire, the
      click still plays audibly (via the `ClickPlayer` fallback) rather than
      going silent. Absence of these warnings is itself useful signal that
      the primary path is the one actually being exercised throughout every
      other audio item on this list.
- [ ] **v0.0.25 negative audio offset genuinely leads, not just "close"**: set a
      clearly audible negative offset (e.g. -100ms) at a fast tempo (300+
      BPM) and confirm the click leads the visual flash by the *full*
      configured amount consistently, not by a visibly smaller amount that
      would suggest the writer's own buffer-ahead time is eating into the
      requested lead (see `StreamingClickEngine.leadMarginNanos` - the
      follow-up fix that specifically targets this). If the lead feels
      shy of what's configured, that's the signal this margin needs
      revisiting for whatever device this is tested on.
- [ ] **v0.0.25 extended range reaches well below 6 BPH**: with "Extended
      range (BPH/BPS)" on, drag or press-and-hold the tempo all the way down
      and confirm it keeps going well past the old 6 BPH floor (down toward
      0.1 BPH) rather than stopping there.
- [ ] **v0.0.25 BPH/BPS hold and drag feel logarithmic**: with extended range
      on, press-and-hold the +/- steppers (and separately, drag) while deep in
      BPH or BPS territory, and confirm each step feels like a *consistent
      proportional* change (similar to how it feels near 120 BPM) rather than
      either imperceptibly small (near 0.1 BPH) or a jarring single leap (near
      200 BPS).
- [x] **v0.0.25 time signature drag-to-scrub**: now covered by
      `TimeSignatureDragScreenshotTest` - drags both the beats-per-bar and
      unit-note-value numbers through a real gesture and confirms each moves
      the expected direction.
- [ ] **v0.0.25 BPM drag direction, above and below 1 BPM - feel only**: the
      directional-correctness question this item was originally tracking (the
      manual bug report that started this whole investigation) is now
      resolved by an actual regression test driving a real gesture -
      `BpmDragBoundaryScreenshotTest` drags across the BPM=1 boundary in both
      directions and asserts the unit/value move the expected way each time,
      not just the underlying math. What's left, real-device-only: confirm
      the responsiveness cliff right at the boundary feels less jarring than
      before (a step change is still expected, just not "stopped responding").
- [x] **v0.0.25 BPM long-press unit-aware entry**: now covered by
      `BpmUnitEntryDialogScreenshotTest` - opens on the natural unit showing
      the real current value, switching chips resets to a sensible default
      rather than a nonsense converted number, and confirming converts back
      to raw bpm correctly.
- [x] **v0.0.25 Settings "Jump to unit" switcher**: now covered by
      `SettingsJumpToUnitScreenshotTest` - tapping each of the BPM/BPH/BPS
      chips jumps to a representative value in that unit's range and
      auto-enables Extended range. Not explicitly asserted: tapping the
      already-active unit's chip is a no-op (a minor edge case, low risk).
- [ ] **v0.0.25 BPS ceiling at 200 BPS (12000 BPM)**: with extended range on,
      push the tempo to the new maximum (via the Settings switcher, direct
      entry, or drag/hold) and confirm the click still fires steadily at that
      rate without the engine falling behind/stuttering - the ceiling is an
      estimate, not a measured limit, so this is the test that tells us
      whether it's too aggressive (or overly conservative) for a given device.
- [ ] **v0.0.25 long custom click duration at a fast tempo**: in Settings →
      Click, set a sound's duration close to or beyond the beat interval at a
      fast tempo (e.g. 150ms+ duration at 300+ BPM, where the interval is
      200ms or less), and confirm no audible glitch/stutter/clipping. The
      primary path (`StreamingClickEngine`) *mixes additively* into the
      continuous stream rather than retriggering, so an overlapping decay
      tail should sound like genuine layering (two hits overlapping), not a
      hard cut or silence - listen specifically for audible clipping/
      distortion where two waveforms sum past 16-bit range. If the
      `ClickPlayer` fallback is active instead (see the graceful-fallback
      item above), its per-sound `AudioTrack` pool exists so a retrigger
      never has to interrupt its own still-playing predecessor - same
      scenario, different mechanism, same "no audible glitch" bar.
- [ ] **v0.0.25 no CPU spike from the audio lookahead loop**: with the click
      on and a fast tempo, watch device temperature/battery drain (or a
      profiler if available) over a few minutes of continuous playback and
      confirm nothing suggests a thread pegged at 100% CPU - the busy-spin
      bug's other symptom besides audible jitter.
- [ ] **v0.0.28 first-beat lag/catch-up, fixed by keeping the streaming click engine warm**:
      reported as "substantial lag in the first beat followed by what seems like a
      catchup, especially noticeable when trying to line up with an existing tempo."
      Root cause: `StreamingClickEngine` was torn down and rebuilt on every single
      play/stop toggle, re-paying `AudioTrack.getTimestamp()`'s warm-up wait (and, on
      real hardware, real HAL/buffer settling time Robolectric can't model) every time,
      not just once per app session - most commonly triggered by the Glyph Toy's own
      lock/unlock-stops-playback behavior (persistent mode off) mid-practice-session, not
      just a cold app launch. With the click on at a moderate tempo, toggle play/stop (or
      lock/unlock the phone via the Glyph Toy) several times in a row and confirm the
      first click of each *subsequent* session lands as promptly as later beats, not with
      the previously-reported lag/catch-up. Compare specifically against the very first
      play after a fresh install/launch, which is still expected to pay a one-time
      warm-up cost - only *repeated* sessions should now feel instant.
- [ ] **v0.0.28 no stale click after pressing stop**: press stop immediately after a
      beat's predictive schedule would have just landed (timing is inexact - repeat
      several times to catch it) and confirm no click sounds after the press. The
      streaming engine's `AudioTrack`/writer now keep running (mixing silence) between
      sessions instead of being torn down, so this specifically guards against a
      schedule that was already pending at the moment of stop still audibly firing.
- [ ] **v0.0.28 long idle-then-resume session**: leave the app open (or backgrounded,
      persistent mode off) for several minutes without playing, then press play and
      confirm the first beat still lands promptly - guards against the writer having
      silently died while idle with nothing to catch it (the one scenario that still
      forces a real rebuild - see `StreamingClickEngine.start()`'s liveness check).
- [ ] **v0.0.29 first-beat count-in (Gap B follow-up) - target met**: reported that the
      warm-keep fix above wasn't sufficient on its own - beat 0 structurally has no
      lead-scheduling window the way every later beat does, traced and confirmed in
      `docs/timing-accuracy-benchmark.md`. Fixed with a small, user-tunable, bounded pause
      (Settings → Audio timing offset → "First beat count-in," default 100ms cap) that
      gives beat 0's audio the same genuine lead every other beat gets, at the cost of a
      brief delay before the very first press's flash/click. Two follow-up hypotheses for
      the initial ~31-36ms residual (routing beat 0 through the same polling loop
      steady-state beats use; keeping that loop's own coroutine warm across sessions) were
      tried and measured the same day - one a no-op, one a measured regression, reverted.
      A round of research into how other systems solve this (Web Audio's lookahead
      scheduler, Android's AAudio/Oboe guidance, professional metronome apps - full
      citations in `docs/timing-accuracy-benchmark.md`) led to the actual fix: a
      self-calibrating lead margin (`LeadMarginCalibrator`) surfaced, via a temporary
      diagnostic, that `AudioTrack.getMinBufferSize()` was sizing this engine's buffer at
      ~120ms on the test device - two orders of magnitude larger than a real low-latency
      burst, and large enough to silently route it off AudioFlinger's fast mixer path
      despite `PERFORMANCE_MODE_LOW_LATENCY` being set. Sizing the buffer from
      `AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER` instead (`StreamingClickEngine.
      configureFromDevice`) dropped steady-state placement error ~3.6x (47ms → 13ms) and
      beat 0's excess to **~2ms** (Nothing A024, SDK 36, 2026-07-09) - inside the doc's
      ≤10ms target, close to its ≤5ms stretch goal. Remaining manual check: confirm the
      count-in's pause is barely perceptible at the default cap, and that cap=0 still
      restores the exact old instant-but-unled behavior for comparison.
- [ ] **v0.0.28 audio/visual offset defaults changed to 0ms - intentional, documented**:
      `DEFAULT_VISUAL_OFFSET_MS` (was `-50f`) and `DEFAULT_AUDIO_OFFSET_MS` (was `-30f`) are
      now true zero, not a hand-guessed per-device pre-roll that was never actually
      calibrated against a given unit's real display/audio latency - see both constants'
      own kdoc in `MetronomeSettings.kt` for the reasoning, still fully adjustable via
      Settings. This exposed a real, previously-latent bug rather than being a simple
      value swap: `MetronomeEngine.startAudioScheduling`'s predictive lookahead loop, and
      `beatZeroCountInNanos`, both used to gate on the offset being *strictly negative*,
      conflating "the user wants perceptual pre-roll" with "the streaming engine
      structurally needs some lead time to place any click precisely" - true regardless of
      the offset's sign. Went unnoticed while the shipped default was negative (always
      satisfying the old check); would have silently disabled lead-scheduling for *every*
      beat, not just beat 0, the moment the default became exactly `0`. Fixed alongside the
      default change (`< 0f` → `<= 0f` in the scheduling gate, `>= 0f` → `> 0f` in
      `beatZeroCountInNanos`) - positive (deliberate-lag) offsets are unaffected. Manual
      check: confirm a fresh install (offset=0, default count-in cap) still lands the first
      beat and steady-state clicks with the same precision as the ~2ms/~13ms measured above
      - this should be identical, since 0 now takes the same code path negative offsets do.
- [ ] **Beat accent marking**: long-press the beats-per-bar number, confirm beat 1 shows as a
      fixed non-interactive "Bar" chip and every other beat cycles NONE → Accent → Strong Accent →
      Custom → NONE on tap, and that Settings → Click's now-five tabs (Bar/Beat/Accent/Strong
      Accent/Custom) each sound audibly distinct once marked and tuned.
- [ ] **MIDI Actions, real hardware**: per `docs/usb-midi-test-plan.md` section 7 - Note and CC
      messages per beat type, velocity-0 flooring, firing independent of Click/mute, and no
      interference with simultaneous outgoing clock. Never run against anything but a fake
      `MidiReceiver` in tests.
- [ ] **Phrase-management strip, on-device feel**: with a single phrase (default), confirm the only
      new chrome versus before this feature is one small icon at the end of the bar-queue row - tap
      it and confirm the full phrase-management strip (reset/remove/dots/add/mode) appears below
      without shifting the BPM/transport rows above it. Add a few phrases with different bar/tempo
      content, confirm tap-to-jump always lands on the target phrase's first bar (never "where you
      left off"), long-press removes a phrase, and removing back down to one phrase makes the whole
      strip disappear again - not just go inert the way the bar queue's own controls do with a
      single bar.
- [ ] **Phrase Once-mode cascade, on-device**: build two phrases, set the first phrase's own
      bar-queue mode to Once and the phrase-level mode to Loop, press play, and confirm playback
      actually hands off from the first phrase to the second the moment the first phrase's bars run
      out - this is real-timing engine behavior (unit-tested via `Thread.sleep`-based Robolectric
      tests, not yet watched/heard on a real device).
- [ ] **Hold-gated destructive controls**: with HOLD off, confirm the bar-queue and phrase-strip
      reset/remove icons are absent (dots/add/mode-cycle stay visible); press-and-hold or latch
      HOLD and confirm both icons appear at both levels, then disappear again once HOLD releases.
- [ ] **Bigger time signature, on-screen fit**: confirm the enlarged beats-per-bar/note-value
      numbers and steppers still fit comfortably next to the BPM display at both portrait and
      compact-landscape layouts, on the smallest screen available to test with.
- [ ] **Unit symbols toggle**: with the Settings → Layout "Unit symbols" switch on (default),
      confirm the small bpm/beats/beat-type/bar/phrase marks appear next to their respective
      controls (main screen and the beats-per-bar accent-marking dialog); toggle off and confirm
      every mark disappears with no layout shift.
- [ ] **Beat Overrides + Trigger, real hardware**: per `docs/usb-midi-test-plan.md` section 7 -
      step to a specific beat, assign it a Note/CC override distinct from its type's own default,
      confirm playback sends the override (not the type default) for that one beat and the type
      default for every other beat of the same type. Confirm the Trigger button fires whatever's
      configured for the engine's current beat position both while stopped and while playing.
- [ ] **Phrase dots as mini bar-stacks, on-screen**: with a phrase containing several bars of
      different beat counts, confirm each phrase dot renders as a small vertical stack of
      bar-segments (not one uniform block), each segment's width roughly tracking that bar's beat
      count relative to its own phrase's bars - and that the phrase strip's own layout doesn't
      shift as bars are added/removed within a phrase.
- [ ] **Radial phrase indicator, real Glyph hardware**: with more than one phrase queued, confirm
      a small dot per phrase appears around the physical Glyph Matrix's outer rim (and its
      on-screen preview mirror), the active phrase's dot reading brighter than the others, and
      that it's independently toggleable from the bar-queue background via Settings → Visualizer.
      Check legibility specifically at higher phrase counts (6-8+) where dots may start crowding -
      this is the practical ceiling the feature shipped acknowledging, not something engineered
      around for a first pass.
- [ ] **Phrase Actions, real hardware**: per `docs/usb-midi-test-plan.md` section 7 - step to a
      specific phrase, assign it a Note/CC action, and confirm it fires exactly once each time you
      jump to that phrase (tapping its dot, and via the automatic Once-mode cascade), including
      re-entering the already-active phrase directly.
