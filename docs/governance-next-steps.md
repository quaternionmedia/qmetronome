# Governance next steps

Tracks what's left after the governance-integration pass (v0.0.22) and the
constitution-broadening pass that followed it (generalizing the house-stack
carve-out, the two-path license-gate doctrine, the perspectives response
index, and centralizing project ADRs as branches of `governance/qm` rather
than copies into each project's repo — see
[`governance-perspective.md`](governance-perspective.md) and
`governance/qm/perspectives/claude-sonnet-5-2026-07-04-qmetronome-onramp-retrospective.md`
for the full background). Organized by what kind of attention each item
needs, across both repos this work touched.

## Review

- **`qm`: perspectives status table is all "Unreviewed."**
  `governance/qm/perspectives/README.md` lists all 5 files at the default
  status. Worth a maintainer pass — in particular, the onramp retrospective
  could reasonably move to **Responded** now that its proposals are actually
  implemented. Status-setting is deliberately a human-only action in that
  mechanism's own design, so this wasn't done automatically.
- **`qmetronome`: second look at the ADR-centralization merge.** Reviewed
  together already, but it's a real structural change (the `governance/qm`
  submodule now tracks a branch instead of a pinned commit) — worth a look
  at the GitHub compare view at your own pace.
- [x] **`qm`: branch cleanup — done.** `perspective/qmetronome-onramp-
      retrospective`, `broaden/onramp-proposals`, and `polish/vocab-and-
      wording` were fully merged into `main` and deleted, local and remote.
      `main` and `project/qmetronome` are the only branches left.
- [x] **`qm`: `README.md`'s "the seed is proven" line — fixed.** Now
      distinguishes the two reference instances: streaming-infrastructure
      for the server/container runtime shape, `project/qmetronome` for the
      branch-per-project ADR model.

## Audit

- **`qm`: nothing is ratified yet.** All 6 org records and qmetronome's 5
  project ADRs are still `Proposed`/`Draft`. Not urgent, but worth an audit
  pass before a first ratification round — number assignment is explicitly a
  human action.
- [x] **`qmetronome`: fresh-clone submodule behavior — verified.** A genuine
      `git clone --recurse-submodules` correctly checks out `governance/qm`
      at the right commit with all 7 `adr/` files present, and no stray
      local `adr/` directory. One nuance worth knowing: the submodule lands
      in **detached HEAD** at that commit rather than a named
      `project/qmetronome` checkout — standard git behavior (`branch =` in
      `.gitmodules` only affects `git submodule update --remote`, not a
      plain clone), not a misconfiguration.
- **`qmetronome`: public-repo claim unverified by me.**
  `governance/qm/adr/DRAFT-constitution-adoption-scope.md` §7 asserts the
  repo is public on GitHub with public Actions/Releases — no `gh` auth
  available in-session to independently confirm that's still accurate.
- [x] **`qm`: banned-vocabulary word lists reconciled.** `TEMPLATE.md` (root,
      `project-seed/adr/`, and `streaming-infrastructure/adr/`) and
      `records/DRAFT-decision-record-discipline.md`'s Consequences bullet
      now list the same set the CI lint (`project-seed/ci/adr-lint.yml`)
      actually enforces, instead of two narrower prose paraphrases.

## Additions

- **`qm`: fully backfill `streaming-infrastructure/`** onto the branch-based
  model, or at least give it a real CI file. Its `adr/README.md` CI-
  enforcement section now honestly says the lint isn't wired into this
  reference instance yet (fixed - it previously claimed it was), but the
  actual workflow file still isn't there.
- **`qm`/`qmetronome`: an actual license-gate CI job.** The doctrine now
  names the two-path pattern (SBOM-per-image or a generated dependency-
  manifest-plus-allowlist report), but no project — including qmetronome
  itself — has one wired into CI yet. qmetronome's own dependency table is
  still hand-typed. Needs a tool-choice nod before wiring in (e.g. the
  `com.github.jk1.dependency-license-report` Gradle plugin) since it's a new
  build-time dependency.
- **`qmetronome`: branch protection on `main`** — still explicitly deferred
  to you; not something to configure via a guessed ruleset.
- **`qmetronome`: `docs/publication_checklist.md`'s Final Manual
  Verification section** — roughly 15 unchecked items need a physical
  Nothing Phone (3)/(4a) Pro. Yours to run; on-device testing stays outside
  what I do here.
