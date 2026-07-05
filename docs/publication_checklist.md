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

## Documentation

- [x] **Tempo controls & MIDI clock-out**: README's `ui/` and `midi/`
      architecture bullets cover the press-and-hold step buttons,
      drag-to-scrub, and bidirectional MIDI clock (virtual + USB, both
      ways). `docs/external-midi-clock.md` and the MIDI ADR cover the
      design rationale, including the deliberate choice to allow rather
      than block following-and-sending-to-the-same-USB-device.
- [x] **Widget implementation**: `docs/home-screen-widget.md` documents the
      `collectAsState` reactivity pattern and the round-by-round path to it.
- [x] **User guide**: README now has a "Using the widget" section (placement,
      toggle, the background-tap app shortcut, why it's not a live preview).
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
- [ ] **Double-tap and long-press on preview**: double-tapping the preview
      should toggle play/stop; long-pressing should open Settings. Confirm
      neither gesture conflicts with the horizontal swipe for visualizer
      cycling (a brief swipe should not accidentally trigger a long-press).
- [ ] **BPM direct entry**: long-pressing the BPM number should open a
      numeric entry dialog. Confirm the keyboard auto-focuses, the current
      BPM is pre-selected, values outside 1–400 are rejected, and the
      running engine immediately reflects the new tempo on confirm.
- [ ] **HOLD staging**: hold the HOLD button in the transport row while
      tapping ±1 or dragging the BPM. The display should show the staged
      value with "• staged" below it. Release HOLD and confirm the engine
      snaps to the staged tempo in one step rather than having changed
      continuously during the hold.
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
- [ ] **Latch mode**: distinct from the existing HOLD-staging item above —
      long-press or double-tap the HOLD button (not the preview) to promote
      staging into a sticky latch, confirm BPM/beats-per-bar edits keep
      staging without holding the button down, and confirm a subsequent plain
      tap on HOLD flushes the staged values and exits latch cleanly. Also
      confirm `stop()` force-clears an active latch rather than leaving it
      stuck engaged across a stop/start cycle.
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
      natural imperfection through rather than ironing it flat.
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
