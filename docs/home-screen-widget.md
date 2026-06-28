# Home screen widget: feasibility & lift

**Status: the BPM + play/stop widget is implemented** (`widget/MetronomeWidget`,
`widget/MetronomeWidgetReceiver`). The rest of this doc is kept as the design
rationale and as the manual test checklist at the bottom, since a placed
home-screen widget isn't something a unit test can verify.

**The short answer:** a useful widget is a small lift (roughly a day) if it
shows BPM + a play/stop control and updates only when state actually
changes. A widget that smoothly mirrors the in-app preview's live pulsing
animation is not realistic on this platform regardless of effort — Android
widgets are built around infrequent, event-driven updates, not a 40Hz render
loop, and fighting that would cost real battery for a result that's still
worse than just looking at the Glyph Matrix or opening the app.

## What Android widgets are actually built for

A widget's content is `RemoteViews` (or, via Jetpack Glance, Compose-like
code that *compiles down to* `RemoteViews` under the hood — same platform
mechanism, more ergonomic API). Two things constrain it hard:

1. **Update frequency.** The declarative `updatePeriodMillis` (set in the
   widget's manifest metadata) has a platform-enforced floor of 30 minutes —
   a value below that is silently clamped up to it. `WorkManager`'s
   `PeriodicWorkRequest` floor is 15 minutes. Neither is fast enough for
   "looks like it's pulsing." The way around both: call
   `AppWidgetManager.updateAppWidget()` directly, e.g. from a running
   foreground service, which isn't floor-limited — but each call reconstructs
   the `RemoteViews` and pushes them to the launcher process over Binder,
   which has real CPU/battery cost at any sustained rate, and nothing in the
   platform is optimized for doing that dozens of times a second.
2. **Bitmap memory.** A `RemoteViews`'s total bitmap memory can't exceed
   screen-width × screen-height × 4 bytes × 1.5 — generous for a small
   rasterized 13×13 or 25×25 matrix image, not a real constraint here.

Net effect: the right design target for a widget is "glanceable state,
updated when it changes," not "ambient animation."

## What's actually worth building (as built)

A widget showing the current BPM and a play/stop button, updated only on
state changes, not polled. This section describes the architecture as it
ended up after four rounds of on-device testing (below) found real gaps in
the first pass - read those rounds for *why* it ended up this shape, not
just what the shape is:

- `MetronomeWidget.provideGlance()` reads `MetronomeEngine.state` reactively
  *inside* `provideContent { }` via `collectAsState()` - not a one-shot
  snapshot taken before entering the composable. This is what actually makes
  it reactive (round 4); a snapshot taken outside the composable doesn't get
  picked up by recomposition no matter how often something calls
  `update()`/`updateAll()` afterward.
- `QMetronomeApp.onCreate()` separately collects `MetronomeEngine.state`,
  maps it down to just `(roundedBpm, isPlaying)`, and calls
  `distinctUntilChanged()` before calling `MetronomeWidget().updateAll(this)`
  - this covers changes while no Glance session is currently active, and is
  still what keeps updates event-driven instead of reacting to the render
  loop's ~40Hz phase ticks.
- The play/stop control is a plain `Text` wrapped in
  `GlanceModifier.clickable(actionRunCallback<ToggleMetronomeAction>())`, not
  Glance's `Button` (round 2/3) and not a hand-built `PendingIntent` to a
  `BroadcastReceiver`. `ToggleMetronomeAction` only toggles the engine now -
  it doesn't call `update()` itself (round 4 removed that as redundant once
  `collectAsState` is the actual update mechanism).
- Tapping the widget's background (anywhere outside the play/stop control)
  opens `MainActivity` via `actionStartActivity<MainActivity>()` (round 4) -
  a fast path into the full app for settings/visualizer changes.
- Cold start (app process not running, widget freshly placed) is covered by
  `MetronomeEngine.attach()` inside `provideGlance` - it loads
  `MetronomeSettings`' persisted bpm before the reactive read needs a value.

A static matrix-preview *thumbnail* (rasterizing `GlyphVisualizer.render()`'s
`IntArray` to a `Bitmap`) was scoped out of this pass - see Known limitations
below.

## Implementation path: Glance over classic RemoteViews

[Jetpack Glance](https://developer.android.com/jetpack/androidx/releases/glance)
(`androidx.glance:glance-appwidget:1.1.1`, the latest confirmed-stable
release at implementation time, minSdk 23 — well under this project's 33)
is what's used. It compiles to the same `RemoteViews` under the hood (same
constraints as above), but the authoring code looks like the rest of `ui/`
instead of XML layouts + `AppWidgetProvider` boilerplate. A `1.2.0-rc01` was
available but a release candidate wasn't worth the risk for a first pass -
worth revisiting once it's stable.

## Known limitations / next steps

- **No matrix-preview thumbnail.** Deliberately scoped out - BPM + play/stop
  is the whole widget, by design (see "what widgets are actually built for"
  above).
- **No Song-Position-style resume.** Tapping play in the widget calls the
  same `MetronomeEngine.toggle()` the in-app button does - it doesn't know
  or care it was triggered from a widget, which is exactly the point (no
  separate state machine to keep in sync).
- **Widget receiver is `android:exported="true"`** - required for the
  launcher/system process to deliver `APPWIDGET_UPDATE`, not a security
  loosening (the action is signature/system-protected).

## First test round: stop didn't work, BPM wasn't reactive

Round 1 testing found the stop button not working/updating and BPM updates
feeling sluggish. Investigation (inspecting the actual `glance-appwidget`
1.1.1 bytecode, not just docs, to get real answers instead of guessing):

- The manifest auto-merge for Glance's click-dispatch infrastructure
  (`ActionCallbackBroadcastReceiver`, the trampoline activities) is correct -
  confirmed by reading the actual merged manifest, not assumed.
- **A real bug, fixed:** `QMetronomeApp`'s widget-update collector had no
  exception handler. If `updateAll()` threw on its *first* invocation for any
  reason, that coroutine would die silently and never collect again - which
  looks exactly like "BPM stopped being reactive" with nothing in normal
  logs to explain why. It now has a `CoroutineExceptionHandler` plus a
  try/catch around the update call itself, and logs every step (`adb logcat
  -s MetronomeWidget:* QMetronomeApp:*`).
- **Consolidated to one update mechanism.** `ToggleMetronomeAction` used to
  call `update(context, glanceId)`; it now calls `updateAll(context)`, the
  same call `QMetronomeApp` uses - fewer distinct code paths to reason about,
  and not dependent on that specific `glanceId` still being tracked.
- **A platform characteristic, not a bug I can fix:** the merged manifest
  shows Glance pulls in WorkManager's background-execution machinery
  (`SystemAlarmService`, `SystemJobService`) for some of its own reliability
  guarantees. That means there's a layer between calling `updateAll()` and
  the home screen actually refreshing that I don't control, and which can be
  slower under battery optimization/Doze. If BPM updates are still genuinely
  laggy (not stuck - just slow) after the fixes above, that's likely this,
  and there isn't a clean way around it short of not using Glance at all.

If it still doesn't work after these fixes, the logging above should show
exactly where: does `ToggleMetronomeAction.onAction` fire at all when you
tap the button? Does `MetronomeEngine`'s `isPlaying` actually flip? Does
`provideGlance` get re-invoked with the new value? Does `updateAll` throw?
Each of those is now a distinct, visible log line instead of a guess.

## Fourth test round: collectAsState instead of a one-shot snapshot, plus an app shortcut

Round 3 left the bug open: a plain `Text` had the identical stuck-label symptom as `Button`,
which ruled out the UI element as the cause. The actual root cause was upstream of any
composable, in `provideGlance` itself: it took a one-shot `MetronomeEngine.state.value` snapshot
*outside* `provideContent { }` and closed over it. Glance's composition inside `provideContent`
doesn't get a second look at that value just because `update()`/`updateAll()` ran again somewhere
else - without a reactive read *inside* the composable, there's nothing telling Glance's
recomposer that anything changed, regardless of how many external update calls fire.

**The fix:** move the read inside `provideContent { }` and make it reactive -
`val beat by MetronomeEngine.state.collectAsState()` - so Glance's own composition observes the
`StateFlow` directly during its active session window (Glance keeps a session alive for a short
period after the last interaction/update, then tears it down) rather than depending on a
snapshot taken once when `provideGlance` happened to run. Two follow-on simplifications now that
this is the real mechanism:

- `ToggleMetronomeAction` no longer calls `update()`/`updateAll()` at all - it only toggles the
  engine. The `QMetronomeApp` collector's `updateAll()` (for changes from outside the widget) and
  `collectAsState`'s direct observation (for changes during an active session, including the
  widget's own tap) together cover every path; the action calling `updateAll()` itself was
  redundant once the composable observes the flow directly, and removing it is one fewer place
  the two mechanisms could race.
- Added a tap-anywhere-on-the-background shortcut to `MainActivity`
  (`actionStartActivity<MainActivity>()` on the `Column`), so the widget isn't just a toggle - it's
  also a fast path into the full app for settings/visualizer changes.

**Verified on-device:** the label now flips immediately on tap, and the background tap opens the
app. This is the first round where the actual fix (not just a plausible-sounding one) was
confirmed working rather than handed back for another test cycle.

One open question worth tracking rather than asserting confidence on: `collectAsState` only
helps while a Glance session is alive. The `QMetronomeApp` → `updateAll()` path is still what
covers a session that's been torn down entirely (e.g. after the device has been idle a while) -
if BPM ever again feels unreactive specifically after a long idle period rather than right after
a toggle, that's the seam to look at first.

## Manual test checklist

This can't be unit-tested - a placed home-screen widget needs a real
launcher. Worth running through once on-device:

1. Long-press the home screen → Widgets → place the qMetronome widget.
   Confirm it shows the last BPM the app was set to (cold-start-from-settings
   path) and "Start".
2. Tap "Start" on the widget. Confirm the metronome actually starts (audible
   click if enabled, or open the app to confirm `isPlaying`), and the
   widget's button relabels to "Stop" promptly.
3. Open the app, change BPM with the slider/tap-tempo. Confirm the widget's
   number updates without needing to tap anything on the widget itself
   (this is the `distinctUntilChanged` collector in `QMetronomeApp`).
4. Force-stop the app from Android's app info screen, then tap "Stop"/"Start"
   on the widget directly. Confirm it still works (this is the cold-start /
   process-dead path through `ToggleMetronomeAction`).
5. Resize the widget (if the launcher supports it) and rotate the device.
   Confirm the layout doesn't clip or crash.
6. Remove and re-place the widget. Confirm it doesn't end up duplicated or
   stuck showing stale state.
7. Tap the widget's background (not the START/STOP control). Confirm it opens
   `MainActivity` rather than toggling the metronome.
8. Leave the device idle (screen off) for a while, then change BPM from
   somewhere else (the app, MIDI). Confirm the widget still picks it up after
   the idle period - this exercises the `QMetronomeApp`/`updateAll()` path
   specifically, not the `collectAsState` path step 3 already covers.

Send back whatever breaks - device/launcher-specific widget quirks are the
one thing in this feature I genuinely can't verify without a physical phone.
