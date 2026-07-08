# Contributing to qMetronome

This covers everything a new contributor needs to go from zero to a running
build, understand what goes where, and know the norms before sending a PR.

**You don't need a Nothing phone to work on most of this.** The on-screen
`MatrixPreview` is a pixel-accurate stand-in for the Glyph Matrix, so
visualizers, MIDI, the widget, tempo controls, and UI changes can all be
developed and tested on a stock Android device or emulator. Only "Activate as
Glyph Toy" and the real LED output require Nothing Phone (3) or (4a) Pro
hardware.

---

## Prerequisites

| Tool | Required version | Notes |
|---|---|---|
| JDK | 21 | Android Studio bundles one; Temurin 21 for CLI-only setups |
| Android SDK | platform 35, build-tools 35.x | Android Studio manages this automatically |
| Git | any modern version | |
| Android Studio *(optional)* | Ladybug 2024.2+ | For Compose previews and the layout editor; not required to build |

The Gradle wrapper (`gradlew` / `gradlew.bat`) downloads Gradle 9.4.1
automatically on first run — you don't install Gradle separately.

The Glyph Matrix SDK (`app/libs/glyph-matrix-sdk-2.0.aar`) is committed to
the repo — no separate SDK download or account is required.

**Windows note:** on Windows, use `gradlew.bat` instead of `./gradlew`
everywhere in this doc. `./gradlew` requires Git Bash; `gradlew.bat` works
in PowerShell and CMD without any extra tools. Gradle also requires `JAVA_HOME`
to be set — see [`docs/onboarding.md`](docs/onboarding.md) for where to find it.

---

## Setup

[`docs/onboarding.md`](docs/onboarding.md) is the source of truth for
getting from zero to a running build - JDK/SDK install (with or without
Android Studio), `local.properties`, cloning, building, running the tests,
installing on a device, and what to do if any of those steps fail. Follow it
first if you're setting up this project for the first time; this doc doesn't
repeat those steps.

Once you're set up, the quick-reference version of the same commands (build,
test, install, plus `adb`/logcat one-liners) lives in
[`docs/cookbook.md`](docs/cookbook.md).

---

## Project structure

```
qmetronome/
├── app/src/main/java/.../
│   ├── engine/          # MetronomeEngine singleton, BeatPhase, ClockSource, settings
│   ├── visualizers/     # GlyphVisualizer implementations + GlyphCanvas
│   ├── midi/            # MIDI clock in/out, USB connector, virtual device service
│   ├── glyph/           # Glyph Matrix SDK integration (isolated here — see ADR)
│   ├── ui/              # Compose UI: MainScreen, SettingsSheet, HelpScreen, MatrixPreview, etc.
│   ├── tutorial/        # TutorialTopics — shared source of truth for user-guide.md + HelpScreen
│   └── widget/          # Home screen widget (Jetpack Glance)
├── app/libs/            # glyph-matrix-sdk-2.0.aar — committed, no separate download
├── docs/                # Feature investigations, test plans, release checklists, user-guide.md
├── governance/qm/       # org constitution submodule; adr/ here holds this project's own ADRs
├── scripts/             # Helper scripts (e.g. generate-release-key.bat)
└── .github/workflows/   # CI (ci.yml) and release pipeline (release.yml)
```

The root [`README.md`](README.md) is the architecture reference — its "Using qMetronome"
section is the narrative walkthrough, and its [Glossary](README.md#glossary) covers every
`engine/`, `midi/`, and `ui/` class and singleton by name; skim the Glossary before diving into
any of those packages. [`docs/README.md`](docs/README.md) indexes the feature-specific
investigations and test plans.
[`governance/qm/adr/README.md`](governance/qm/adr/README.md) explains the
decision-record process — this project's own ADRs live inside that
submodule, on this project's dedicated `project/qmetronome` branch, not as a
top-level directory in this repo.

---

## Adding a visualizer

Implement `GlyphVisualizer`, add an instance to `VisualizerRegistry.all`, and
you're done — no service registration or threading needed. The existing
`VisualizerRenderTest` will automatically cover your new entry. See the
"Adding a new visualizer" section in [`README.md`](README.md) for the two
behavioural contracts every visualizer must satisfy (beat must read without
audio; bar 1 must read distinctly from other beats).

---

## Development workflow

### Before every PR

```sh
./gradlew test assembleDebug       # macOS/Linux
gradlew.bat test assembleDebug     # Windows
```

CI runs the same tasks (plus the Glyph SDK import-boundary check) on every
push and PR. A red CI is a blocker before merging.

### Test coverage

Tests live alongside the production code in `app/src/test/`. The suite uses
JUnit 4 + Robolectric for anything that touches Android framework classes
(`@RunWith(RobolectricTestRunner::class)`, `@Config(sdk = [33])`). Pure
Kotlin logic uses plain JUnit. New behaviour should come with tests; new
engine or MIDI behaviour needs them.

**Compose UI gestures are tests-as-source-of-truth, not just tests.** Every
major user-facing gesture (drag-to-scrub, long-press-to-type, HOLD's
momentary/latch staging, the bar queue, Settings' chip rows, the Glyph Matrix
preview's swipe/double-tap/long-press, layout toggles) has a
`*ScreenshotTest.kt` under `app/src/test/java/.../ui/` that drives the real
production composable through an actual simulated gesture
(`performTouchInput`), asserts genuine behavior, *and* captures a
[Roborazzi](https://github.com/takahirom/roborazzi) screenshot in the same
test - almost always by rendering the actual, full `MainScreen` (or
`SettingsSheet`/`HelpScreen`) at `FULLSCREEN_QUALIFIERS`' resolution
(1080x2400, a realistic modern-phone size) rather than the one composable
under test in isolation, so every screenshot looks like a genuine phone
screen. The one exception is `BpmUnitEntryDialogScreenshotTest` - see its own
kdoc for why a real Android dialog is captured differently. Motion-heavy
topics (drags, swipes, HOLD's timing) additionally have a
[`recordRoboVideo`](https://github.com/takahirom/roborazzi)-captured GIF
alongside the static screenshot - see `*VideoTest.kt` files for the pattern.

Adding a new gesture/topic means:

1. Add a `TutorialTopic` to `tutorial/TutorialTopic.kt`'s `TutorialTopics.all`
   (id, title, end-user-facing description, category).
2. Write a `*ScreenshotTest` that drives the gesture and calls
   `composeTestRule.onScreenshotRoot().captureRoboImage(screenshotPath(topic.id))`
   (see `ComposeTestSupport.kt` for the shared theming/capture helpers - read
   its kdoc before writing a new test, several non-obvious gotchas are
   documented there, e.g. why gestures use hand-rolled `performTouchInput`
   sequences over `swipeLeft()`/`swipeRight()`, why some clicks inside
   `SettingsSheet` need `.invokeOnClick()` instead of `.performClick()`, and
   why drag distances are 3x what they'd look like at 1x density). Add a
   `*VideoTest` alongside it too if the gesture is genuinely motion-based.
3. Run `./gradlew generateUserGuide` (implies `testDebugUnitTest`, which
   captures the screenshot/video) to regenerate
   [`docs/user-guide.md`](docs/user-guide.md) - this also *fails* if any
   topic's screenshot is missing, so a new topic without a matching test
   can't silently ship a broken doc image link.
4. **Wire the new screenshot into git** - `docs/images/generated/` is
   gitignored by default (see `.gitignore`'s own comment) precisely so a
   topic's file only ends up tracked as a deliberate step, not automatically
   the moment a test happens to produce one locally. Add an explicit `!`
   negation line for the new file(s) (`!docs/images/generated/screenshots/
   your-topic-id.png`, and the `videos/` equivalent if it has one) right
   next to the existing ones, then `git add` it normally.
5. Add a live-composable branch for the topic's category in
   `ui/HelpScreen.kt` if it isn't already covered by an existing one (several
   categories share one live control - see that file's `when` block).

The Glyph Matrix SDK (`glyph/`) and the live `MidiManager` device-open path
are not unit-testable (closed AAR with real Binder calls; Robolectric's MIDI
shadows don't cover device I/O). Those paths have manual test plans in
[`docs/usb-midi-test-plan.md`](docs/usb-midi-test-plan.md) instead.

### Glyph SDK isolation

The import boundary check in CI enforces that `com.nothing.ketchum.*` only
appears under `glyph/`. Keep it that way — see
[`governance/qm/adr/DRAFT-glyph-matrix-sdk-dependency.md`](governance/qm/adr/DRAFT-glyph-matrix-sdk-dependency.md)
for why.

### Signing

Debug builds don't need a keystore. Release signing (for the GitHub release
workflow) requires secrets configured in the repo — see
[`.github/workflows/release.yml`](.github/workflows/release.yml) for the
required secret names. As a contributor you don't need to set these up;
CI handles it on tagged releases.

---

## Decision records

Non-trivial architectural decisions go in `governance/qm/adr/` as Proposed
ADRs — this project's own dedicated branch of the org's constitution repo,
checked out via the `governance/qm` submodule. Read
[`governance/qm/adr/README.md`](governance/qm/adr/README.md) for the full
process; the short version:

- **Before ratification**: rewrite in place (no "previously" or "updated to"
  language in the prose — git is the history).
- **After ratification**: append-only (dated entries in `## Amendments` only).
- **Never assign numbers** — that's a human step at ratification, not
  something a contributor or AI assistant does.

If your change adds a component with a non-obvious architectural justification
(a new singleton, a new persistence layer, a new permission), a brief ADR
draft is more useful than a long PR description that will be forgotten.

This process is itself adopted from Quaternion Media's org-wide constitution,
vendored as a submodule at [`governance/qm`](governance/qm). Which org
records actually apply to a sideloaded mobile app (as opposed to the org's
server/infra defaults) is decided in
[`governance/qm/adr/DRAFT-constitution-adoption-scope.md`](governance/qm/adr/DRAFT-constitution-adoption-scope.md) —
worth a read before assuming a constitution rule does or doesn't apply to a
change you're making.

---

## Cutting a release

[`CHANGELOG.md`](CHANGELOG.md) is generated from this repo's own annotated
tag history (`scripts/generate-changelog.sh`), and both release workflows
below regenerate it and attach it as a release asset automatically - but
that's a CI-local copy, not committed back to the repo. If you want the
tracked `CHANGELOG.md` itself current, run the script and commit it
(before or after tagging, either works, since it reads *all* existing tags):

```sh
bash scripts/generate-changelog.sh
git add CHANGELOG.md && git commit -m "Regenerate CHANGELOG.md"
```

There are two tag series, each with its own workflow:

### Alpha / developer builds (`v0.x.x`) — no secrets required

Debug builds that anyone can sideload. No signing keys, no Google Play account.

```sh
git tag v0.0.1 -m "Alpha v0.0.1"
git push --tags
```

The `alpha-release.yml` workflow fires, runs tests, builds a debug APK, and
creates a pre-release GitHub Release with the APK attached. Developers install
via:

```sh
adb install app-debug.apk
```

Or transfer the APK to the device and open it with "Install unknown apps"
enabled in Android developer settings.

### Production releases (`v1.x.x`) — signing secrets required

Signed APK + AAB for sideloading and Play Store submission. Requires four
secrets configured by the repo owner in Settings → Secrets and variables →
Actions (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`).

```sh
git tag v1.0.0 -m "Release v1.0.0"
git push --tags
```

The `release.yml` workflow fires, runs tests, builds a signed APK + AAB, and
creates a GitHub Release with both attached. As a contributor you don't need
the signing secrets; CI handles it on tagged releases.

---

## Getting help

Open an issue on GitHub. If you're unsure whether something is a bug or
intentional behaviour, the [`docs/`](docs/) directory often has the answer —
especially the feature investigation docs, which log what was tried and why.
