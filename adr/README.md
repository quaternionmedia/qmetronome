# Architecture Decision Records — Process & Handoff

This directory is the project's decision memory. This file is the contract
for producing and maintaining it. The discipline exists because decision
documents drift in a specific, predictable way — drafts accumulate references
to their own revision history, numbers get assigned before ratification and
then need "renumbering," and supersession language leaks into documents that
were never published. The rules below make the discipline mechanical instead
of dependent on any one contributor's (or assistant's) memory.

## This project is a deliberate experiment, not a clean instance

qmetronome is the org's first mobile/cross-platform-device project. The seed
below is copied verbatim per the org's fork procedure, but this project does
**not** claim clean compliance with every org record, and says so explicitly
rather than quietly building exceptions:

- The open-license exclusion record's enforcement mechanism (SBOM-per-image,
  digest-pinning, offline mirrors, quarterly upstream scan) assumes a
  self-hosted server runtime the org operates end-to-end. A sideloaded/
  app-store-distributed mobile app has no "image" in that sense — the runtime
  is the end user's device.
- qmetronome has a hard dependency on `app/libs/glyph-matrix-sdk-2.0.aar`, a
  closed-source binary distributed by the hardware vendor (Nothing Technology
  Limited) as the *only* way to address the Glyph Matrix LED hardware on
  Nothing Phone (3) / (4a) Pro. This is not a capability gap with an upstream
  to contribute to (§3 of the org record) — there is no open project
  implementing "the Glyph Matrix protocol" to contribute to, because the
  vendor hasn't published the wire protocol, only a compiled SDK. The
  capability *is* the product; "don't have the capability" is not a live
  option.
- Android application development has a hard platform-mandated toolchain
  (Kotlin/Java, Android Gradle Plugin) with no Python-native equivalent,
  which departs from the house stack (P5) for reasons no different in kind
  from the existing client-mandated-stack carve-out — just platform-mandated
  instead of client-mandated.

These are named as open questions for the org, not resolved unilaterally
here. See [`docs/governance-perspective.md`](../docs/governance-perspective.md)
for the qmetronome-side writeup, and the corresponding perspective drafted in
the `qm` constitution repo for the org-level proposal this project's
experience is feeding. Until the org records are amended or a project-level
disposition is ratified, the relevant ADRs below are filed as `Proposed`
with an explicit `Pends on` rather than decided by stealth.

## Adopted org records

This project adopts the QM constitution by reference. Org records bind this
project; project records may tighten them, never relax them. A genuine
exception is an amendment ratified at org level.

| Corpus | Pin (tag/commit) | Records adopted |
|---|---|---|
| qm-constitution | `<pending - first mobile/cross-platform instance, see note above>` | all Accepted QM records at pin, with the open questions above pending org-level resolution |

Bumping the pin is a reviewed commit in this project.

## The one rule that prevents most drift

> **Before ratification, documents have no memory. After ratification, they
> have nothing but memory.**

- A **draft** is rewritten in place as understanding improves — squashed, as
  if the final position had been held from the beginning. Git is the
  archaeology; prose is not. A draft never says "previously," "supersedes the
  earlier stance," or "corrected in review."
- An **Accepted** ADR is append-only. Its body is never silently edited.
  Changes are dated entries under **Amendments**; reversals are a new ADR
  that **supersedes** it. Supersession is a relation between *ratified*
  documents only.

## Lifecycle

```
Draft ──▶ Proposed ──▶ Accepted ──▶ (Amended*) ──▶ Deprecated | Superseded by ADR-NNNN
  ▲           │
  └── squash ─┘   (any change before Accepted = rewrite in place)
```

| Status | Meaning |
|---|---|
| **Draft** | Being written. Numberless (`ADR-XXXX`). Rewritten freely. |
| **Proposed** | Complete; pending a named input (`Pends on`) or ratification. Numberless. |
| **Accepted** | Ratified. Number assigned. Append-only from here. |
| **Deprecated** | No longer applies; nothing replaced it. Body intact. |
| **Superseded** | Replaced by a named ADR. Body intact, header points forward. |

## Numbering

Numbers are assigned **at ratification, by the index below, in order of
acceptance** — never during drafting. Drafts reference each other by *title*.
Once assigned, a number is permanent; gaps are fine; numbers are never
reused. Project numbering is local (`ADR-NNNN`); org records are `QM-NNNN`.

## Authoring rules

1. **One decision per ADR.**
2. **Org records are constitutional** — component selections must pass the
   open-license record; gaps route through its upstream-contribution
   remediation; seams pass the replaceability test.
3. **Alternatives are written honestly** — each with the real reason it lost.
4. **Every ADR has revision triggers** — observable events, not vibes.
5. **Open questions are not decided by stealth** — undecided input → status
   Proposed with an explicit `Pends on`.
6. **External history is context; internal history is noise.**
7. **Build-time patches are registered** in the org carried-patch register
   before the patch ships.

## Drafting-session handoff (humans and AI assistants alike)

**Inputs to provide the session:** this file; the pinned org records
(minimally the open-license record); the current index; the project design
plan and any ADRs being touched.

**Session obligations:** plan first, with a contradiction check against the
org records and the index; squash continuously (the chat may discuss a
position change; the document may not); never assign numbers, never
renumber, never write supersession language into a draft; mark pending human
decisions as Proposed with `Pends on`; end by outputting drafts, the proposed
index diff, and the open-question list. Ratification — status flip, number
assignment, index update — is a **human commit** naming the record.

**Session prohibitions (verbatim-banned):** "takes the ADR-NNNN slot,"
"the set renumbers," "supersedes the stance from the earlier review/draft,"
"retroactive" framing for adoption-time rules, edits to an Accepted body
outside Amendments.

## CI enforcement

The standard scaffold (ADR lint, SBOM license gate) is org-designed around
server-runtime images and is not yet adapted for an Android APK's dependency
surface - see the open question above. What qmetronome runs today instead:
a CI check restricting which files may import `com.nothing.ketchum.*` (the
closed Glyph Matrix SDK) to `glyph/`, as the seam-boundary enforcement for
the disposition recorded in `DRAFT-glyph-matrix-sdk-dependency.md`.

## Index

| # | Title | Status | Date |
|---|---|---|---|
| | | | |

Drafts in flight (numberless, by title): Glyph Matrix SDK dependency
*(pends: org-level disposition for closed hardware-vendor SDKs)*; Android/
Kotlin platform stack *(pends: platform-mandated-stack carve-out, analogous
to the existing client-mandated one)*; MIDI clock as an open-standard seam.
