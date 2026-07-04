# ADR-XXXX — QM Constitution Adoption Scope for a Sideloaded Mobile App

| | |
|---|---|
| **Status** | Proposed |
| **Date** | 2026-07-04 |
| **Pends on** | Org-level ratification of the QM constitution records themselves - every record in the adopted corpus is still filed `DRAFT-*` at the pinned commit, so nothing is formally `Accepted` for this project to adopt yet. This ADR fixes *this project's* disposition against that corpus regardless, so a future reader has one place to look rather than re-deriving it once org ratification lands. |

## Context

qmetronome vendors the org's constitution as a submodule at `governance/qm`, pinned
in `adr/README.md`'s "Adopted org records" table to commit `6b893e33176751c5b036a9380901397b2d77e7ba`
(that repo's `main` tip at adoption time - no tags exist there yet). `adr/README.md`
already names three gaps in prose ("this project is a deliberate experiment, not a
clean instance"), but naming a gap is not the same as deciding what to do about it for
every record in the corpus. This ADR is that decision, covering all seven records/
policies in the pinned snapshot (`records/DRAFT-decision-record-discipline.md`,
`records/DRAFT-house-stack.md`, `records/DRAFT-open-license-exclusion-and-upstream-remediation.md`,
`records/DRAFT-seams-on-standard-protocols.md`, `records/DRAFT-build-the-seam-buy-the-engines.md`,
`records/DRAFT-contribution-and-sponsorship-policy.md`, `handbook/public-by-default.md`),
plus the required baseline component audit.

The corpus is written against the org's default project shape - a self-hosted service
with a container image, a database, and a deployment pipeline. qmetronome has none of
those: it is a single Android application, sideloaded or distributed through an app
store, whose only "runtime" is the end user's own device. Several records' *literal*
content (blessed language/framework list, SBOM-per-image, offline mirrors, GitOps
deployment) has no analog here. The constitution's own stated preference (per
`records/DRAFT-decision-record-discipline.md` and the "Proposed... Pends on" mechanism)
is that a gap gets an honest, bounded disposition, not silent non-compliance and not a
strained literal reading.

## Decision

1. **Decision-record discipline - adopted in full, already in effect.** This project's
   `adr/` directory (forked from `project-seed/adr/`), its Draft→Proposed→Accepted
   lifecycle, its squash-before-ratification/append-only-after-ratification rule, and
   its "assistants draft, humans ratify" numbering discipline are already how this
   project's four prior ADRs and this one were written. No further action beyond the CI
   lint job in §4.
2. **Open-license exclusion - adopted, scoped to what a mobile app actually ships.**
   The *core* rule (every component in the shipped runtime path must carry an
   OSI-approved or FSF-free license; no source-available/field-of-use-restricted
   licenses; no waivers) applies directly to this project's Gradle dependency tree and
   any vendored non-code assets (icons, fonts, sound samples - qmetronome currently
   vendors none of the latter two; `ToneGenerator` tones are platform-generated, not
   sampled). Explicitly **out of scope**: the record's deployment/provenance language
   (SBOM-*per-image*, digest-pinned base images, offline mirrors, internal CA, restore-
   verified backups) - these assume a self-hosted server runtime this app doesn't have;
   the runtime *is* the end user's device, already sandboxed and updated by the OS/app
   store. This resolves gap #1 from `adr/README.md`'s existing "deliberate experiment"
   section with an actual disposition rather than just naming the mismatch.
3. **House stack - the mechanism is adopted, the blessed list is not.**
   `adr/DRAFT-android-kotlin-platform-stack.md` already establishes Kotlin/Jetpack
   Compose/Android Gradle Plugin, with platform APIs preferred over third-party
   libraries, as this project's equivalent of a blessed stack - that ADR is the
   project-level house-stack record; this ADR does not duplicate it, only points to it.
   The org record's own mechanism - *a new dependency category needs a linked record
   weighing it against what's already blessed* - is adopted going forward for this
   project's own stack, the same way it applies to the org's Python stack.
4. **Seams on standard protocols - adopted as doctrine, currently near-vacuous in
   practice.** qmetronome has exactly one external-protocol integration today: MIDI
   Clock, already covered by its own clean-compliance ADR
   (`adr/DRAFT-midi-clock-as-open-standard-seam.md`). It has zero networked integrations
   (no backend API, no analytics SDK, no ad network, no cloud sync) as of this writing.
   The record is adopted for when that changes (see Revision triggers) rather than
   applied to nothing today.
5. **"Build the seam, buy the engines" - not applicable, stated rather than silently
   skipped.** This doctrine assumes a control-plane architecture orchestrating selected
   engines (media routers, databases, transcoders) behind standard-protocol seams. A
   single-process mobile client app has no engines to buy and no control-plane/seam
   split in that sense - `MetronomeEngine` is an in-process state singleton, not an
   orchestrated external service. No project-level seam-instance record is created for
   this project; if qmetronome ever gains a genuine engine-orchestration shape (e.g. a
   companion backend), that would be new architecture warranting its own ADR at that
   time, not a retrofit of this one.
6. **Contribution and sponsorship policy - adopted mechanically, currently inactive.**
   The carried-patch register (`governance/qm/registers/carried-patches.md`, org-level)
   requires an entry for any build-time-applied patch against a dependency. qmetronome
   carries none today (§6, baseline audit) - nothing to register, but the obligation is
   adopted so a future patched dependency doesn't get missed. The client-engagement
   clause (contribution terms for client-billed work) doesn't apply - qmetronome isn't
   client work.
7. **Public by default (handbook policy) - adopted.** This project's repository is
   public on GitHub (`github.com/quaternionmedia/qmetronome`) with public Actions runs
   and Releases. The signing keystore and its four secrets
   (`KEYSTORE_BASE64`/`KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD`, per
   `docs/app-store-checklist.md`) are the one named, justified exception (credentials),
   consistent with the policy's own carve-outs.

## Consequences

- Gap #1 in `adr/README.md`'s "deliberate experiment" section now has a real
  disposition (§2 above) instead of just being named; gap #2 (the closed Glyph Matrix
  SDK) and gap #3 (Kotlin/Android platform mandate) remain correctly routed to their
  existing dedicated ADRs, which this record cross-references rather than restates.
- A baseline component audit (required by the open-license record at adoption) is
  recorded here rather than asserted informally. As of this ADR, `app/build.gradle.kts`'s
  full dependency list:

  | Dependency | License | Notes |
  |---|---|---|
  | AndroidX (appcompat, core-ktx, lifecycle-runtime-ktx, activity-compose, compose BOM + ui/ui-graphics/ui-tooling(-preview)/material3, glance-appwidget) | Apache-2.0 | Google |
  | Material Components (`com.google.android.material:material`) | Apache-2.0 | Google |
  | Material Icons Extended | Apache-2.0 | Google |
  | kotlinx.coroutines-android | Apache-2.0 | JetBrains |
  | JUnit 4 (test) | EPL-1.0 | OSI-approved copyleft; test-only, never ships in the app |
  | Robolectric (test) | Apache-2.0 | test-only |
  | AndroidX Test / Espresso (androidTest) | Apache-2.0 | instrumented-test-only |
  | `app/libs/glyph-matrix-sdk-2.0.aar` | Closed/proprietary | The one deliberate exception - already disposed of by `adr/DRAFT-glyph-matrix-sdk-dependency.md`, not re-litigated here |

  Every shipped (non-test) dependency clears the OSI-approved bar; the one non-compliant
  component is the Glyph Matrix SDK, already named and isolated (import boundary
  enforced in CI) by its own ADR. This table was compiled by reading
  `app/build.gradle.kts` and each library's well-established public license, not by a
  generated SBOM - §4 below adopts tooling to make this mechanically verifiable rather
  than manually asserted from here forward.
- No carried-patch register entry is needed today (§6) - revisit if any dependency is
  ever locally patched at build time.
- This ADR becomes the natural place to update if/when qmetronome gains a networked
  feature (making §4 live), a second engine-like architecture layer (revisiting §5), or
  a patched dependency (revisiting §6) - see Revision triggers.

## Alternatives considered

1. **Adopt every record literally, as written for a server project** - rejected: would
   require inventing a fictional "deployment," "image," and "control plane" for a mobile
   client app just to have something to point compliance language at, which is
   compliance theater, not compliance - the constitution's own emphasis on honest,
   bounded scope (Proposed/Pends-on rather than silent stretching) argues against this.
2. **Leave the gaps named in `adr/README.md` without a disposition ADR** - rejected:
   naming a mismatch and deciding what to do about it are different acts; per P6
   ("decisions are documented or they didn't happen"), the disposition itself needs to
   exist somewhere, not just the observation that a disposition is needed.
3. **Fold this into `docs/governance-perspective.md` instead of a new ADR** - rejected:
   that document is explicitly framed as a *perspective fed back to the org* (non-binding,
   proposing a new project class), not this project's own binding internal decision.
   They're complementary, not the same document - this ADR is the record docs/governance-
   perspective.md and adr/README.md's summary both defer to.

## Revision triggers

- Any org record in the pinned corpus is ratified (`Accepted`, numbered `QM-NNNN`) -
  re-check whether this ADR's dispositions still match the ratified text (drafts can
  still change before ratification).
- qmetronome adds any networked integration (backend API, analytics, ads, cloud sync) -
  §4 (seams on standard protocols) becomes live; evaluate the specific protocol/vendor
  against the replaceability test at that time.
- qmetronome ever vendors a build-time-patched dependency - §6's carried-patch register
  entry is no longer optional.
- The `governance/qm` submodule pin is bumped - re-verify this ADR's dispositions still
  match the new pinned text, especially if any record's scope language changed.

## Amendments

*None.*
