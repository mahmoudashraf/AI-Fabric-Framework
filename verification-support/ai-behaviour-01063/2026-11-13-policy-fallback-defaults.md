# Policy Evidence, Answer Integrity And Template Defaults

Status: in progress, 13 November 2026. This is a new qualification cycle, not a
replacement for the earlier failed observations or historical release records.

## Scope And Order

1. Establish the actual approved policy evidence and source permissions.
2. Remove the audited answer-substitution paths, preserving action receipts and
   separating failed narration from successful execution.
3. Qualify reusable dealership settings before publishing new template defaults.

Independent widget verification and source-only template preparation can proceed
while external storage access and the proposed framework patch await review.
No relationship-module change, web search or UI redesign is included.

## Policy Baseline

Fresh source previews confirm the same three original short policies on both
Northfield targets, all active version 1. Their sizes are 290, 298 and 327 bytes.
The richer fictional pack in
`product-demos/autotrader-dealership-demo/backend/deployment/customer-storage-policy-pack/`
has **not** been uploaded or indexed in this cycle.

Four actual staging queries used the public installation manifest and canonical
conversation route. Transport passed, but that is not semantic acceptance:

- Delivery: the answer did not supply the requested costs or handover details.
  Fees and insurance are absent from the old source; some ID/payment information
  exists but was not successfully incorporated.
- Test drive: duration, advance request, licence and unconfirmed appointment
  boundaries were represented without inventing the missing age requirement.
- Compound stock/policy: the two qualifying electric cars and prices were
  correct, but the answer incorrectly treated 40-mile delivery as unavailable.
  The richer approved pack instead explicitly describes a quoted 26–75-mile band.
- Warranty: engine/gearbox coverage was distinguished from unconfirmed battery
  coverage and battery health.

The managed-storage provisioner's identity was verified, but its GetObject
request to this existing canary bucket was denied. That is **not** proof of
PutObject permission or denial. Runtime reader access remains working. Required
for production found no current approved uploader and prepared an exact
six-object HTTPS-only PutObject policy for owner review. No reader/provisioner
permission was broadened, no IAM identity was created and no S3 object changed.

## Answer Integrity

The widget query path no longer substitutes a generated product-count narrative
for a supplied answer. Product cards remain a separate structured presentation.
Absent query narration remains absent rather than becoming a fabricated success
message. Local coverage exercises authored answers, uncertainty, missing answers,
failed/compound turns, unchanged product cards and exactly one query request.

The three framework findings remain reachable in source 0.8.51:

- READ evidence can become a successful answer after failed/empty generation.
- Forced READ post-action generation can substitute a deterministic summary and
  suppress messages by phrase; the generic path has no explicit recovery grant.
- RAG can substitute a fixed response after generation failure and attempt an
  unconfigured second generation call with a narrower context.

A bounded framework correction has been proposed for owner approval, not yet
implemented or released. Successful writes must retain their actual receipt and
success status if only narration fails; no write is replayed to repair wording.
Relationship-specific behavior remains deferred.

The widget's separate confirmation/clarification/history callers still have
legacy missing-message defaults. This query-path patch is not a claim that every
UI fallback has been removed or that every empty error envelope has a detailed
visible rendering.

## Template Qualification

The live Northfield profile is Consultative / Advisory / Balanced / Contextual.
Global defaults remain Professional / Grounded / Balanced / Off. Current public
dealership templates do not embed those named behaviour selections consistently.

The new fixed three-turn scenario checks stock, a mileage/advice follow-up and a
sparse vehicle record. Each target receives one observed sequence; no failed
query is rephrased or retried to obtain a pass.

Initial staging observation: stock facts and the sparse E2 boundary were useful,
but the follow-up mislabeled electric range as mileage. This prevents claiming
the current settings are fully qualified template defaults. Preparing a reusable
overlay is not permission to mark it accepted or silently change live customers.

Production's same sequence supplied the correct odometer values (1,980 and
4,850 miles) and preserved the sparse E2 boundary. Its stock answer unnecessarily
introduced hybrid alternatives and used imprecise budget wording; suitability
advice still relies partly on body style rather than supplied comfort evidence.
These observations are retained, not treated as a controlled before/after test
or perfect semantic acceptance. Both targets still serve the existing framework
0.8.46 releases and unchanged settings; updating the control-plane default to
0.8.51 did not automatically upgrade customer runtimes.

The ten real queries in this cycle are four staging policy-baseline queries and
three defaults queries per target. Their HTTP success is not a quality score.
The three-turn template scenario is reusable at
`dealership-defaults.scenarios.json` in this directory.

Retained staging diagnostics explain the mileage miss more precisely: the READ
planner chose `ANSWER_FROM_CONTEXT`, executed zero actions and returned zero RAG
documents. Generation used `RAG_NO_CONTEXT` with two history messages; the first
answer contained range but not odometer values. It then confused that range with
mileage. This is a model/source-choice and field-attribution error, not proof of
a broken inventory mapping or one of the audited provider-failure fallbacks. The
proposed fallback patch must not be advertised as automatically fixing it. A
later prompt-quality pass can guide the model to fetch missing comparison facts
or state their absence; no keyword routing or compulsory lookup is added here.

Versioned template candidates must contain only declared, nonsecret defaults.
They must preserve security, connection inputs, required plugin references,
other orchestration modes and historical manifests. Routine template sync must
not reapply defaults over customer edits. Model/provider changes and new customer
storage are outside the bootstrap-default overlay.

The offline builder in `product-templates/dealership-experience/` has 13 passing
test groups. All three current published manifests were processed privately with
the actual live behaviour catalogue. Their security, required references and
installation defaults remained unchanged. The native compose API accepted the
common managed profile with exact identity/instruction round-trip; no draft was
saved and no model was called. Proposed versions are simulator 1.15.0, external
read-only 1.5.0 and external Desk 1.11.0. **None is published or activated.**

Existing backend tests passed: 19 template-sync and 10 behaviour-profile tests,
with zero failures/skips. These are configuration-contract checks, not a model
quality pass or a fresh customer installation acceptance claim.

## Private Evidence

Raw exchanges, manifests, drafts and exact scoped receipts remain outside Git:
`~/.codex/private/loomai-optimisation-20261113/`.

Relevant subdirectories are `policy-baseline-staging/`, `defaults-staging/` and
`defaults-production/`. These runs perform no successful business write and no
automatic vector copying.

## Widget Hosted Closeout

Source `7d468d5f6e5ff829c58f260492f8745e7e9b6582` is committed/pushed. Core-image
CI `43363826271` passed. Both backends and both Console UIs are native-image and
source verified healthy at that exact source:

- Backend digest:
  `sha256:26b9686416f6b70af5ca3db2e58542e7c5891e41e5880287edd30e1e841eec1a`.
- UI digest:
  `sha256:f7fcc9468da9dcb843b7f80753beca5bbb4d7b9e030b0917b40e3c90ae3a7780`.
- Actual hosted workspace asset SHA-256:
  `f9dc5c5a96c7c9bfcbb6ab378539bed33d107e558dd8349b1bc4872576841018`;
  manifest integrity and fetched bytes match. Existing experience-pack bytes
  remain unchanged.

One real stock query ran in each app. Supplied answers and existing vehicle
cards rendered. The staging stock answer again borrowed the E1 town for E2;
production left E2's absent location unasserted. That answer-quality issue is
retained, not fixed by presentation code. Including these two UI queries, this
cycle observed twelve real model queries, with zero business writes.

Browser contract replay on each hosted app separately verified a supplied
answer with uncertainty alongside a product card, a data-only response without
fabricated success narration, and an explicit failed-generation answer with
its structured card retained. Exact prose, cards, asset identity, one request per
case, desktop/mobile presentation and zero captured page errors passed. The
synthetic product cases are labelled **UI contract replay**, not real Shopify
integration or model-quality evidence. Screenshots were inspected.

The initial staging checker failed because its document-level selector did not
pierce the widget shadow root. The failed run and original real response are
retained. Corrected locator checks replayed that same response without another
AI call. A further screenshot-only replay scrolled to the actual message;
no model response was rewritten, rephrased or retried to obtain a pass.

Local TypeScript/build, narrative, suggestions/races/lifecycle, source resolution,
compound results, action presentation, confirmation replay and authentication
checks passed. No app assignment, runtime image, customer draft or template was
changed. Customer runtime framework remains 0.8.46; the unchanged control-plane
default remains 0.8.51. The framework patch, policy replacement and final template
adoption remain open as described above; this is **not** full optimisation closure.
