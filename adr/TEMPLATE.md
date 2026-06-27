# ADR-XXXX — <Decision title: an imperative or noun phrase, one decision only>

<!--
DRAFTING RULES (delete this comment block before ratification):

1. NUMBER AT RATIFICATION, NOT BEFORE. Drafts are ADR-XXXX. The number is
   assigned by the index (README.md) at the moment Status becomes Accepted.
   Never reference other drafts by anticipated number.

2. SQUASH BEFORE RATIFICATION. A draft has no memory. If the decision changes
   while drafting, rewrite the draft as if the final position were held from
   the beginning. Git history is the archaeology; prose is not. Words banned
   in any pre-ratification document: "previously", "originally", "the earlier
   draft", "supersedes the ... stance/finding", "re-review", "corrected".

3. ONE DECISION PER ADR. If Consequences starts describing a second decision,
   split it.

4. WRITE THE ALTERNATIVES HONESTLY. Each rejected alternative gets the real
   reason it lost, strong enough that a reader could disagree.

5. DON'T DECIDE OPEN QUESTIONS BY STEALTH. If an input is genuinely
   undecided, the ADR is Proposed and says what it pends on — it does not
   pick silently.
-->

| | |
|---|---|
| **Status** | Draft \| Proposed \| Accepted \| Deprecated \| Superseded by ADR-NNNN |
| **Date** | YYYY-MM-DD (date of last status change) |
| **Pends on** | *(Proposed only)* the open question or decision this awaits |

## Context

What forces are in play: the requirement, the constraints (always including
ADR-0001 where licensing is relevant), and any external facts a future reader
needs to evaluate whether the context still holds. External history (industry
events, upstream project status) belongs here; internal drafting history does
not.

## Decision

The decision, stated in full, in the present tense ("MediaMTX is the media
router"), with the operational specifics a builder needs. Sub-clauses
numbered if they will be cited (§1, §2 …).

## Consequences

What follows from the decision — positive, negative, and obligations created
(CI gates, runbooks, contracts). Costs are stated and explicitly *accepted*,
not hidden.

## Alternatives considered

1. **<Alternative>** — why it lost.
2. **<Alternative>** — why it lost.

## Revision triggers

Observable events that force a revisit of this ADR. Every ADR has at least
one; "never" is not an answer. (Examples: upstream relicense or archive;
maintainer inactivity > N months; scale threshold crossed; a Pends-on
question resolving.)

## Amendments

*None.*

<!--
POST-RATIFICATION RULES:

- Accepted ADRs are append-only. Clarifications and §2-style events are dated
  entries under Amendments. The body above is never silently edited.
- A material reversal is a NEW ADR that supersedes this one; this ADR's
  Status becomes "Superseded by ADR-NNNN" and its body is left intact.
- Renumbering never happens. Numbers are permanent once assigned, gaps are
  acceptable, numbers are never reused.
-->
