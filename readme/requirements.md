# Requirements

[← Root README](../README.md)

- **Any Android 13 (API 33)+ phone, from any manufacturer** — the whole core metronome (tap
  tempo, BPM/beats-per-bar, the bar queue, random mute, on-screen visualizer preview, audible
  click, home screen widget) works here, no special hardware needed. `minSdk` 33 is driven by the
  Glyph Matrix SDK itself rather than anything in this app's own code (see
  [Setup notes](setup-notes.md)), but applies whether or not you have Glyph hardware.
- **A Nothing Phone (3) or Phone (4a) Pro adds the physical Glyph Matrix display** - an
  enhancement, not a requirement. On any other device the Glyph Toy button just shows a toast
  saying it's unsupported; nothing crashes.
- **MIDI clock sync** (virtual in-app-to-app, or USB) degrades gracefully on devices without MIDI
  support (`android.software.midi` is declared optional in the manifest). USB MIDI additionally
  needs a device with USB host/OTG support.
- **The Glyph Matrix (and its on-screen preview) must visually represent which phrase is active
  whenever more than one phrase is queued** — a hard requirement, not optional polish, since the
  physical Glyph hardware has no other on-device way to show phrase position short of opening the
  app's own UI. Met by `QueueOverlay`'s radial per-phrase indicator (a small dot per phrase around
  the matrix's outer rim); independently toggleable, on by default.
