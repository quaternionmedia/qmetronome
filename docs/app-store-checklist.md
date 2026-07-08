# App store publication checklist: Google Play + Nothing

What's needed to actually publish qMetronome, as distinct from
[`publication_checklist.md`](publication_checklist.md) (repo/code
readiness - signing keys, listing assets, and platform-specific review
requirements live here instead). Structured the same way: checked items are
verified true today, unchecked items are real open work, and anything I
don't have confident first-hand knowledge of is flagged as such rather than
guessed at.

## Is this a Nothing-only app, or a general Android app?

This matters because it decides almost everything else below, so it's
worth establishing first rather than assuming.

- [x] **Verified by reading the code, not just asserting it**: `MainActivity`,
      `engine/`, `ui/`, `midi/`, and `visualizers/` import nothing from the
      closed `com.nothing.ketchum.*` SDK - confined entirely to `glyph/` (see
      `governance/qm/adr/DRAFT-glyph-matrix-sdk-dependency.md`, enforced by CI). The app's
      core (tempo engine, on-screen matrix preview, MIDI clock in/out,
      audible click, the home screen widget) works on **any Android 13+
      device**, Nothing or not.
- [x] **Verified graceful degradation on non-Nothing hardware**: the Glyph
      Toy service only registers if `Common.is23112()`/`is25111p()` match
      (`GlyphMatrixToyService.registerDevice()`), logging a warning and
      doing nothing further otherwise. The "Activate as Glyph Toy" button
      (`MainActivity.openGlyphToysManager()`) deep-links to a Nothing-only
      system activity and catches `ActivityNotFoundException` with a toast
      if it doesn't exist. Neither path crashes or blocks the rest of the
      app on non-Nothing hardware.
- [x] **No manifest restriction**: no `<uses-feature android:required="true">`
      ties the app to Glyph hardware; `android.software.midi` is the only
      declared feature and it's `required="false"`.
- **Recommendation**: list broadly on Google Play, not restricted to
  Nothing devices. Frame the Glyph Matrix integration as a bonus for Phone
  (3)/(4a) Pro owners in the store description, not the headline - the app
  is a fully-functional metronome/MIDI-clock tool without it.

## Google Play

### Store listing (blocking - Play Console won't let you submit without these)

- [ ] **Privacy policy URL** - mandatory for every app in Play Console
      regardless of whether it collects data. Drafted [`PRIVACY.md`](../PRIVACY.md)
      (no data collected, no analytics, no network calls) - paste its GitHub
      URL into Play Console once pushed:
      `https://github.com/quaternionmedia/qmetronome/blob/main/PRIVACY.md`
- [ ] **App icon, 512×512 PNG** - export from the existing adaptive icon
      source (`drawable/ic_launcher_foreground.xml` +
      `drawable/ic_launcher_background.xml`); this is a separate upload from
      the in-app adaptive icon resource.
- [ ] **Feature graphic, 1024×500** - needs new artwork; nothing in the repo
      is sized for this.
- [ ] **Phone screenshots** - minimum 2, recommend 4-8. None exist yet
      (expected - these need a real device/emulator, not something to
      generate from source). Suggest: main screen mid-beat with a visualizer
      active, the full-screen settings overlay (visualizer picker + MIDI
      clock section), and the home screen widget placed.
- [ ] **Short description** (≤80 chars) - "Metronome & tempo visualizer for musicians. Extra Glyph Matrix flair on Nothing."
- [ ] **Full description** (≤4000 chars) -
      "qMetronome is a precision metronome and tempo visualizer for performing and
      practicing musicians, built for any Android 13+ phone.

      Key Features:
      - Multiple Input Methods: Tap tempo, step buttons, or drag-to-scrub.
      - Bar Queue: Line up a sequence of differently-metered, differently-paced bars for a
        whole set ahead of time.
      - MIDI Clock Sync: Send/receive MIDI clock over USB or inter-app.
      - Precision Timing: Drift-corrected engine for rock-solid performance.
      - Home Screen Widget: Toggle play/stop directly from your launcher.
      - Minimalist Design: Pure monochrome aesthetic.

      On a Nothing Phone (3) or Phone (4a) Pro, qMetronome also drives the physical Glyph
      Matrix with custom beat-synced animations - a bonus display layered on top of
      everything above, not a requirement to use the app.

      qMetronome is open source and collects zero user data."
- [ ] **App category** - "Music & Audio" fits better than "Tools" given the
      metronome/MIDI framing.
- [ ] **Content rating questionnaire** - nothing in the app suggests
      anything other than "Everyone"; still has to be filled out in console.
- [ ] **Data safety form** - draft answer is "No data collected": no network
      requests in app code, no analytics/ads SDKs, `SharedPreferences` only
      and never transmitted (see `PRIVACY.md` for the full reasoning). Still
      has to be filled out as a form in console, not just asserted in a doc.
      The persistent-playback feature's new permissions (`POST_NOTIFICATIONS`,
      the battery-optimization exemption, the foreground service itself)
      don't change this answer - none of them collect, transmit, or share
      data; see `PRIVACY.md`'s Permissions section for what each is actually
      for.
- [ ] **Ads/target-audience declarations** - no ads, not designed for
      children.

### Build & signing (blocking)

- [x] **Release signing configuration prepared**: The build now loads signing
      credentials from a local (non-committed) `keystore.properties` file.
      Use `scripts/generate-release-key.bat` to create your upload key.
- [ ] **Android App Bundle, not APK** - Play requires `.aab` for new apps.
      `./gradlew bundleRelease` once signing is configured; no other config
      change needed.
- [x] `versionCode`/`versionName` (1 / "1.0") are sane defaults for a first
      release.
- [x] **R8/optimization is enabled** for release builds (`isMinifyEnabled = true`, `isShrinkResources = true`).

### Permissions & API level

- [x] Reviewed the actual merged manifest (not just the source one) via a
      built debug APK's intermediate output - the full permission set is
      `com.nothing.ketchum.permission.ENABLE`, `WAKE_LOCK`,
      `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`
      (the last four transitive via WorkManager, a Glance dependency), plus
      an auto-generated unexported-receiver signature permission - **as of
      the persistent-playback feature, this list is stale**: the app now
      *directly* declares `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`,
      `POST_NOTIFICATIONS` (Play-dangerous-protection-level, opt-in via
      Settings → Playback, never required), and
      `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Needs a fresh merged-manifest
      pass before Play submission.
- [x] **Verified `targetSdk` 35 meets Play's current requirements**.
- [ ] **Foreground service declaration** - the app now genuinely calls
      `startForeground()` (`engine/PersistentPlaybackService`, only while
      Settings → Playback's "Persistent playback" is on and the metronome is
      playing), declared `foregroundServiceType="specialUse"` with a
      `PROPERTY_SPECIAL_USE_FOREGROUND_SERVICE_TYPE` justification string.
      Play Console review of that justification is a real gate for this one,
      not a maybe - check the pre-launch report rather than assuming it's fine.

### The closed Glyph Matrix SDK and redistribution

- [ ] **Not verified in this pass**: whether the GlyphMatrix-Developer-Kit's
      own terms explicitly permit redistributing `glyph-matrix-sdk-2.0.aar`
      inside a published third-party Play Store APK. The AAR itself carries
      no bundled `LICENSE`/`NOTICE` file (confirmed by unzipping it - just
      `R.txt`, `AndroidManifest.xml`, `classes.jar`, resources, and AAR
      metadata). The Developer Kit publishing the SDK at all strongly implies
      third-party redistribution is the intended use, but "strongly implies"
      isn't the same as "verified the actual terms," and I don't have that
      text memorized with confidence - read the Developer Kit's own
      README/license/terms directly before relying on this.
- [x] **`NothingKey`**: Ships with the SDK's development placeholder (`"test"`);
      no production key is needed or planned.

## Nothing's distribution channel

I want to be explicit here about the boundary between what's verified from
this codebase and what would just be me guessing at a process I don't have
confident knowledge of.

**What's confirmed and already done:**

- Nothing phones run Android with Google Play as the primary app store -
  nothing in this project or the Developer Kit suggests a separate "Nothing
  App Store" for general app distribution.
- "Glyph Toy" isn't a separate marketplace submission. It's the
  `com.nothing.glyph.TOY` intent-filter (already declared in
  `AndroidManifest.xml`) that makes an *installed* app appear in the
  on-device Glyph Toys picker - confirmed by `MainActivity`'s own deep link
  to `com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity`, a
  system activity, not a store. Once qMetronome is on Play and a user
  installs it on a supported Nothing phone, it already qualifies for that
  picker with no further submission step - this part is done in code today.
- The Glyph Toy preview image (`drawable/toy_preview.xml`) has been replaced
  with generated pixel art matching the app's own static-logo pose (see
  README's Setup notes) - no longer the bare placeholder it was. It has
  **not** been checked against the Developer Kit's own spec images
  (`23112_spec.svg` / `25111_spec.svg`) for exact dimension/format
  conventions Nothing expects for a submitted toy, which is worth doing
  before this is genuinely ready, since it's what users see in the picker.

**What I don't have verified knowledge of, and would be guessing at if I
wrote specific steps:**

- Whether Nothing runs any kind of curated showcase, featured-toys list, or
  community review process beyond the technical registration above. I
  genuinely don't know one way or the other with confidence - if it exists,
  it would be an optional visibility step, not a publication requirement,
  since the technical path to "this app works as a Glyph Toy" is already
  complete.

**Bottom line for Nothing**: there's no separate store submission to do, and no
production `NothingKey` is needed — the app ships with the SDK's development
placeholder (`"test"`) intentionally.

## Suggested order of operations

1. Generate/secure a release signing setup (human-managed secret - start
   this early, it's the slowest-moving piece).
2. Finish the listing assets (icon export, feature graphic, screenshots,
   descriptions).
3. Fill out Play Console's Data safety / content rating / ads forms using
   this doc's drafted answers.
4. Push `PRIVACY.md`, build a signed release AAB, submit to a closed/internal
   testing track first rather than production - this is explicitly a beta.
