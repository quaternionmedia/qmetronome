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
to be set — see the setup paths below for where to find it.

> New to Android development? [`docs/onboarding.md`](docs/onboarding.md) walks
> through every step with annotated output and "if this goes wrong" notes.
> Experienced but just need the commands? [`docs/cookbook.md`](docs/cookbook.md)
> is the quick reference.

---

## Setup: with Android Studio

1. **Clone the repo.**
   ```sh
   git clone https://github.com/quaternionmedia/qmetronome.git
   cd qmetronome
   ```

2. **Open in Android Studio.**
   File → Open → select the `qmetronome` directory (the one containing
   `settings.gradle.kts`). Not the `app/` subdirectory.

3. **Let the Gradle sync finish.** Android Studio prompts to install missing
   SDK components if needed — accept all of them. On first sync it downloads
   Gradle 9.4.1 and resolves Maven dependencies (~300 MB total, cached after
   the first time).

4. **Run on a device or emulator.**
   - Any Android 13+ (API 33+) device or emulator works.
   - The app runs fine without Nothing hardware — you get the full UI and
     the on-screen Glyph Matrix preview. The "Activate as Glyph Toy" button
     will show a toast on non-Nothing devices instead of opening the toys
     manager.
   - To see the real Glyph Matrix output, deploy to a Nothing Phone (3) or
     (4a) Pro, then use the Glyph Toys manager to enable the toy.

5. **Run the tests.** In the terminal panel at the bottom:
   ```sh
   ./gradlew test        # macOS/Linux or Android Studio's built-in terminal
   gradlew.bat test      # Windows PowerShell / CMD
   ```
   All tests run on the JVM via Robolectric — no device or emulator needed.

   > **Windows:** Android Studio's built-in terminal (View → Tool Windows → Terminal)
   > already has `JAVA_HOME` configured — both `./gradlew` and `gradlew.bat` work
   > there. In an external PowerShell window you need `JAVA_HOME` set first; see
   > the CLI setup below or [`docs/onboarding.md`](docs/onboarding.md).

---

## Setup: without Android Studio (command line)

Any editor works for code — VS Code with the Kotlin extension, IntelliJ IDEA
Community, Neovim, whatever. You need the Android SDK separately.

### 1 — JDK 21

**Windows with Android Studio already installed:** use the bundled JDK — no
separate download needed:

```powershell
# PowerShell — add to your $PROFILE for persistence
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH      = "$env:JAVA_HOME\bin;$env:PATH"
```

**Everyone else:** download [Temurin 21](https://adoptium.net/) (the CI
distribution) and set `JAVA_HOME`:

```sh
# macOS / Linux (add to ~/.zshrc or ~/.bashrc)
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"

# Windows without Android Studio (PowerShell)
$env:JAVA_HOME = "C:\path\to\temurin-21"
$env:PATH      = "$env:JAVA_HOME\bin;$env:PATH"
```

Verify: `java -version` should print `openjdk version "21…"`.

### 2 — Install the Android SDK

**Option A: command-line tools only** (no Android Studio)

Download [Android command-line tools](https://developer.android.com/studio#command-line-tools-only)
and unzip to a location of your choice (e.g. `~/android-sdk` on Linux/macOS,
`C:\android-sdk` on Windows). Then:

```sh
# Set ANDROID_HOME (add to shell profile)
export ANDROID_HOME=~/android-sdk      # Linux/macOS
# or
$env:ANDROID_HOME = "C:\android-sdk"  # Windows PowerShell

# Install the required SDK components
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager \
  "platform-tools" \
  "platforms;android-35" \
  "build-tools;35.0.0"
```

**Option B: reuse an existing Android Studio SDK**

If Android Studio is installed elsewhere on the machine (but you want to use
a different editor), point to its SDK instead:

```sh
# macOS
export ANDROID_HOME=~/Library/Android/sdk
# Linux
export ANDROID_HOME=~/Android/Sdk
```

```powershell
# Windows PowerShell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
```

### 3 — Tell Gradle where the SDK is

**Android Studio users:** Android Studio already created `local.properties`
with `sdk.dir=` pointing to your SDK. Skip this step — you're done.

**CLI-only setups:** create `local.properties` in the project root:

```properties
# macOS/Linux
sdk.dir=/home/yourname/android-sdk

# Windows — use forward slashes or escape backslashes
sdk.dir=C\:/android-sdk
```

Alternatively, if `ANDROID_HOME` is set the Gradle Android plugin picks it up
automatically and `local.properties` isn't required.

`local.properties` is gitignored — don't commit it.

### 4 — Clone and build

```sh
git clone https://github.com/quaternionmedia/qmetronome.git
cd qmetronome

# First build — downloads Gradle 9.4.1 and all Maven dependencies (~300 MB, cached after)
./gradlew assembleDebug          # macOS/Linux
gradlew.bat assembleDebug        # Windows PowerShell/CMD

# Run all unit tests (JVM only, no device needed)
./gradlew test                   # macOS/Linux
gradlew.bat test                 # Windows
```

### 5 — Install on a device

Enable USB debugging on your Android 13+ device, then:

```sh
# macOS/Linux (adb is in $ANDROID_HOME/platform-tools/)
adb install app/build/outputs/apk/debug/app-debug.apk

# Windows — if adb isn't in PATH, use the full path:
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install app\build\outputs\apk\debug\app-debug.apk
```

To add `adb` to your PATH permanently on Windows, add
`%LOCALAPPDATA%\Android\Sdk\platform-tools` to System Properties → Environment
Variables → Path.

---

## Project structure

```
qmetronome/
├── app/src/main/java/.../
│   ├── engine/          # MetronomeEngine singleton, BeatPhase, ClockSource, settings
│   ├── visualizers/     # GlyphVisualizer implementations + GlyphCanvas
│   ├── midi/            # MIDI clock in/out, USB connector, virtual device service
│   ├── glyph/           # Glyph Matrix SDK integration (isolated here — see ADR)
│   ├── ui/              # Compose UI: MainScreen, SettingsSheet, MatrixPreview, etc.
│   └── widget/          # Home screen widget (Jetpack Glance)
├── app/libs/            # glyph-matrix-sdk-2.0.aar — committed, no separate download
├── docs/                # Feature investigations, test plans, release checklists
├── adr/                 # Architecture Decision Records
├── scripts/             # Helper scripts (e.g. generate-release-key.bat)
└── .github/workflows/   # CI (ci.yml) and release pipeline (release.yml)
```

The root [`README.md`](README.md) is the architecture reference — read the
`engine/`, `midi/`, and `ui/` bullets there first. [`docs/README.md`](docs/README.md)
indexes the feature-specific investigations and test plans. [`adr/README.md`](adr/README.md)
explains the decision-record process.

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

The Glyph Matrix SDK (`glyph/`) and the live `MidiManager` device-open path
are not unit-testable (closed AAR with real Binder calls; Robolectric's MIDI
shadows don't cover device I/O). Those paths have manual test plans in
[`docs/usb-midi-test-plan.md`](docs/usb-midi-test-plan.md) instead.

### Glyph SDK isolation

The import boundary check in CI enforces that `com.nothing.ketchum.*` only
appears under `glyph/`. Keep it that way — see
[`adr/DRAFT-glyph-matrix-sdk-dependency.md`](adr/DRAFT-glyph-matrix-sdk-dependency.md)
for why.

### Signing

Debug builds don't need a keystore. Release signing (for the GitHub release
workflow) requires secrets configured in the repo — see
[`.github/workflows/release.yml`](.github/workflows/release.yml) for the
required secret names. As a contributor you don't need to set these up;
CI handles it on tagged releases.

---

## Decision records

Non-trivial architectural decisions go in `adr/` as Proposed ADRs. Read
[`adr/README.md`](adr/README.md) for the full process; the short version:

- **Before ratification**: rewrite in place (no "previously" or "updated to"
  language in the prose — git is the history).
- **After ratification**: append-only (dated entries in `## Amendments` only).
- **Never assign numbers** — that's a human step at ratification, not
  something a contributor or AI assistant does.

If your change adds a component with a non-obvious architectural justification
(a new singleton, a new persistence layer, a new permission), a brief ADR
draft is more useful than a long PR description that will be forgotten.

This process is itself adopted from Quaternion Media's org-wide constitution,
vendored as a submodule at [`governance/qm`](governance/qm) and pinned in
[`adr/README.md`](adr/README.md). Which org records actually apply to a
sideloaded mobile app (as opposed to the org's server/infra defaults) is
decided in
[`adr/DRAFT-constitution-adoption-scope.md`](adr/DRAFT-constitution-adoption-scope.md) —
worth a read before assuming a constitution rule does or doesn't apply to a
change you're making.

---

## Cutting a release

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
