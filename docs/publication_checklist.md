# Publication Checklist

Tracks what's needed before qMetronome ships. Updated in place as items
complete — see [`docs/README.md`](README.md) for how this relates to the
feature docs and `adr/` decision records it references. This doc is
repo/code readiness; for the separate question of actually submitting to
Google Play and Nothing's distribution channel, see
[`app-store-checklist.md`](app-store-checklist.md).

## Technical Polish

- [x] **Application identity**: `applicationId`/`namespace` were still
      Android Studio's `com.example.qmetronome` template default. Renamed to
      `media.quaternion.qmetronome` (package directories, all source files,
      `app/build.gradle.kts`, the one stale path in
      `adr/DRAFT-glyph-matrix-sdk-dependency.md`) before any beta install
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
