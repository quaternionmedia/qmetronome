# Governance perspective: this project as a mobile/cross-platform experiment

qmetronome is Quaternion Media's first mobile/device-hardware project. The
org's decision-record discipline (`adr/`) and constitution apply by adoption,
but two of the constitution's enforcement mechanisms were built around
self-hosted server infrastructure and don't transfer cleanly to a sideloaded
Android app built against a closed hardware-vendor SDK. Rather than quietly
building exceptions into this project's docs to paper over the mismatch, the
gaps are named here and fed back to the org as an open question, per the
constitution's own "decisions are documented or they didn't happen" rule
(P6) and its drafts-vs-perspectives separation.

## The two gaps, briefly

1. **The open-license exclusion record's remediation path (§3: contribute
   upstream) assumes an upstream exists.** qmetronome's Glyph Matrix support
   depends on a closed-source vendor SDK gating a specific piece of hardware
   that the vendor has not documented at the protocol level — there is no
   open project to contribute to. See
   [`adr/DRAFT-glyph-matrix-sdk-dependency.md`](../adr/DRAFT-glyph-matrix-sdk-dependency.md)
   for the full disposition: isolation behind a checkable import boundary as
   the mitigant, named and bounded rather than hidden.
2. **The house stack (P5) is Python; Android development is not.** This is a
   platform mandate, not a preference, and is structurally identical to the
   client-mandated-stack carve-out the org already recognizes — see
   [`adr/DRAFT-android-kotlin-platform-stack.md`](../adr/DRAFT-android-kotlin-platform-stack.md).

For balance: the MIDI clock work is a case where the existing doctrine fits
perfectly with zero exceptions needed (open protocol, platform API, zero new
dependencies) — see
[`adr/DRAFT-midi-clock-as-open-standard-seam.md`](../adr/DRAFT-midi-clock-as-open-standard-seam.md).
The gaps are specific to hardware-vendor integration and platform-mandated
language choice, not a general mismatch between this project and the org's
principles.

## What this is feeding

These three drafts, plus this document, are the qmetronome-side half of a
perspective drafted in the `qm` constitution repo proposing that mobile/
cross-platform device projects be recognized as a distinct project class
with their own ADR-0001-equivalent (addressing distribution surface and
closed hardware/platform SDKs in place of SBOM/digest-pinning/offline
mirrors). That perspective is non-binding by the constitution's own rules —
assistants draft, humans ratify — and lives in the org repo, not here.

## Turning the isolation claim into enforcement, not just an assertion

The Glyph SDK ADR's mitigant is an import boundary (`com.nothing.ketchum.*`
confined to `glyph/`), checkable today by a one-line grep. Wiring that into
CI as an actual gate - the same "teeth" pattern the org's SBOM gate uses,
pointed at a boundary a server-image scanner can't see - is tracked as
repo-setup work, not asserted here as already done.
