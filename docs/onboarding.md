# Onboarding: your first build

This is the step-by-step path for someone new to Android development (or just
new to this stack). If you already know Gradle and ADB, skip to
[`cookbook.md`](cookbook.md) instead.

**What you're building:** qMetronome is an Android app — a tempo visualizer
that drives a Glyph LED matrix on Nothing phones. You don't need a Nothing
phone to work on it. The app runs on any Android 13+ device or emulator and
shows a pixel-accurate preview of the Glyph Matrix on screen.

---

## What you need before you start

Three things. Everything else is handled automatically.

| What | Why |
|---|---|
| Git | clone the repo |
| JDK 21 | run the build tool (Gradle) |
| Android SDK | compile Android code |

Android Studio bundles both the JDK and the SDK and configures them for you.
Without it you set them up once and then they're there.

---

## Path A: with Android Studio (recommended for new contributors)

### 1. Install Android Studio

Download [Android Studio](https://developer.android.com/studio) (Ladybug
2024.2 or newer). Install it. Open it once so it finishes its first-run SDK
setup — this downloads the Android SDK and sets everything up.

### 2. Clone the repo

In a terminal (any terminal — PowerShell, Terminal.app, your OS shell):

```sh
git clone https://github.com/quaternionmedia/qmetronome.git
```

### 3. Open the project

In Android Studio: **File → Open** → select the `qmetronome` folder (the one
that contains `settings.gradle.kts`). Don't open the `app/` subfolder.

Android Studio will start a **Gradle sync** automatically. You'll see a
progress bar at the bottom. This downloads Gradle 9.4.1 and all library
dependencies — about 300 MB on the first run, all cached locally afterwards.
If it prompts to install missing SDK components, accept them.

**Expected output when sync finishes:** "Gradle sync finished" in the status
bar, no red underlines in `build.gradle.kts`.

**If sync fails:** the most common cause is a missing SDK component. Check
the "Build" tab at the bottom for the error message. Accepting any Android
Studio prompts about missing components usually fixes it.

### 4. Run the tests

In Android Studio's built-in terminal (View → Tool Windows → Terminal):

```sh
./gradlew test        # macOS/Linux
gradlew.bat test      # Windows
```

**Expected output:**

```
BUILD SUCCESSFUL in Xs
28 actionable tasks: 28 up-to-date
```

All tests run on your laptop — no phone or emulator needed. If you see
`BUILD SUCCESSFUL`, the project is healthy.

### 5. Run the app

Connect an Android 13+ phone via USB (or start an emulator in Android
Studio's Device Manager). Press the green ▶ button or **Run → Run 'app'**.

- On any phone: you get the full UI with an on-screen Glyph Matrix preview.
- On a Nothing Phone (3) or (4a) Pro: tap "Activate as Glyph Toy" to drive
  the real LED matrix.

That's it. You're set up.

---

## Path B: without Android Studio (any editor)

Use this if you prefer VS Code, IntelliJ IDEA Community, Neovim, or anything
else. You need to set up the JDK and SDK yourself, once.

### 1. Get JDK 21

**Windows — if Android Studio is installed on your machine:**

Android Studio ships with a bundled JDK. You can point Gradle at it directly:

```powershell
# PowerShell — add these two lines to your $PROFILE for persistence
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH      = "$env:JAVA_HOME\bin;$env:PATH"
```

To verify it worked: open a new PowerShell window and run `java -version`.
You should see `openjdk version "21…"`.

**Windows — if Android Studio is NOT installed:**

Download [Eclipse Temurin 21](https://adoptium.net/) and install it. Then:

```powershell
# Set JAVA_HOME — replace with your actual install path
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.x.x.x-hotspot"
$env:PATH      = "$env:JAVA_HOME\bin;$env:PATH"
```

To make this permanent: System Properties → Environment Variables → add
`JAVA_HOME` as a system variable and add `%JAVA_HOME%\bin` to `Path`.

**macOS:**

```sh
brew install --cask temurin@21
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
```

Add those two `export` lines to `~/.zshrc` to make them permanent.

**Linux:**

```sh
sudo apt install temurin-21-jdk    # Ubuntu/Debian (after adding Adoptium repo)
export JAVA_HOME=/usr/lib/jvm/temurin-21-amd64
export PATH="$JAVA_HOME/bin:$PATH"
```

### 2. Get the Android SDK

**Windows — if Android Studio is installed:**

Android Studio already downloaded the SDK. Tell Gradle where it is:

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
```

Or skip this entirely — when you open the project, Android Studio already
wrote `local.properties` with the SDK path, and Gradle reads that file
automatically. Nothing to do.

**Everyone else:** download the
[Android command-line tools](https://developer.android.com/studio#command-line-tools-only),
unzip them, then:

```sh
# macOS/Linux
export ANDROID_HOME=~/android-sdk
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager \
  "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

```powershell
# Windows PowerShell
$env:ANDROID_HOME = "C:\android-sdk"
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" `
  "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

### 3. Clone and build

```sh
git clone https://github.com/quaternionmedia/qmetronome.git
cd qmetronome
```

```sh
./gradlew assembleDebug     # macOS/Linux
gradlew.bat assembleDebug   # Windows PowerShell/CMD
```

First run downloads Gradle 9.4.1 and Maven dependencies — expect 3–8 minutes
and ~300 MB. Subsequent builds are seconds.

**Expected output:**

```
BUILD SUCCESSFUL in Xs
36 actionable tasks: 36 executed
```

**If you see "JAVA_HOME is not set":** your JAVA_HOME environment variable
isn't visible to the shell running `gradlew.bat`. Double-check that you set
it in the same terminal session, or that the system-wide environment variable
was saved and you've opened a new terminal since.

**If you see "SDK location not found":** either `ANDROID_HOME` isn't set or
`local.properties` is missing. Create `local.properties` in the project root:

```properties
# Windows (escape the backslash, or use forward slashes)
sdk.dir=C\:/Users/yourname/AppData/Local/Android/Sdk

# macOS
sdk.dir=/Users/yourname/Library/Android/sdk
```

### 4. Install on a device

Enable USB debugging on your Android 13+ phone (Settings → Developer options
→ USB debugging). Then:

```sh
# macOS/Linux
adb install app/build/outputs/apk/debug/app-debug.apk
```

```powershell
# Windows — if 'adb' isn't in PATH
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" `
    install app\build\outputs\apk\debug\app-debug.apk
```

To add `adb` permanently to your Windows PATH: System Properties →
Environment Variables → edit Path → add
`%LOCALAPPDATA%\Android\Sdk\platform-tools`.

**If you see "error: no devices/emulators found":** the phone isn't
recognized yet. Try: (1) unplug and replug, (2) accept the "Allow USB
debugging?" dialog that appears on the phone, (3) run `adb devices` to see
what's connected and whether it shows as `unauthorized`.

---

## Run the tests

This works the same on all platforms once the JDK is set up:

```sh
./gradlew test         # macOS/Linux
gradlew.bat test       # Windows
```

Every test should pass (the suite spans a growing number of files, so don't
be surprised if the count doesn't match what you've seen quoted elsewhere).
If any fail, the output tells you which test and why —
read the full message before assuming the worst. A failure here usually means
a test is exercising a real constraint (the visualizer contracts are strict:
"more light at beat start than mid-decay").

---

## What the project is doing, briefly

The build system is **Gradle** (the `gradlew` / `gradlew.bat` wrapper). It
compiles Kotlin, packages Android resources, and runs tests. You never call
`kotlinc` or `javac` directly — Gradle handles it.

**Robolectric** is the test framework that lets Android code run on your
laptop's JVM without a device. It stubs out the Android framework enough
for unit tests. That's why tests run fast — no emulator boot, no ADB push.

The Glyph Matrix SDK (`app/libs/glyph-matrix-sdk-2.0.aar`) is a prebuilt
binary from Nothing Technology Limited, committed directly to the repo. You
don't download it separately. It only works on real Nothing hardware — on
everything else the "Activate as Glyph Toy" button shows a toast instead of
opening the system toys manager. That's intentional and not a bug.

---

## Where to go next

- [`CONTRIBUTING.md`](../CONTRIBUTING.md) — project structure, PR norms, ADR
  discipline, and the release tagging process.
- [`docs/cookbook.md`](cookbook.md) — quick-reference command table for daily
  use once you're set up.
- [`README.md`](../README.md) — architecture: how `MetronomeEngine`,
  visualizers, MIDI, and the widget fit together.
