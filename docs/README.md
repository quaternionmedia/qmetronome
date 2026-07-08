# Docs index

What's in here, in the order you'd actually want to read it, and how it
relates to `governance/qm/adr/`. The main [`README.md`](../README.md) is the entry point
for architecture/setup/testing; [`CONTRIBUTING.md`](../CONTRIBUTING.md)
covers getting a build running (with and without Android Studio) and the
contribution norms; this directory is feasibility investigations, test plans,
and the project's relationship to the org's decision-record discipline -
the *why* and *how to verify*, not the *what*.

## Getting started

- **[`onboarding.md`](onboarding.md)** — step-by-step first build for
  someone new to Android development: JDK/SDK setup for Windows and
  macOS/Linux (with and without Android Studio), annotated expected output,
  and "if this goes wrong" notes.
- **[`cookbook.md`](cookbook.md)** — quick-reference for experienced
  contributors: build/device/release commands, visualizer recipe,
  engine API, MIDI shortcuts, and common errors.

## For end users, not contributors

- **[`user-guide/`](user-guide/README.md)** — every major gesture (tempo scrub,
  HOLD staging, the bar queue, Settings' chip rows, the Glyph Matrix preview,
  layout toggles), one page per topic with a screenshot (or video, for
  motion-based gestures). Generated, not hand-written - see `TutorialTopics.all`
  (`app/src/main/java/.../tutorial/`) and CONTRIBUTING.md's "Test coverage" section
  for how a new topic gets added and why the screenshot can never silently go
  stale. The same content also drives the in-app Help screen (`ui/HelpScreen.kt`),
  reached via the help icon next to Settings' gear - that version embeds the
  real, live controls instead of a static image.
- **[`../CHANGELOG.md`](../CHANGELOG.md)** — generated from this repo's own
  annotated tag history (`scripts/generate-changelog.sh`); see
  CONTRIBUTING.md's "Cutting a release" section.

## Feature docs (feasibility → implementation → test plan, per feature)

Each of these follows the same shape: an investigation written before the
feature existed, then updated in place as it was actually built and tested.
Where on-device testing found real bugs, they're logged round-by-round
rather than smoothed over - the bug and the wrong hypothesis along the way
are part of the record, not just the eventual fix.

- **[`external-midi-clock.md`](external-midi-clock.md)** — MIDI Clock sync,
  both directions, over both virtual (inter-app) and USB transports:
  following an external clock, and sending our own clock out. Implemented.
  Pairs with **[`usb-midi-test-plan.md`](usb-midi-test-plan.md)**, the manual
  checklist for the parts of this that need real hardware to verify.
- **[`home-screen-widget.md`](home-screen-widget.md)** — the BPM + play/stop
  widget. Implemented, four rounds of on-device debugging logged in full
  (a silently-dying update collector, then a one-shot-snapshot-vs-reactive-
  composition bug that took two attempts to correctly diagnose). Ends with
  its own manual test checklist.
- **[`realtime-audio-roadmap.md`](realtime-audio-roadmap.md)** — **not
  started**, a scoping doc rather than a feasibility investigation with a
  build behind it yet: independent audio-channel routing per beat type,
  multiple simultaneous beat "threads" (true polyrhythm), and per-beat-type
  MIDI actions. Written down explicitly so this direction is built toward
  deliberately rather than boxed out by today's singleton-shaped engine.

Corresponding decision records:
`governance/qm/adr/DRAFT-midi-clock-as-open-standard-seam.md` and
`governance/qm/adr/DRAFT-home-screen-widget-via-glance.md`. No ADR yet for
the real-time audio roadmap - nothing to ratify until something's decided.

## Project governance

The org's constitution is vendored as a git submodule at
[`../governance/qm`](../governance/qm), checked out on this project's own
dedicated branch (`project/qmetronome`) — which is also where this
project's own `adr/` directory lives, rather than as a top-level directory
in this repo. The branch's ancestry is the pin; see
`governance/qm/adr/README.md`'s "Adopted org records" section.

- **[`governance-perspective.md`](governance-perspective.md)** — this
  project is the org's first mobile/cross-platform-device project, and two
  of the `qm` constitution's enforcement mechanisms (built around self-hosted
  server infrastructure) don't transfer cleanly to a sideloaded app with a
  closed hardware-vendor SDK. Named explicitly rather than papered over, fed
  back to the org as an open question.
- **`governance/qm/adr/DRAFT-constitution-adoption-scope.md`** — the full,
  record-by-record disposition (adopted in full / adopted-but-scoped /
  not-applicable) for every record in the pinned corpus, including the
  baseline dependency-license audit the constitution requires at adoption.
  `governance-perspective.md` covers two headline gaps worth surfacing to
  the org; this ADR is the complete internal decision.
- `governance/qm/adr/README.md`'s "this project is a deliberate experiment"
  section, `governance/qm/adr/DRAFT-glyph-matrix-sdk-dependency.md`, and
  `governance/qm/adr/DRAFT-android-kotlin-platform-stack.md` are the two
  specific gaps' own dedicated records.
- **[`governance-next-steps.md`](governance-next-steps.md)** — what's left
  after the adoption and broadening passes, grouped by whether it needs
  review, an audit, or new work, across both this repo and `qm`.

## Release readiness

- **[`publication_checklist.md`](publication_checklist.md)** — repo/code
  readiness: what's done, what's deliberately deferred (and why), and what
  genuinely needs a human (final on-device sign-off) before this ships.
- **[`app-store-checklist.md`](app-store-checklist.md)** — the separate
  question of actually publishing it: Google Play listing/signing
  requirements, and an honest accounting of what's confirmed vs. genuinely
  unverified about Nothing's distribution channel (there is no separate
  Nothing app store and no separate submission process). See [`../PRIVACY.md`](../PRIVACY.md), drafted
  because Play Console requires a privacy policy URL for every app
  regardless of data collected.
- **[`google-play-setup.md`](google-play-setup.md)** — the Play Console
  account/app-creation steps `app-store-checklist.md`'s listing requirements
  assume are already done.

## How `governance/qm/adr/` fits in

`docs/` is investigation and verification; `governance/qm/adr/` is the
decision record once something's actually been decided. A doc here can
exist with no ADR (a test plan doesn't need one) or alongside one (the MIDI
and widget docs both do). Read `governance/qm/adr/README.md` first if you
haven't - it's the process contract, and explains why this project's ADRs
are filed `Proposed`/numberless rather than `Accepted`/numbered
(ratification is a human action, not something either of us assigns
ourselves).
