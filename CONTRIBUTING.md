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
| JDK | 21 (Temurin recommended) | CI uses Temurin 21; other JDK 21 distributions work |
| Android SDK | platform 35, build-tools 35.x | See setup paths below |
| Git | any modern version | |
| Android Studio *(optional)* | Ladybug 2024.2+ | For Compose previews and the layout editor; not required to build |

The Gradle wrapper (`gradlew` / `gradlew.bat`) downloads Gradle 9.4.1
automatically on first run — you don't install Gradle separately.

The Glyph Matrix SDK (`app/libs/glyph-matrix-sdk-2.0.aar`) is committed to
the repo — no separate SDK download or account is required.

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
   ./gradlew test
   ```
   All tests run on the JVM via Robolectric — no device or emulator needed.

---

## Setup: without Android Studio (command line)

Any editor works for code — VS Code with the Kotlin extension, IntelliJ IDEA
Community, Neovim, whatever. You need the Android SDK separately.

### 1 — Install JDK 21

[Temurin 21](https://adoptium.net/) is the CI distribution. Set `JAVA_HOME`
to the JDK root:

```sh
# Linux / macOS (add to ~/.bashrc or ~/.zshrc)
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"

# Windows (PowerShell — set permanently via System Properties → Environment Variables)
$env:JAVA_HOME = "C:\path\to\jdk-21"
```

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
# Linux/macOS — path is usually:
export ANDROID_HOME=~/Library/Android/sdk            # macOS
export ANDROID_HOME=~/Android/Sdk                    # Linux

# Windows — path is usually:
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
```

### 3 — Tell Gradle where the SDK is

Create `local.properties` in the project root (alongside `settings.gradle.kts`):

```properties
# Linux/macOS
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

# First build — downloads Gradle 9.4.1 and all Maven dependencies
./gradlew assembleDebug          # Linux/macOS
gradlew.bat assembleDebug        # Windows

# Run all unit tests (JVM only, no device needed)
./gradlew test
```

### 5 — Install on a device

```sh
# Enable USB debugging on your Android 13+ device, then:
adb install app/build/outputs/apk/debug/app-debug.apk
```

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
./gradlew test assembleDebug   # tests + compile check
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
