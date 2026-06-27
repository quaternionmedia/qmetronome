# ADR-XXXX — Android/Kotlin Platform Stack

| | |
|---|---|
| **Status** | Proposed |
| **Date** | 2026-06-27 |
| **Pends on** | Org recognition of a platform-mandated-stack carve-out alongside the existing client-mandated one (P5) |

## Context

The org's house stack (P5) is Python — FastAPI, SQLModel/Pydantic, Metaflow,
Click, Jinja2 — plus single-file HTML/JS for visualization deliverables, with
an explicit carve-out for client-mandated stacks. qmetronome is an Android
application whose UI, lifecycle, and hardware integration (Glyph Matrix,
USB/virtual MIDI) are Android-platform APIs reachable only from Kotlin/Java
on the Android Gradle Plugin toolchain. There is no Python-native path to a
real Android app binding a vendor AIDL service, registering a
`MidiDeviceService`, or rendering Jetpack Compose.

This is not a preference question the way ORM or web-framework choice is for
a server project — it is a property of the target platform, identical in
kind to the client-mandated-stack exception P5 already names, just mandated
by the device's OS rather than by a client contract.

## Decision

qmetronome is written in Kotlin against the Android SDK (Jetpack Compose,
Android Gradle Plugin, kotlinx.coroutines), with platform/AOSP APIs preferred
over third-party libraries wherever the platform provides the capability
(e.g. `android.media.midi` for MIDI, `android.media.ToneGenerator` for the
click, rather than a MIDI or audio library). The house stack governs nothing
here, by the same logic the existing carve-out already states for client
contracts: house preference governs what *we* build when we have the
choice; the platform decides when we don't.

## Consequences

- This project contributes no evidence toward or against the house stack's
  cross-project pattern-transfer benefit (P5's stated rationale) — it is
  outside that comparison by construction, not an exception eroding it.
- Test tooling follows the platform too: Robolectric (Apache-2.0) is adopted
  for unit tests that touch Android framework classes, because the
  alternative (the SDK's stub jar) throws on every call outside an
  instrumented/Robolectric environment. This is an engine selection (P4) for
  the mobile platform's equivalent problem, not a house-stack departure.
- Future QM mobile/cross-platform projects (iOS, desktop, browser) will hit
  the same shape of mandate with different specifics (Swift/Kotlin
  Multiplatform, etc.) — this ADR is one instance of a pattern worth naming
  at org level once a second instance exists.

## Alternatives considered

1. **Kotlin Multiplatform with a shared Python-adjacent core** — rejected for
   this project's scope: qmetronome targets one platform (Android) for one
   piece of exclusive hardware; multiplatform abstraction cost isn't earned
   yet. Revisit if a second target platform is ever in scope.
2. **Treat the whole project as an unexamined exception** — rejected for the
   same P6 reason as the SDK-dependency ADR: an undocumented departure is a
   decision the org doesn't possess.

## Revision triggers

- A second QM mobile/cross-platform project exists — the platform-mandated-
  stack pattern should be named at org level (a QM record amendment to P5 or
  a sibling record) rather than re-derived per project.
- A Python-native toolchain for production Android apps becomes viable
  (Chaquopy-style embedding maturing past prototype quality, or similar) —
  re-examine, though hardware/AIDL/Compose access would still likely require
  a Kotlin/Java boundary layer regardless.

## Amendments

*None.*
