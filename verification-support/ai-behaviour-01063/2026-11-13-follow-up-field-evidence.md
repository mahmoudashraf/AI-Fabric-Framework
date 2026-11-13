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
Backend image CI `43407319736` passed, building only the affected backend.
Source `ef03ec84ec1410ba99ec25dbfb7fe10d2048c1a1` is native-verified healthy
on staging and production at digest
`sha256:ac178ac35f2dd26dc0a3fad12e292207bc798a33ab8e91b5ce3d11c6d603bbf1`.
Staging's authenticated deployments API returned HTTP 200 before the production
rollout. Eight previously unexecuted private-preview turns subsequently
completed successfully through the corrected production backend. The precise
absent-score regression is covered by the focused tests; not every live turn
contained an absent score. No UI/runtime image or framework release was needed.

## Qualification Status

The initial baseline follow-up happened to answer mileage correctly. The first
candidate answer supplied both odometer and range fields; its follow-up used one
retrieved record and still made an overstrong comparison while admitting the
other current value was unavailable. These samples do not prove improved quality
over the baseline. The baseline also answered correctly in this sample, unlike
the retained earlier live miss. The original failed wrapper report and recovered
response remain unchanged.

All eight remaining planned turns were then run once. The distance-driven and
recorded-mileage follow-ups supplied 1,980 versus 4,850 miles correctly. The
range follow-up supplied 287 versus 216 miles correctly. The sparse E2 answer
kept seats, range, equipment and showroom location unknown rather than borrowing
another record's fields. These are useful observations, not a statistically
qualified improvement or proof that the original planning miss is fixed.
The candidate is therefore held, not adopted or made a template default.

## Policy Preview Access Finding

The private-preview policy turn returned no policy evidence. This is not a
missing-document or vector-threshold diagnosis: the actual search-source trace
shows `SKIPPED/ineligible`, zero eligible sources and zero searches attempted.
The approved policy source explicitly allows private backend, authenticated
public and anonymous public modes, but omits `PLATFORM_PROXY_SESSION`. Console
correctly uses that separate verified identity mode. Runtime source eligibility
correctly enforces the configured list. No framework change is required.

The proposed correction adds only the existing Console identity mode to this
source through normal draft publication and staging-first apply. It must retain
all source filters, `PUBLIC_APPROVED`, source/tenant handles, credentials and
business permissions. The publication command was rejected by approval review
before executing because it changes a persistent access policy. No draft edit,
publication or apply occurred. A dedicated owner approval question is pending.
Do not treat the prepared helper as authorization to execute it or bypass the
rejection through another command. Future plugin/template source definitions
also need this explicit decision when authoring Console-testable sources;
do not globally append the mode to every customer-defined source.

## Public Customer-Path Check

One fresh anonymous query through the actual production Engage app answered
the identical policy question correctly: valid driving licence, 30-minute test
drive, advance booking, accompanied by a representative and subject to checks.
It cited the existing active test-drive document. Exactly one chat submission,
no business action, zero page errors and no telemetry suppression were observed.
Desktop/mobile screenshots were inspected; the basic viewport check passed.
This is not full accessibility, every mobile interaction or sign-in qualification.
The public policy path therefore works; the access finding is Console-specific.

## Final State

Thirteen actual chat queries: twelve private preview turns, including the
recovered fourth completion, plus one public workspace turn. No automatic model
replay. All ten saved draft sections and draft `drf-e620ed89` are unchanged.
The same app bindings retain Northfield `ver-0458c7ed`, staging `rel-6c13942c`
and production `rel-0139e718`, both `APPLIED_VERIFIED/PASSED`, framework 0.8.52.
Runtime/UI source remains `0089818162f8805bc58578dc20149dec9a93d237`;
only the two control-plane backends changed to the diagnostic-fix image above.
No prompt, source-access configuration, S3 object/IAM permission, customer
runtime release, app identity or database changed.

Private evidence and resumable helpers are preserved at
`~/.codex/private/loomai-followup-20261113/`; the initial temporary directory
also remains. `closeout.private.json` records actual identity/configuration
preservation. `policy-preview-rollout.mjs` is prepared but **unexecuted and
approval-gated**. Richer policy uploads, accepted template defaults and the
remaining normal model-quality miss are not closed by this diagnostic fix.
