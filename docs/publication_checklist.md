# Publication Checklist

Tracks what's needed before qMetronome ships. Updated in place as items
complete — see [`docs/README.md`](README.md) for how this relates to the
feature docs and `adr/` decision records it references.

## Technical Polish

- [x] **ProGuard/R8 rules**: Release `optimization` is currently disabled
      (`app/build.gradle.kts`), so nothing is obfuscated today — but added
      forward-looking keep rules anyway (`app/src/main/keepRules/rules.keep`)
      for the Glyph Matrix SDK and Glance's reflection-dispatched
      `ActionCallback`/`GlanceAppWidget`/`GlanceAppWidgetReceiver` classes, so
      enabling optimization later doesn't silently break either.
- [ ] **Final log cleanup**: `Log.d`/`Log.e` calls in `MetronomeWidget` and
      `QMetronomeApp` are deliberately still in place. They're what found
      both real widget bugs across four rounds of on-device testing (see
      `docs/home-screen-widget.md`) — stripping them before the round-4 fix
      has had more than one round of confirmation would remove the only tool
      that's actually worked so far. Revisit once that's settled.
- [x] **Resource optimization**: Removed the unused legacy raster launcher
      icons (`mipmap-*/ic_launcher*.webp`) — `minSdk` 33 always resolves the
      adaptive vector icon (`mipmap-anydpi-v26/`), so they were dead weight
      left over from the default Android Studio template, never updated past
      the stock green-robot art.

## Documentation

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
- [ ] **Nothing key**: Still the kit's demo placeholder (`"test"`) — blocked
      on an actual production key from Nothing, which isn't something I have
      access to. Flagged in the README's Setup notes; needs a human with that
      credential before a real release build.

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
