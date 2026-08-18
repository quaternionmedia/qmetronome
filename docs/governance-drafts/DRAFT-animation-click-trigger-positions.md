# ADR-XXXX — Selectable click-trigger positions for metronome visualizers

> **Staging note:** This draft belongs in `governance/qm adr/` on the
> `project/qmetronome` branch of the qm submodule repo. It is staged here
> while the submodule branch is pending push by a human with qm write access.
> Once pushed and PR'd there, this file should be removed.

<!--
DRAFTING RULES (delete this comment block before ratification):

1. NUMBER AT RATIFICATION, NOT BEFORE. Drafts are ADR-XXXX.
2. SQUASH BEFORE RATIFICATION. A draft has no memory.
3. ONE DECISION PER ADR.
4. WRITE THE ALTERNATIVES HONESTLY.
5. DON'T DECIDE OPEN QUESTIONS BY STEALTH.
-->

| | |
|---|---|
| **Status** | Proposed |
| **Date** | 2026-08-18 |
| **Pends on** | UI design for the mode picker (three modes, one requiring a numeric sub-setting `n`); a UX decision on whether the mode is per-visualizer or global. The architectural shape is decided; these design inputs are needed before implementation begins. |
| **Tracks** | issue #11 |
| **Tools** | Analysis and draft text produced with GitHub Copilot (coding agent); authored and submitted by mrharpo |

## Context

The arm visualizers (`MetronomeVisualizer`, `PendulumVisualizer`) flash their
weight at every beat boundary (`beat.phase == 0`). On a real acoustic
metronome the click fires at the extremes of the arm's swing — once per
half-cycle, twice per full cycle — not at the centre. With a 4/4 time
signature the arm crosses the centre twice and the extremes twice per bar,
but beat boundaries (`phase == 0`) may coincide with the centre crossing as
well as the extremes, causing the flash to fire at the centre.

The `GlyphVisualizer` interface is a pure function
`render(matrixSize, beat): IntArray` (`visualizers/GlyphVisualizer.kt`).
Visualizers are stateless and have no access to settings at render time. The
arm swing position is computed locally in each visualizer from
`beat.beatIndex`, `beat.phase`, and `beat.beatsPerBar` via a triangle-wave
formula — it is not carried in `BeatPhase` (`engine/BeatPhase.kt`). The
`VisualizerRenderTest` suite enforces two per-visualizer contracts: the frame
is brightest at `phase == 0` (beat perceptible without audio), and brighter
on accented beats (bar-1 findable at a glance). Any flash-timing change must
preserve both or explicitly replace them with mode-aware equivalents.

Three trigger modes are required (issue #11):
- **`SIDES_ONLY`** — flash only when the arm is near either extreme; fires
  twice per full sweep cycle. This is real-metronome behaviour. Default.
- **`SIDES_AND_CENTER`** — flash at both extremes and the centre; the current
  behaviour.
- **`N_PER_CYCLE`** — flash `n` times per full sweep cycle at equally spaced
  positions; `n` is user-settable.

## Decision

1. A `ClickAnimationMode` sealed class (or enum) with three variants —
   `SidesOnly`, `SidesAndCenter`, and `NPerCycle(n: Int)` — is added to the
   engine package. The default is `SidesOnly`.

2. `MetronomeSettings` persists the selected mode and, for `NPerCycle`, the
   `n` value, alongside the existing `visualizerId` and `clickEnabled` fields
   (`engine/MetronomeSettings.kt`).

3. The mode is injected into `MetronomeVisualizer` and `PendulumVisualizer`
   at construction time, not at `render` time, keeping the `GlyphVisualizer`
   interface signature unchanged. `VisualizerRegistry` reads the mode from
   `MetronomeSettings` when building the list, or rebuilds when the setting
   changes. Non-arm visualizers (e.g. `PulseVisualizer`, `SweepVisualizer`)
   are unaffected.

4. Flash-gating logic per mode, using the existing triangle-wave `swing`
   value (`−1` at full left, `+1` at full right, `0` at centre):
   - `SidesOnly`: apply `flash` only when `|swing| > SWING_EXTREME_THRESHOLD`
     (recommended starting value: `0.85`, exposed as a tunable constant).
     Between extremes the weight renders at a constant dim level.
   - `SidesAndCenter`: existing behaviour unchanged.
   - `NPerCycle`: apply `flash` when `(barProgress * n) % 1.0 < CYCLE_EPSILON`,
     where `barProgress` is the existing `(beatIndex + phase) / beatsPerBar`.

5. `VisualizerRenderTest` is extended with mode-aware variants:
   - `SidesOnly`: assert the frame at a swing extreme is brighter than the
     frame at centre swing, for the same `phase` offset from the nearest
     extreme.
   - `NPerCycle`: assert the frame at each trigger position is the brightest
     in its surrounding window.
   Existing tests are not removed — they remain valid for `SidesAndCenter`.

## Consequences

- `SIDES_ONLY` default gives new installs real-metronome behaviour
  immediately. Existing installs get `SidesOnly` on upgrade; cost accepted
  since the issue reports the current behaviour as a bug.
- The `GlyphVisualizer` interface is not changed. Third-party or future
  visualizers not using arm-swing are unaffected.
- `VisualizerRegistry` must be rebuilt whenever the mode setting changes.
  The mechanism (StateFlow observation in the service, or a factory call at
  settings-change time) is an implementation-level detail.
- The existing "brightest at `phase == 0`" test in `VisualizerRenderTest`
  is still valid for `SidesAndCenter` and must not be removed. For `SidesOnly`
  it does not apply when `phase == 0` coincides with a centre-swing position;
  the test comments are updated to reflect this.
- The `Pends on` UX question (global vs per-visualizer mode, picker design)
  must be resolved before implementation. This decision governs the
  architecture; the UI form is not decided here.

## Alternatives considered

1. **Add a `mode` parameter to `GlyphVisualizer.render`** — rejected:
   breaks every existing visualizer's signature, most of which have no use
   for the parameter. Construction-time injection limits impact to the two
   arm visualizers.

2. **Add `swingPosition: Float` to `BeatPhase`** — rejected: `BeatPhase` is
   engine-side state; swing geometry is a visualizer concern derived from
   `BeatPhase`, not the other way around. Adding it couples the engine to a
   specific visual metaphor.

3. **A single `clickFlashEveryBeat: Boolean` toggle** — rejected: too coarse;
   covers `SidesOnly` vs `SidesAndCenter` but not `NPerCycle`, and names
   the wrong concept (arm position, not beat rate).

4. **`SidesAndCenter` as default, `SidesOnly` as opt-in** — rejected: the
   issue describes `SidesAndCenter` as a bug. Preserving it as the default
   keeps the problem for every new install.

## Revision triggers

- The arm-swing triangle-wave changes (e.g. to a sine wave): `SWING_EXTREME_THRESHOLD`
  needs recalibration; amend §4.
- A future visualizer uses arm-swing with different geometry: review the
  construction-time injection pattern in §3 for generality.
- User research shows `SidesAndCenter` is preferred as a default: amend §1.
- The pending UX design (global vs per-visualizer mode, picker form) is
  decided: if it differs from §3's implied single global mode, amend §3.

## Amendments

*None.*
