# AGENTS.md

This project is governed by the Quaternion Media constitution, vendored at
`governance/qm` (a submodule pinned to this project's `project/qmetronome`
branch of that repo). If you are an AI coding agent opening this repo with
no other briefing, read this file fully before your first commit or edit.

## Before you do anything

**Establish four facts about this session before you write anything**, by
asking the repository rather than inheriting a previous session's belief.
Each says what it needs.

- **A. The commit you are working against**, and the branch. `git log -1` and
  `git status` answer it, and need nothing but the clone.
- **B. Which pull requests you already hold open, and how they stack** — small,
  atomic pull requests, a dependent one stacked on its parent as a draft:
  `python governance/qm/project-seed/ci/check_one_pr.py --repo <owner/name>`.
  It asks the host, so it needs a signed-in `gh`; without one it says
  `gh auth login` and stops, and with no account the answer is none.
- **C. What else is in flight in this clone** — a dirty tree you did not dirty,
  a sibling branch, an unpushed commit. `git status` and `git branch -vv`
  answer it. Other sessions are likely running right now, in other
  repositories, for the same reviewer;
  `governance/qm/handbook/async-contract.md` is the short set of rules for
  working alongside them.
- **D. Which gates exist**, and what each cannot see:
  `python governance/qm/project-seed/ci/run_workflows_locally.py`, which
  needs pyyaml, a signed-in `gh` and an `origin` to run every step.

Those are the invariants. **How** you gather them is yours to choose — read the
repository, run the scripts above, or use an adapter if one exists for your
tooling. `governance/qm/adapters/` holds any that do, each named for the product
it targets and none of them required. This file names no vendor, and neither
should anything you add to it.

**Read the corpus's committed status documents before re-deriving what they
hold.** `governance/qm/status/harness.yaml` and
`governance/qm/status/governance.yaml` each carry their own refresh command and
staleness budget in a `reading:` block inside the file. Check the age before
quoting a figure.

1. Read `governance/qm/PRINCIPLES.md` in full and the opening points of `governance/qm/README.md`. For namespaces and precedence, see `governance/qm/docs/ref/namespaces.md` and `governance/qm/docs/ref/precedence.md`.
2. This project's own decision records live in `governance/qm/adr/` — inside
   the submodule, on this project's own branch, not at this repo's root — as
   `ADR-NNNN` (numbered locally, at ratification) or `DRAFT-*.md` before
   ratification. A human ratifies; you draft.
3. **Everything you produce arrives as a pull request, and the pull request is
   an audit record rather than a request for anyone's attention.** Work on a
   branch and open a PR — in this repo, and in the `governance/qm` submodule
   when you touch this project's records there — then **merge it yourself once
   every gate is green.** Your job is a default branch that is clean and
   working, entered through a pull request so the gates ran and the diff stays
   readable afterwards. That route is a maintainer's. An outsider with this
   project's records to change opens an issue or a pull request here, and a
   maintainer pushes the project's `project/qmetronome` branch in the corpus on
   their behalf. An outside tester's pull request is merged by its assignee,
   a maintainer. **Never push
   a shared branch directly**: that is the one act that destroys the audit
   record.
   **The default branch is not a claim, so merging into it is not a release.**
   Per `governance/qm/records/DRAFT-version-tags-are-claims.md` §4, the default
   branch, a pull request and a local build are all drafts — they may be
   perfectly good and they assert nothing. **The two human gates are
   ratification, for what a record says, and the version tag, for what this
   project ships.** A `v*` tag asserts a human reviewed the change set, a human
   manually tested it against its real runtime, and deterministic automated
   validation passed. Keeping the default branch clean is what makes cutting
   one cheap.
   **Never request a review**, and add the person who asked for the work as
   **assignee**. Reviewers are named at the tag, by the human cutting it. A
   review request pulls a second person into work that asserts nothing yet, and
   against a branch carrying a live `CODEOWNERS` it fires the moment the PR
   opens — you name no one, and the notification cannot be recalled.
   **Draft means unfinished, or stacked on an open PR, and nothing else.** It
   is not a holding pen for finished work: a green PR left in draft against
   the default branch is a change that never landed.
   **One change per PR; parallel when independent, stacked when dependent.**
   Two changes that could land alone are two PRs, each ready against its base.
   A PR that needs another's work is cut from that branch, targets it as its
   base, and stays a draft until the one beneath it merges, however finished
   it is. When it does, retarget the next PR onto the target *before*
   deleting the merged branch — deleting it first closes every PR based on
   it. Never close a PR in favour of one that contains it
   (`governance/qm/handbook/async-contract.md` §1).
   `.github/workflows/one-pr-check.yml` refuses a ready stacked PR; run
   `governance/qm/project-seed/ci/check_one_pr.py` before you open anything.
   One active stack at a time is the habit to aim for, never a limit.
   **The pull request body speaks as the contributor, to the world.** It is
   posted under a human's account and addresses nobody — a question or a
   handling instruction for the person who asked belongs in the session,
   never in the body (`governance/qm/handbook/async-contract.md` §3;
   `check_pr_voice.py` refuses the second person).
4. **Human-only contributorship applies to every commit you make here** (see
   `governance/qm/records/DRAFT-human-only-contributorship.md`): do not add
   yourself, your model name, or any co-author trailer naming an unmonitored
   address (e.g. a vendor `noreply@` address) to any commit. If your default
   tooling normally appends a `Co-Authored-By:` trailer, suppress it for
   this repo. Tool involvement is disclosed as a `Tools:` note where the
   artifact calls for one, never as a byline.
5. Follow the drafting-session handoff contract in
   `governance/qm/adr/README.md` before writing or amending any record.
6. A QM record may be tightened by this project's own records, never
   relaxed — see `governance/qm/docs/ref/precedence.md`.
7. **Put explanation in one place**, per
   `governance/qm/handbook/style-guide.md`: inline comments carry clarifying
   facts about the code, `README.md` is a shallow onramp to the docs, `docs/`
   is reference, and **every why goes to a retrospective in
   `governance/qm/perspectives/`**. A record's Context and Alternatives are
   the one exception, answering *why this decision* rather than *why it went
   that way*.
8. Banned in any pre-ratification `DRAFT-*.md` record: "previously",
   "originally", "earlier draft", "re-review", "renumber", "retroactive",
   "supersedes the ... (stance|finding)", "corrected". Drafts are rewritten
   in place, not narrated. The ADR lint enforces this over prose only, so
   quoting the list in a code span is fine.
9. **Establish a fact before asserting it, and check a signal before reading
   it.** A claim that something is broken, unsupported or behaves a certain way
   carries the command you ran and what it returned. Before reporting what a
   result means, name one other thing that would produce the same output — a
   tool version, a flag's semantics, stale local state, the working directory,
   a substring matching prose. An unexpected uniform result is a tooling fault
   until shown otherwise, and a check that has only ever been seen green has
   not been tested: break the thing it names and watch it go red.
10. **A claim about what facts *mean* names what else could produce them.**
    This is the sibling of the rule above and catches a different failure: the
    facts are all true and the sentence built from them is wrong. Name the
    ordinary cause before the interesting one — same author, same source, same
    tooling, same period — and state direction and date, because "A resembles
    B" is symmetric and the useful version rarely is. **A correction carries
    the same burden as the claim it replaces**: an overclaim gets caught by a
    reader who knows better, while a deflation reads as rigour, closes the
    topic, and can quietly delete something real. See
    `governance/qm/records/DRAFT-decision-record-discipline.md` §7 and §8.
11. **The scaffolding you measure with is part of the measurement.** Item 9 is
    the tool answering a different question than you asked. This is the tool
    being fine and the setup not — nothing errors, and the result describes your
    own scaffolding rather than the subject. Its shapes: a diff against files a
    redirect never wrote; a working tree read after a merge that exited
    non-zero; file copies written through a text API that converts every line
    ending, so the diff is all encoding; a mutation test whose baseline is
    already failing, so it proves nothing either way. **Prefer the artefact you
    did not create** — read a document's own answer instead of recomputing one —
    and assert the intermediate: non-empty, exit zero, baseline green.
12. **A guard is not finished until someone has tried to route around it.**
    Breaking it and watching it go red proves it fires on the case you thought
    of; it cannot find the case you did not. Ask for a pass whose brief is to
    satisfy the check while doing the thing it forbids. A guard with a hole is
    worse than no guard — it is a green check standing exactly where a reader
    believes something is enforced. See the same record's §9 and §10.

13. **Show it by running it** — `show-it-by-running-it` of the charter, with
    `governance/qm/records/DRAFT-one-executable-walkthrough.md` as the record.
    This project's `walkthrough/` is one ordered set of pages that the ordinary
    test command executes: `walkthrough/NN-<slug>.md`, run by pytest with
    `--doctest-glob=*.md`. The example a reader reads is the example that ran.
    Do not write a second copy of a behaviour beside the code — no prose example
    that is not executed, no screenshot that is not a byproduct of a test
    asserting what the code did. What text cannot hold is emitted by that test
    and **recorded, never compared**: a test that diffs images fails on a font
    and gets switched off. Regeneration rides the command you already run before
    a pull request, so drift shows up as an uncommitted diff rather than as
    staleness nobody sees. A skip is not a pass, and a page that always skips is
    deleted.
14. **The workstation, the agent and the conversation are not the
    organisation** — record
    `governance/qm/records/DRAFT-what-is-not-the-organisation.md`, with
    `governance/qm/handbook/what-is-not-the-organisation.md` as the thing to
    do. A committed file states what is true of this project: not where a
    clone sits on one disk, which editor held it, what the tool driving a
    session did with its shell, or what anybody said. A decision enters as a
    decision, never as reported speech; a commit message describes the change
    and not the conversation behind it. The seed's `leak-check.yml` runs
    `check_leaks.py` on the mechanical part — a home path, a personal folder,
    a scratch path, a shared link; the rest is yours to read for before you
    push.
15. **Work is delivered into a repository, never to a hosted page** — record
    `governance/qm/records/DRAFT-work-is-delivered-into-a-repository.md`. A
    report, a demo, a preview or a handoff is a committed file, and reaches a
    reviewer as a pull request or as a page the reviewer builds and serves
    locally. Nothing goes to a page an assistant's host keeps outside the
    org's repositories, such as a chat product's shareable artifact or canvas:
    it is not versioned with the code, cannot be reviewed as a diff, and goes
    stale unseen. `check_leaks.py` refuses a committed link to one.
16. **A shared allowance is drawn on politely** — record
    `governance/qm/records/DRAFT-a-shared-allowance-is-drawn-on-politely.md`.
    Before many calls to one service, read what remains of its allowance and
    state the count; past a tenth, ask first. Sample for an estimate, keep each
    response as it arrives, send a few at a time, and stop at the first refusal
    that names a limit, saying when it resets. The allowance belongs to every
    caller on the credential.
17. **Check what your branch carries before you open the pull request.**
    `python governance/qm/project-seed/ci/check_pr_base.py --base <base> --head
    <branch>` reports the merge-base, the commit and file counts, the authors,
    and any commit that also lives on another branch; paste its output into the
    description. A branch cut from the wrong parent passes every other check,
    because tests and lint measure the branch and not where it came from. It
    refuses the directions the namespaces forbid, such as a `project/qmetronome`
    head aimed at the default branch.
18. **Run the CI locally before you call a pull request ready.**
    `python governance/qm/project-seed/ci/run_workflows_locally.py` executes the
    workflows' actual steps; reading a workflow and running the commands you
    think it contains is not the same thing. Report what you ran and what it
    said, including the steps the runner cannot reproduce, and say whether a
    local failure is a defect or a difference in the environment.
19. **Durable text carries as few integers as it can** — record
    `governance/qm/records/DRAFT-few-integers-in-durable-text.md`. A number in
    prose is a claim with an expiry date that the prose does not carry. Prefer
    the relation to the count ("every service", not a total), and where a
    figure is the point, name the command and the commit that produced it.
    Never restate a figure a generated document already holds. A verification
    section is the one place a bare count belongs. An assertion in code that goes
    stale fails, which is the property prose lacks.
20. **This page is read first, so it restates decisions it does not own** —
    record `governance/qm/records/DRAFT-the-read-document-governs.md`. Where a
    passage here summarizes a record, it names the record's path. When the
    summary and the record disagree, the record is what was decided and the
    summary is repaired, never the other way round.
21. **Durable text states what is true now, for a reader who was not there** —
    record `governance/qm/records/DRAFT-durable-text-states-what-is-true.md`. A
    docstring, a page or a record says what the thing is, what it does, what it
    cannot do and what to do with it, in the present tense. It does not tell how
    it came to be: what it replaced, what was found, when or by whom. The
    commit, the pull request and the retrospective hold that. The seed's
    `timeless-check.yml` runs `check_timeless.py` on the lines a pull request
    adds and refuses a phrase set in capitals and a phrase that only narrates.

## The instruction files

`CLAUDE.md` is a regular file holding the one line `@AGENTS.md`: an import
the tool that reads it expands into this page, on every platform, with no
setup. This file is the one every tool reads, and no other instruction file
is carried.
`governance/qm/records/DRAFT-ide-integrated-governance-discovery.md` is the
decision behind the file, and says when a project adds a
`.github/copilot-instructions.md`.

<!-- Project-specific setup commands, test commands, and conventions belong
     below this line. Above it, the governance section of
     governance/qm/project-seed/ide/AGENTS.md stays verbatim apart from the
     project's name. -->

## Building and testing

```sh
./gradlew assembleDebug testDebugUnitTest --console=plain
```

Tests are the source of truth for the user guide and screenshots — see
`CONTRIBUTING.md`'s "Test coverage" section before adding or renaming one.
Every `Test` task carries a project-default timeout, per
`governance/qm/adr/DRAFT-enforced-test-timeouts.md`: a hung test fails rather
than being watched.
