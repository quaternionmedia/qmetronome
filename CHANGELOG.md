# Changelog

Generated from this repo's own annotated git tags (`scripts/generate-changelog.sh`) -
do not edit by hand. Newest first.

## v0.0.25 — 2026-07-06

BPM/BPH/BPS unit switcher, drag responsiveness fix, higher BPS ceiling

Follow-up from on-device manual testing feedback on the streaming audio
rewrite (variance reportedly down from ~12/300 to ~2-3/300, per the user's
estimate).

- New BpmUnit (BPM/BPH/BPS) with conversion/range helpers, and
  BpmUnitEntryDialog replacing the plain NumericEntryDialog for BPM: the
  long-press dialog now has unit chips, and switching between them is the
  "convert between BPH/BPM/BPS" gesture itself. Settings gets a matching
  "Jump to unit" chip row that jumps straight to a representative value in
  whichever unit is picked, auto-enabling extended range if needed.
- Raised EXTENDED_MAX_BPM 3000 -> 12000 (50 -> 200 BPS) - an estimate of
  device capability (5ms floor beat interval on a dedicated elevated-
  priority thread), not a measured limit; documented as such for future
  revision.
- Investigated a reported "backwards" BPM drag below 1 BPM: verified via a
  new direct regression test that steppedBpm's direction is mathematically
  correct in both directions. Found and fixed a real, related rough edge -
  a ~20x responsiveness cliff right at the BPM=1 boundary between flat and
  log-scale stepping - by raising LOG_BPM_STEP_FACTOR 5% -> 10%.
- Investigated a reported lack of perceptible difference between Mechanical
  and Organic MIDI clock-out modes: confirmed the toggle/logic is intact
  and untouched; the setting only affects the outgoing MIDI clock sent to
  other apps/gear, not this app's own click/flash, so it's untestable by
  ear on the phone alone. Clarified this in the in-app copy and checklist.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>

## v0.0.24 — 2026-07-05

v0.0.24: audio offset lookahead, decoupled tap tempo, extended BPM range, live Settings tempo/bar mirror, tunable mute ramp, real dev version string

## v0.0.23 — 2026-07-05

Alpha v0.0.23: visual offset default, MIDI clock fixes, tunable click engine + Mechanical/Organic clock-out timing, Firework visualizer, persistent playback mode, collapsible Settings redesign

## v0.0.22 — 2026-07-04

Alpha v0.0.22: governance integration pass - ADR process fixes, constitution adoption-scope ADR, CI ADR lint, docs accuracy pass across onboarding/cookbook/publication checklist

## v0.0.21 — 2026-07-03

Alpha v0.0.21: ambient per-bar glyph background, per-bar visualizer, tempo-preserving meter changes

## v0.0.3 — 2026-07-03

Alpha v0.0.3: bar queue with per-bar tempo, random mute, extensible click sounds

## v0.0.2 — 2026-07-03

Alpha v0.0.2: hold latch, BPM tap-tempo, reusable sliders, unified metronome branding

## v0.0.1 — 2026-07-02

Alpha v0.0.1

