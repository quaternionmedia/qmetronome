# qMetronome User Guide

Every gesture qMetronome has, one topic at a time - a screenshot of the real app plus a short video showing it in motion where a single still frame wouldn't tell the whole story (a drag, a swipe, a timed hold). This exact same content is also built into the app itself: tap the **?** icon next to Settings for a live, interactive version where you can actually try each control - richer than what's on this page, since it's the real thing rather than a picture of it.

![qMetronome running the default visualizer at the default tempo](../images/generated/videos/app-running.gif)

*Generated from `TutorialTopics.all` - do not edit by hand; regenerate via `./gradlew generateUserGuide`.*

**Jump to:** [Tempo](#tempo) · [Time Signature](#time-signature) · [Bar Queue](#bar-queue) · [Settings & Layout](#settings--layout) · [MIDI](#midi) · [Glyph Matrix](#glyph-matrix)

## Tempo

- [Drag to fine-tune tempo](bpm-drag-scrub.md) - Drag the BPM number left or right to adjust tempo continuously, instead of tapping the +/- steppers one beat at a time. Works while stopped or playing.
- [Scrubbing into BPH/BPS territory](bpm-drag-scrub-boundary.md) - With Extended range on, dragging the tempo below 1 BPM or above 400 BPM switches to beats-per-hour or beats-per-second automatically - the same drag gesture, just a wider range. Dragging back the other way returns to ordinary BPM.
- [Type an exact tempo, in any unit](bpm-unit-entry-dialog.md) - Long-press the BPM number to type an exact value. Chips let you pick which unit you're typing in - BPM, BPH, or BPS - converting automatically rather than making you do the math.
- [Tap out a tempo](tap-tempo.md) - Tap the BPM number in rhythm to set the tempo by ear - the first tap just starts timing, the second and later taps derive a BPM from the interval between taps.
- [Hold to stage a change](hold-momentary-staging.md) - Press and hold HOLD, then adjust tempo or beats-per-bar - the change is staged, not applied, until you release. Release to commit it all at once, rather than the engine reacting to every intermediate value.
- [Latch HOLD for sticky staging](hold-sticky-latch.md) - Long-press or double-tap HOLD to latch it - staging stays active without holding the button down, until a later tap on HOLD flushes everything and unlatches.

## Time Signature

- [Drag time signature numbers](time-signature-drag-scrub.md) - The beats-per-bar and note-value numbers scrub the same way the BPM number does - drag left or right for continuous adjustment, long-press to type an exact value.
- [Mark a beat as accented](marking-beat-accents.md) - Long-press the beats-per-bar number to open time signature entry, then tap any beat's chip to cycle it through Accent, Strong Accent, Custom, and back to unmarked. These are the same beat types the audible click and MIDI Actions can each be tuned per-beat for.

## Bar Queue

- [Build a queue of bars](bar-queue-management.md) - Add a bar to line up a sequence of differently-metered bars - each one remembers its own beats-per-bar, note value, and tempo. Tap a bar to jump to it; long-press to remove it.
- [Choose how the queue advances](bar-queue-mode-cycling.md) - Tap the queue mode icon to cycle between Loop (wraps back to the first bar), Once (stops advancing at the last bar), and Manual (only moves when you tap a bar directly).
- [Group bars into phrases](phrase-queue-management.md) - Below the bar queue, a small icon adds a phrase - a song-form section ("Verse", "Chorus") with its own full bar queue. Invisible until you add a second phrase; tap a phrase to jump to it, long-press to remove it.
- [Choose how phrases advance](phrase-queue-mode-cycling.md) - Once a second phrase exists, tap the phrase mode icon to cycle Loop, Once, and Manual - the same three modes the bar queue uses, one level up, governing how playback flows from one phrase into the next.

## Settings & Layout

- [Jump straight to BPM/BPH/BPS](settings-jump-to-unit.md) - In Settings, tap a unit chip to jump the live tempo straight into that range - a quick shortcut instead of dragging or typing an exact value.
- [Compact landscape layout](compact-landscape-layout.md) - In Settings -> Layout, enable Compact landscape layout so rotating the phone puts the preview and controls side-by-side instead of overflowing.
- [Symbol-only controls](symbol-only-controls.md) - In Settings -> Layout, enable Symbol-only controls to drop text labels from the main screen's tempo/transport controls in favor of icons and dots.
- [Unit symbols](unit-symbols.md) - In Settings -> Layout, Unit symbols (on by default) shows a small mark next to BPM, beats, beat type, bar, and phrase controls, naming what each one is at a glance. Turn off for a cleaner, symbol-free look.

## MIDI

- [Mechanical vs Organic outgoing clock](settings-clock-feel.md) - Mechanical actively corrects the outgoing MIDI clock for the truest, most locked-in beat. Organic lets a followed clock's own natural timing variance through unfiltered. Only affects clock sent to other apps/gear, not this app's own click or flash.
- [Send MIDI notes or CC per beat type](midi-actions.md) - In Settings -> MIDI Actions, turn on beat actions and pick Note or CC for any beat type (Bar, Beat, Accent, Strong Accent, Custom) - sent over the same virtual/USB connections "Send clock" already reaches, independent of whether the audible click is on.
- [Give one beat its own MIDI action](beat-overrides.md) - In Settings -> Beat Overrides, step to any beat and assign it its own MIDI action, overriding its type's default for that beat only. The Trigger button fires whatever's configured for the engine's current beat position, for one-shot testing without starting playback.
- [Give a phrase its own MIDI action](phrase-actions.md) - In Settings -> Phrase Actions, step to any phrase and assign it its own MIDI action, fired once whenever you jump to that phrase - tapping its dot on the main screen, or arriving there automatically as the queue advances.

## Glyph Matrix

- [Swipe to cycle visualizers](preview-swipe-visualizer.md) - Swipe the Glyph Matrix preview left or right to cycle through available visualizers.
- [Double-tap to play/stop](preview-double-tap-play.md) - Double-tap the preview to toggle playback without reaching for the play/stop button.
- [Long-press to open Settings](preview-long-press-settings.md) - Long-press the preview as a shortcut to Settings, in addition to the dedicated settings button.

