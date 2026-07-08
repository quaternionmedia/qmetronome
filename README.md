# qMetronome

A metronome and tempo visualizer for performing and practicing musicians, built for any
Android 13+ phone: tap tempo, drag-to-scrub, a bar queue for lining up a set's tempo/meter
changes, MIDI clock sync, and a home screen widget - no special hardware required. On a
Nothing Phone (3) or Phone (4a) Pro, it additionally lights up the physical Glyph Matrix as a
second, glanceable display, via the [Glyph Matrix Developer Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit).

<p align="center">
  <img src="docs/images/generated/videos/app-running.gif" width="260" alt="qMetronome running the default visualizer at the default tempo">
</p>

**New here?** [`docs/user-guide/`](docs/user-guide/README.md) is the visual, gesture-by-gesture
guide - every drag, tap, and toggle this app has, each with a screenshot and a short video showing
it in motion. The exact same content is also built into the app itself - tap the **?** icon next
to Settings for a live, interactive version of the same walkthrough.

This file is a short index. New contributors should start with
[`CONTRIBUTING.md`](CONTRIBUTING.md) — it covers building with and without
Android Studio, the project layout, and PR norms. Feasibility investigations,
manual test plans, and release readiness live in [`docs/`](docs/README.md);
decision records live in [`governance/qm/adr/`](governance/qm/adr/README.md),
inside the `governance/qm` submodule (this project's own branch of the org's
constitution repo).

## Contents

Each of these used to be a section of this file - now its own page, so it reads as one topic (and,
where relevant, one screenshot/gif) at a time. Full index, with one-line descriptions, in
[`readme/README.md`](readme/README.md).

- [Requirements](readme/requirements.md)
- [Using qMetronome](readme/using-qmetronome/README.md) - the narrative walkthrough: tempo, bar
  queue, Glyph Matrix, MIDI sync, widget.
- [Adding a new visualizer](readme/adding-a-new-visualizer.md)
- [Setup notes](readme/setup-notes.md)
- [Testing](readme/testing.md)
- [Project governance](readme/project-governance.md)
- [CI](readme/ci.md)
- [Glossary](readme/glossary.md) - every class/singleton by name, reference-style.
- [Inspired by](readme/inspired-by.md)
- [License](readme/license.md)
- [Known limitations / next steps](readme/known-limitations.md)
