# ADR-XXXX — Home Screen Widget via Jetpack Glance, Scoped to Glanceable State

| | |
|---|---|
| **Status** | Proposed |
| **Date** | 2026-06-28 |
| **Pends on** | Nothing architectural - filed Proposed rather than Accepted only because ratification is a human action per the org's own process, not because an input is outstanding |

## Context

A home screen widget was requested and investigated before being built (see
`docs/home-screen-widget.md`). The investigation's central finding shaped
this decision: Android widgets are built around infrequent, event-driven
`RemoteViews` updates - the declarative `updatePeriodMillis` floors at 30
minutes, `WorkManager`'s periodic floor is 15 minutes, and even bypassing
both via direct `updateAppWidget()` calls has real per-call Binder/CPU cost
with nothing in the platform optimized for sustained high-frequency pushes.
A widget that tried to mirror the in-app preview's ~40Hz live pulsing would
fight that economics for a result still worse than glancing at the actual
Glyph Matrix.

Getting the *actually-in-scope* version (BPM + play/stop, updated when state
changes) working correctly took four rounds of on-device testing across two
real bugs: a silently-dying update collector (no exception handler - fixed
by adding one) and, more substantially, a one-shot state snapshot taken
outside Glance's reactive composition scope, which meant no amount of
calling `update()`/`updateAll()` from outside could make the rendered label
reflect a new value. The fix was moving the read inside `provideContent { }`
via `collectAsState()`. Both are documented in full in
`docs/home-screen-widget.md`'s round-by-round log; this ADR records the
resulting architecture and the scope boundary, not the debugging narrative.

## Decision

1. **Jetpack Glance** (`androidx.glance:glance-appwidget:1.1.1`, Apache-2.0,
   AOSP-adjacent AndroidX) is the implementation, not classic
   `AppWidgetProvider` + hand-written `RemoteViews`/XML layouts. Glance
   compiles to the same `RemoteViews` under the same platform constraints,
   but the authoring code is Compose-idiomatic, consistent with the rest of
   `ui/`.
2. **Scope is bounded to glanceable state**: current BPM and a play/stop
   control. No matrix-preview thumbnail, no live-pulsing animation. This is
   not a deferred nice-to-have being apologized for - it is the correct
   shape for what this platform surface is for, per the Context above.
3. **Updates are event-driven through two complementary paths**, not a single
   mechanism and not polling: `MetronomeWidget.provideGlance()` reads
   `MetronomeEngine.state` reactively via `collectAsState()` inside
   `provideContent { }` (covers changes while a Glance session is active,
   including the widget's own button tap), and `QMetronomeApp` separately
   collects the same `StateFlow`, reduces it to `(roundedBpm, isPlaying)`
   with `distinctUntilChanged()`, and calls `updateAll()` (covers changes
   after a session has been torn down). `ToggleMetronomeAction` only toggles
   the engine - calling `update()` itself was redundant once `collectAsState`
   is the real mechanism, and removing it removed one place the two paths
   could race.
4. **The widget's background is a secondary entry point into the full app**
   (`actionStartActivity<MainActivity>()`), not just a toggle - tapping
   anywhere outside the play/stop control opens `MainActivity`.

## Consequences

- One new Gradle dependency (`glance-appwidget`). Apache-2.0/OSI-approved,
  so no exception is needed against the org's open-license record - same
  disposition as Robolectric under `DRAFT-android-kotlin-platform-stack.md`,
  an engine selected for a platform-specific testing/UI problem rather than
  written by hand.
- The widget is not unit-testable here (a placed home-screen widget needs a
  real launcher); `docs/home-screen-widget.md`'s manual test checklist is
  the verification path, same shape as the USB MIDI hardware test plan.
- Diagnostic logging (`Log.d`/`Log.e` in `MetronomeWidget` and
  `QMetronomeApp`) is left in place rather than stripped immediately after
  the round-4 fix, deliberately - removing the only tool that found two real
  bugs before the fix has had more than one round of on-device confirmation
  would be premature. Tracked in `docs/publication_checklist.md`.
- `MetronomeEngine` gained no new public surface for this feature beyond what
  `MetronomeGlyphService` already used (`attach()`, `toggle()`, `state`) -
  the widget is a second consumer of an interface that already existed, not
  a reason to grow the engine's API.

## Alternatives considered

1. **Classic `AppWidgetProvider` + hand-written `RemoteViews`/XML layouts** -
   rejected: more boilerplate for the same underlying platform mechanism,
   and inconsistent with the rest of the app being Compose throughout.
2. **A live-pulsing or matrix-preview-thumbnail widget** - rejected per the
   feasibility doc's update-economics finding (Context above); not a
   capability gap with a workaround, a genuine platform mismatch.
3. **Polling instead of event-driven updates** (e.g. a periodic
   `WorkManager` job re-checking state) - rejected: floors at 15 minutes
   regardless, strictly worse latency than reacting to the `StateFlow`
   directly, for no simplicity benefit.

## Revision triggers

- `glance-appwidget` reaches a new stable release past `1.1.1` (a `1.2.0-rc01`
  already exists as of this writing) - re-evaluate the version pin.
- A second QM mobile project needs a widget - generalize the event-driven
  `collectAsState` + `distinctUntilChanged`-`updateAll` pattern at that point
  rather than before; one instance isn't enough to extract a doctrine from
  yet.
- Real usage feedback says BPM + play/stop isn't enough - revisit the scope
  boundary in Decision §2 deliberately, rather than scope-creeping it
  incrementally.

## Amendments

*None.*
