# Governance drafts — staging area

Files in this directory are ADR drafts that belong in the `governance/qm`
submodule (`adr/` directory, `project/qmetronome` branch) but are staged here
while the submodule branch is pending push and PR by a human with qm write
access.

**Migration steps (human action required):**

1. For each `DRAFT-*.md` here, copy the file to
   `governance/qm/adr/DRAFT-<slug>.md` while on a local checkout of the
   `project/qmetronome` branch.
2. Add the draft title to the "Drafts in flight" paragraph in
   `governance/qm/adr/README.md`.
3. Commit in the submodule, push the submodule branch to `quaternionmedia/qm`,
   open a PR targeting `project/qmetronome`, and update the submodule pin in
   this repo.
4. Remove the corresponding file from this directory once the submodule PR is
   merged and the pin is updated.

## Current drafts

| File | Tracks | Status |
|---|---|---|
| `DRAFT-hardware-volume-key-stream-routing.md` | issue #12 | Proposed — ready for ratification |
| `DRAFT-animation-click-trigger-positions.md` | issue #11 | Proposed — pends UX design inputs (see Pends on) |
