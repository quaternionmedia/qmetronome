# Docs index

What's in here, in the order you'd actually want to read it, and how it
relates to `adr/`. The main [`README.md`](../README.md) is the entry point
for architecture/setup/testing; this directory is feasibility investigations,
test plans, and the project's relationship to the org's decision-record
discipline - the *why* and *how to verify*, not the *what*.

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

Corresponding decision records: `adr/DRAFT-midi-clock-as-open-standard-seam.md`
and `adr/DRAFT-home-screen-widget-via-glance.md`.

## Project governance

- **[`governance-perspective.md`](governance-perspective.md)** — this
  project is the org's first mobile/cross-platform-device project, and two
  of the `qm` constitution's enforcement mechanisms (built around self-hosted
  server infrastructure) don't transfer cleanly to a sideloaded app with a
  closed hardware-vendor SDK. Named explicitly rather than papered over, fed
  back to the org as an open question. See `adr/README.md`'s "this project is
  a deliberate experiment" section and
  `adr/DRAFT-glyph-matrix-sdk-dependency.md` /
  `adr/DRAFT-android-kotlin-platform-stack.md` for the two specific gaps.

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

## How `adr/` fits in

`docs/` is investigation and verification; `adr/` is the decision record once
something's actually been decided. A doc here can exist with no ADR (a test
plan doesn't need one) or alongside one (the MIDI and widget docs both do).
Read `adr/README.md` first if you haven't - it's the process contract, and
explains why this project's ADRs are filed `Proposed`/numberless rather than
`Accepted`/numbered (ratification is a human action, not something either of
us assigns ourselves).
