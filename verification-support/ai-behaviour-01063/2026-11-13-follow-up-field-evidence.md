# Follow-Up Evidence And Measurement Quality

## Scope

Continue the owner's requested quality work using the existing model, runtime
and configuration controls. The mileage/range confusion is worth addressing
because a comparison of different measurements is materially misleading.
Do not add query-term rules, rewrite model answers, force every source, relax
authorization or hide failed observations. Relationship work stays deferred.

## Retained Failure

The production 0.8.52 follow-up selected `ANSWER_FROM_CONTEXT` and executed no
new READ. It did attempt authorized vector retrieval, but received no results.
Its self-contained retrieval query did not name the referenced vehicles. The
answer then relabelled electric range as mileage. This clarifies earlier short
summaries: zero documents does not mean no retrieval attempt occurred.

Three small, domain-neutral prompt additions are being evaluated: resolve
follow-up subjects in retrieval questions, keep a requested field distinct
from other measurements with the same units, and preserve per-item unknowns.
No dealership phrase detector or framework change is part of this candidate.
Existing behaviour profiles and all execution/ownership boundaries are retained.

## Preview Diagnostic Fix

The initial private preview run completed four model queries, although its
wrapper returned only three responses successfully. The fourth answer was
recovered from the canonical saved conversation, not generated again.
Its final source reference had no optional score. `collectDocuments()` used a
numeric conditional that unboxed null, causing `NullPointerException` after
the runtime had completed. The error surfaced as HTTP 401; ordinary authorized
GETs still worked. This was not a reason to broaden access or retry inference.

The bounded Platform correction keeps an absent score null and maps canonical
`documentId`, `sourceId` and top-level `vectorSpace` to their existing trace
fields. It preserves the actual answer, source references and supplied scores,
including zero. It neither fills a missing model fact nor changes source choice.

All 41 focused `DeploymentPocChatServiceTest` cases passed. The new regression
covers missing/null/non-numeric scores, explicit zero, supplied similarity,
numeric-score precedence, distinct source references and exactly one query.
No framework/runtime image or database change is required for this trace fix.
Hosted backend rollout and the remaining fixed preview cases are not yet closed.

## Qualification Status

The initial baseline follow-up happened to answer mileage correctly. The first
candidate answer supplied both odometer and range fields; its follow-up used one
retrieved record and still made an overstrong comparison while admitting the
other current value was unavailable. These samples do not yet prove improved
quality. Keep the original run and recovered response. Continue only the
previously unexecuted cases after the diagnostic fix, with no automatic replay.

Private evidence: `/private/tmp/loomai-followup-20261113/`. No customer draft,
release, app assignment, model, policy document or live prompt was changed in
this initial preview phase. Richer policy uploads and final template adoption
remain separate outstanding work.
