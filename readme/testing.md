# Testing

[← Root README](../README.md)

`./gradlew test` runs the full unit test suite, including Robolectric-backed
tests for anything that touches Android framework classes (the engine's
self-healing render loop, `MetronomeSettings` persistence, MIDI clock source
arbitration) and plain-JUnit tests for the pure-Kotlin pieces (every
visualizer, `GlyphCanvas`, `VisualizerRegistry`). The visualizer tests double
as a contract check: every built-in visualizer is verified to produce a
correctly-sized, in-range frame, to flash brighter at the start of a beat
than mid-decay (the no-audio accessibility requirement - see
`GlyphVisualizer`'s docs), and to render fast enough not to lag the render
loop. `glyph/` and the real Glyph Matrix SDK aren't unit-testable here (a
closed third-party AAR with real Binder calls, not something Robolectric can
shadow) - see [`docs/usb-midi-test-plan.md`](../docs/usb-midi-test-plan.md) for
how that side is verified on real hardware instead.

Compose UI gestures go further than plain assertions: every major user-facing gesture (drag-to-
scrub, long-press-to-type, HOLD's staging, the bar queue, Settings' chip rows, the Glyph Matrix
preview's swipe/double-tap/long-press, layout toggles) has a test that drives the real production
composable through an actual simulated touch gesture, asserts genuine behavior, *and* captures a
screenshot - plus a short GIF for gestures a single still can't convey - in the same test, one
example below. Those captures are what [`docs/user-guide/`](../docs/user-guide/README.md) and the
in-app Help screen are built from (see the `TutorialTopic` entry in the
[Glossary](glossary.md)); `./gradlew generateUserGuide` regenerates the doc and fails outright if
any topic's screenshot or video is missing, so it can never silently go stale. See
CONTRIBUTING.md's "Test coverage" section for how a new gesture gets one of these.

![Example capture: dragging the BPM number to scrub tempo](../docs/images/generated/videos/bpm-drag-scrub.gif)
