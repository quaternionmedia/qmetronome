# A widget for the home screen

[← Using qMetronome](README.md) · [Root README](../../README.md)

*(No screenshot here yet - the widget is a Jetpack Glance surface rendered outside the Compose UI
this project's Roborazzi pipeline captures, so it doesn't fit the same "one gif" convention as
everything else in this folder without separate tooling. Worth revisiting if that changes.)*

Long-press the home screen → **Widgets** → place qMetronome, and it shows the current BPM with a
START/STOP control. Tapping START/STOP toggles the same engine the app and the Glyph Toy use, so
it's always in sync with both; tapping anywhere else on the widget opens the full app for tempo,
visualizer, or MIDI settings. The number updates on its own whenever BPM changes from the app,
MIDI, or the widget itself - no need to remove and re-place it. It's deliberately BPM + play/stop
only, not a live mirror of the Glyph Matrix animation - see
[`docs/home-screen-widget.md`](../../docs/home-screen-widget.md) for why that was ruled out rather
than attempted.
