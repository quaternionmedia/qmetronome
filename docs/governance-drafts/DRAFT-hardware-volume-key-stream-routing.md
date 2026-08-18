# ADR-XXXX — Route hardware volume keys to the media stream

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
| **Pends on** | Nothing — ready for ratification |
| **Tracks** | issue #12 |
| **Tools** | Analysis and draft text produced with GitHub Copilot (coding agent); authored and submitted by mrharpo |

## Context

Hardware volume buttons on Android adjust whichever audio stream the activity
has declared as its volume-control target. Without an explicit declaration,
Android defaults to `STREAM_RING` (or the currently active foreground stream,
which is device- and OEM-dependent). The app's click engine — both
`ClickPlayer` (the fallback retrigger path, `engine/ClickPlayer.kt` line 81)
and `StreamingClickEngine` (the primary sample-clocked path,
`engine/StreamingClickEngine.kt` line 384) — builds `AudioTrack` instances
tagged with `AudioAttributes.USAGE_ASSISTANCE_SONIFICATION` /
`CONTENT_TYPE_SONIFICATION`, which Android maps to `STREAM_MUSIC`. The
in-app volume slider operated on `STREAM_MUSIC` directly, so it worked;
the hardware buttons did not, because the activity carried no declaration
linking them to that stream (issue #12, Samsung Galaxy Tab A11+ / Android 16
/ One UI 8.0).

Android provides `Activity.volumeControlStream` for exactly this purpose: set
it once in `onCreate` and the OS routes physical button presses to that stream
for as long as the activity is foregrounded. PR #13 adds this one-line fix.

A related gap exists for background playback: `PersistentPlaybackService`
(governed by the persistent-playback ADR) can keep the engine running after
the activity is paused or stopped. When the activity loses focus, its
`volumeControlStream` binding lapses, and hardware buttons revert to the
system default. The present decision covers only the foreground case.
Background hardware volume control is deferred and explicitly named (see
*Revision triggers*).

## Decision

1. `MainActivity.onCreate` sets `volumeControlStream = AudioManager.STREAM_MUSIC`
   before `enableEdgeToEdge()` and `setContent`. This is a one-line change in
   the activity lifecycle, not a change to the audio engine or the existing
   `AudioTrack` attribute configuration in `ClickPlayer` or
   `StreamingClickEngine`.

2. The `AudioAttributes` usage values in both click engines
   (`USAGE_ASSISTANCE_SONIFICATION`) are left unchanged. The stream mapping
   from those attributes to `STREAM_MUSIC` is correct for this use case; a
   broader reassignment to `USAGE_MEDIA` is not made here, since it would
   change audio-focus and ducking behaviour without a corresponding review.

3. A JVM unit test (`MainActivityTest`, using Robolectric) asserts
   `volumeControlStream == AudioManager.STREAM_MUSIC` after `setup()`.
   This is an activity-level property check, not a hardware-key dispatch
   simulation; the latter requires an instrumented device test and is not
   added here.

## Consequences

- Hardware volume buttons change metronome click volume while the app is
  foregrounded.
- The fix does **not** cover the background-playback path
  (`PersistentPlaybackService`). A performer who enables persistent playback
  and backgrounds the app sees hardware buttons revert to the system default
  until either the activity is foregrounded again, or the service registers
  a `MediaSession` — the standard Android mechanism for routing media keys
  from a background service.
- Cost accepted: the `MediaSession`-based background fix is deferred. It
  requires its own review of lock-screen key handling, notification metadata,
  and audio-focus policy.

## Alternatives considered

1. **Override `onKeyDown` to intercept `KEYCODE_VOLUME_UP/DOWN` and call
   `AudioManager.adjustStreamVolume` directly** — rejected: duplicates
   platform behaviour already provided correctly once the stream is declared;
   introduces manual volume-step arithmetic that will drift from platform
   defaults; suppresses the system volume overlay UI.

2. **Change `AudioAttributes.USAGE_ASSISTANCE_SONIFICATION` to
   `USAGE_MEDIA`** — rejected as the primary fix: the stream-declaration
   approach is the documented, single-responsibility mechanism; changing the
   usage attribute alters audio-focus and ducking policy as a side effect.

3. **Extend the fix to include `MediaSession` registration for background
   volume control** — rejected for this decision: correct as an eventual fix,
   but bundling it couples an activity-level one-liner to a service-level
   feature with its own lifecycle, permissions, and test surface.

## Revision triggers

- `PersistentPlaybackService` receives a `MediaSession`: the background
  volume-control gap in Consequences is resolved; amend or supersede.
- Android changes the stream mapping for `USAGE_ASSISTANCE_SONIFICATION` away
  from `STREAM_MUSIC` on a significant OEM configuration: the attribute
  selection in §2 requires re-evaluation.
- A future audio-focus review changes `AudioAttributes` usage to `USAGE_MEDIA`:
  update §2 via amendment.

## Amendments

*None.*
