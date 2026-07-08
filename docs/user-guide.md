# qMetronome User Guide

Every gesture qMetronome has, one topic at a time - a screenshot of the real app plus a short video showing it in motion where a single still frame wouldn't tell the whole story (a drag, a swipe, a timed hold). This exact same content is also built into the app itself: tap the **?** icon next to Settings for a live, interactive version where you can actually try each control - richer than what's on this page, since it's the real thing rather than a picture of it.

*Generated from `TutorialTopics.all` - do not edit by hand; regenerate via `./gradlew generateUserGuide`.*

**Jump to:** [Tempo](#tempo) · [Time Signature](#time-signature) · [Bar Queue](#bar-queue) · [Settings & Layout](#settings--layout) · [MIDI Clock](#midi-clock) · [Glyph Matrix](#glyph-matrix)

## Tempo

### Drag to fine-tune tempo

Drag the BPM number left or right to adjust tempo continuously, instead of tapping the +/- steppers one beat at a time. Works while stopped or playing.

![Drag to fine-tune tempo](images/generated/screenshots/bpm-drag-scrub.png)

**In motion:**

![Drag to fine-tune tempo (in motion)](images/generated/videos/bpm-drag-scrub.gif)

### Scrubbing into BPH/BPS territory

With Extended range on, dragging the tempo below 1 BPM or above 400 BPM switches to beats-per-hour or beats-per-second automatically - the same drag gesture, just a wider range. Dragging back the other way returns to ordinary BPM.

![Scrubbing into BPH/BPS territory](images/generated/screenshots/bpm-drag-scrub-boundary.png)

**In motion:**

![Scrubbing into BPH/BPS territory (in motion)](images/generated/videos/bpm-drag-scrub-boundary.gif)

### Type an exact tempo, in any unit

Long-press the BPM number to type an exact value. Chips let you pick which unit you're typing in - BPM, BPH, or BPS - converting automatically rather than making you do the math.

![Type an exact tempo, in any unit](images/generated/screenshots/bpm-unit-entry-dialog.png)

**In motion:**

![Type an exact tempo, in any unit (in motion)](images/generated/videos/bpm-unit-entry-dialog.gif)

### Tap out a tempo

Tap the BPM number in rhythm to set the tempo by ear - the first tap just starts timing, the second and later taps derive a BPM from the interval between taps.

![Tap out a tempo](images/generated/screenshots/tap-tempo.png)

**In motion:**

![Tap out a tempo (in motion)](images/generated/videos/tap-tempo.gif)

### Hold to stage a change

Press and hold HOLD, then adjust tempo or beats-per-bar - the change is staged, not applied, until you release. Release to commit it all at once, rather than the engine reacting to every intermediate value.

![Hold to stage a change](images/generated/screenshots/hold-momentary-staging.png)

**In motion:**

![Hold to stage a change (in motion)](images/generated/videos/hold-momentary-staging.gif)

### Latch HOLD for sticky staging

Long-press or double-tap HOLD to latch it - staging stays active without holding the button down, until a later tap on HOLD flushes everything and unlatches.

![Latch HOLD for sticky staging](images/generated/screenshots/hold-sticky-latch.png)

**In motion:**

![Latch HOLD for sticky staging (in motion)](images/generated/videos/hold-sticky-latch.gif)

## Time Signature

### Drag time signature numbers

The beats-per-bar and note-value numbers scrub the same way the BPM number does - drag left or right for continuous adjustment, long-press to type an exact value.

![Drag time signature numbers](images/generated/screenshots/time-signature-drag-scrub.png)

**In motion:**

![Drag time signature numbers (in motion)](images/generated/videos/time-signature-drag-scrub.gif)

## Bar Queue

### Build a queue of bars

Add a bar to line up a sequence of differently-metered bars - each one remembers its own beats-per-bar, note value, and tempo. Tap a bar to jump to it; long-press to remove it.

![Build a queue of bars](images/generated/screenshots/bar-queue-management.png)

**In motion:**

![Build a queue of bars (in motion)](images/generated/videos/bar-queue-management.gif)

### Choose how the queue advances

Tap the queue mode icon to cycle between Loop (wraps back to the first bar), Once (stops advancing at the last bar), and Manual (only moves when you tap a bar directly).

![Choose how the queue advances](images/generated/screenshots/bar-queue-mode-cycling.png)

**In motion:**

![Choose how the queue advances (in motion)](images/generated/videos/bar-queue-mode-cycling.gif)

## Settings & Layout

### Jump straight to BPM/BPH/BPS

In Settings, tap a unit chip to jump the live tempo straight into that range - a quick shortcut instead of dragging or typing an exact value.

![Jump straight to BPM/BPH/BPS](images/generated/screenshots/settings-jump-to-unit.png)

**In motion:**

![Jump straight to BPM/BPH/BPS (in motion)](images/generated/videos/settings-jump-to-unit.gif)

### Compact landscape layout

In Settings -> Layout, enable Compact landscape layout so rotating the phone puts the preview and controls side-by-side instead of overflowing.

![Compact landscape layout](images/generated/screenshots/compact-landscape-layout.png)

**In motion:**

![Compact landscape layout (in motion)](images/generated/videos/compact-landscape-layout.gif)

### Symbol-only controls

In Settings -> Layout, enable Symbol-only controls to drop text labels from the main screen's tempo/transport controls in favor of icons and dots.

![Symbol-only controls](images/generated/screenshots/symbol-only-controls.png)

**In motion:**

![Symbol-only controls (in motion)](images/generated/videos/symbol-only-controls.gif)

## MIDI Clock

### Mechanical vs Organic outgoing clock

Mechanical actively corrects the outgoing MIDI clock for the truest, most locked-in beat. Organic lets a followed clock's own natural timing variance through unfiltered. Only affects clock sent to other apps/gear, not this app's own click or flash.

![Mechanical vs Organic outgoing clock](images/generated/screenshots/settings-clock-feel.png)

**In motion:**

![Mechanical vs Organic outgoing clock (in motion)](images/generated/videos/settings-clock-feel.gif)

## Glyph Matrix

### Swipe to cycle visualizers

Swipe the Glyph Matrix preview left or right to cycle through available visualizers.

![Swipe to cycle visualizers](images/generated/screenshots/preview-swipe-visualizer.png)

**In motion:**

![Swipe to cycle visualizers (in motion)](images/generated/videos/preview-swipe-visualizer.gif)

### Double-tap to play/stop

Double-tap the preview to toggle playback without reaching for the play/stop button.

![Double-tap to play/stop](images/generated/screenshots/preview-double-tap-play.png)

**In motion:**

![Double-tap to play/stop (in motion)](images/generated/videos/preview-double-tap-play.gif)

### Long-press to open Settings

Long-press the preview as a shortcut to Settings, in addition to the dedicated settings button.

![Long-press to open Settings](images/generated/screenshots/preview-long-press-settings.png)

**In motion:**

![Long-press to open Settings (in motion)](images/generated/videos/preview-long-press-settings.gif)

