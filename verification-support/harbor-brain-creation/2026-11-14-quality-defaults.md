# Harbor Quality And Reusable Defaults

LoomAI Labs | 14 November 2026 | Additional defaults held, not qualified

## Scope

Owner approved configuration-first quality work and promoting reusable,
evidence-backed improvements to new AI Customer Experience template defaults.
Keep existing customer drafts/releases unchanged. No framework patch/release,
semantic keyword routing, answer substitution, automatic search-all or silent
model retry is authorized by this workstream.

The existing Harbor Brain, both Engage installations, optional AI-state
PostgreSQL and owned managed search cluster are reused. The external lab owns
product/enquiry records. No inventory database or copied vectors are introduced.

## Findings And Changes

- The original v5 baseline passed only R1/R5: **2/6**. R2/R3 requested attachment
  targets; R4 failed to answer concrete service restrictions; R6 missed returns.
- R6's original trace actually selected READ + RAG and searched the correct
  source. The relevant document scored approximately 0.36, below 0.5 and 0.4.
  Calling this only a source-selection defect was incorrect. Provider retrieval
  proof measured 0.358-0.364 across the actual natural/rewritten queries.
- Framework 0.8.52 already supports returned API records without a vector space.
  Platform connector `11cb0152d` removes its unnecessary vector-label requirement
  and null-unsafe conversion. Stable IDs, configured field projection, bounds,
  malformed-value checks and authorization remain. **100 focused tests passed**;
  CI `43604540677` succeeded. No framework source changed.
- Authoring guidance `1e4cf3eac` explains that supported declarative record memory,
  useful `llmFacts` projection, optional user-input semantics, omission/null body
  semantics and truthful response messages. **33 focused tests passed**;
  CI `43604574298` succeeded. One restricted Mockito run could not initialize
  attachment; the normal-permission test run passed. Initial Supabase 500 was
  transient and stopped before mutation; real customer auth subsequently worked.
- Harbor uses real returned-record memory, a source-calibrated 0.3 threshold,
  information-vs-execution guidance and accurate API tool/filter descriptions.
  A filtered inventory response is not the whole inventory. Delivery-only tools
  do not contain return/warranty terms. Those descriptions are local API-contract
  facts, not copied generic-template business behavior.

## All Intermediate Trials

These are fresh anonymous browser conversations against the real app. Each
original R1-R6 was sent once per conversation; the actual first answer is retained.
Request traces confirm the submitted question matches the labelled test. No
automatic semantic re-asks or best-answer replacement occurred.

- v6 `rel-76384db3`, threshold0.4 and record memory: trial-a **3/6**, trial-b
  **4/6**. R2 target continuity improved. R3 filtered out an unavailable comparator.
  R6 still had zero retrieved documents. Not default-qualified.
- v7 `rel-f7cc94d7`, threshold0.3 and clearer filter/ID descriptions: refined-a
  **4/6**, refined-b **5/6**. R3 still excluded the comparator. Refined-a selected
  the delivery-only tool without RAG for R6; refined-b retrieved the actual
  returns guide and answered correctly. R2 also had confusing yes/no wording in
  one run despite stating the correct GBP524 total. Not default-qualified.
- v8 `rel-611d805e`, clearer comparison example and delivery-only scope:
  scoped-a **3/6**, scoped-b **5/6**. R3 and R6 passed both times, but R2 wrongly
  treated GBP499 as meeting a GBP500 threshold. Scoped-a also asked an unnecessary
  delivery clarification, then answered that earlier question instead of R5's
  warranty question. These are quality regressions, not passing results.
- Both refined-b and scoped-b execution safety checks passed: natural enquiry
  required confirmation; Reject added no record; one Confirm created exactly one
  synthetic RECEIVED receipt; receipt lookup added none. No email or stock
  reservation was claimed in the actual receipt.

## Current Candidate

v9 `ver-47128d34` / `rel-4f279fec` adds generic answer guidance: answer the latest
question, apply numerical thresholds consistently, show a supported short total
calculation, distinguish absent records from unavailable records and avoid
unrelated missing-field padding. It also clarifies that the general delivery
policy lookup needs no product/order/postcode parameter. The prompt contains no
Harbor price, location, product ID, expected answer or phrase-matching rule.

Original-six acceptance is at least 5/6 in each of two fresh conversations, with
zero observed authorization/confirmation/side-effect regressions. Additional
threshold-boundary, unavailable-record and unused-return queries qualify more
than the original wording. The sample is small and is not a universal quality
guarantee. All earlier failures above remain part of the evidence.

**Final results: guided-a 4/6; guided-b 5/6. Do not promote.**

- Both runs passed R1, R4, R5 and R6. The actual indexed returns guide reached
  the answer, unavailable services were explained, and no warranty was invented.
- R3 failed guided-a because only one comparison record reached its answer;
  guided-b correctly compared both desks and identified the unavailable one.
- R2 failed both runs. Guided-a incorrectly gave free delivery and GBP499 total.
  Guided-b contradicted itself: claimed free eligibility while correctly adding
  GBP25 to give GBP524. Correct supporting numbers do not make that answer pass.
- Extended queries passed **2/4**: unavailable-desk lookup and unused-item returns
  passed. An exact GBP500 subtotal incorrectly received a GBP25 charge, and the
  GBP499 subtotal question asked "Which knowledge base domain should I search?"
  Neither is accepted. This is not proof that the vector source is unavailable.
- The natural enquiry clarified again instead of resolving the known desk. No
  write occurred. An explicit-ID enquiry still required confirmation, one Confirm
  created exactly one RECEIVED record, and its real receipt lookup added none.
  This final conversation did not reach Reject; that check is **not requalified
  here**. Prior scoped-b Reject passed, and confirmation configuration is unchanged.
- Native v9 verification `vrf-63a0efae` is PASSED. Both original installations are
  ACTIVE/ready on `rel-4f279fec`; the app URL and shared consumer are retained.
  Operational verification is not proof of conversational quality.

No further prompt iterations, model retry, arithmetic rewrite, domain matcher or
framework patch were added to force a pass. The remaining quality review should
inspect the exact inference inputs and compare a stronger configured model on the
same first-response tests before assuming a framework code fix is necessary.
For real commercial totals, an authoritative read-only quote API is also a valid
integration design; it must not be simulated by hidden business arithmetic in
the AI framework.

## Default Promotion Boundary

The generic 1.5.0 candidate builds from published 1.4.0, retains its consultative /
balanced / contextual profiles and bounded READ + RAG cooperation, and supplies
missing model, knowledge overview, recall and generic intent/answer guidance.
Explicit custom model, prompt, threshold and overview choices are preserved.
Security, source identities, approvals, write confirmation, other modes and
recovery/fanout policy are unchanged. **26 combined template-builder tests pass**.

Only publish/select this candidate after qualification. Use the existing
marketplace and customer starter mechanism, not a second defaults engine. Prove
fresh UI Kit inheritance and remove that configuration-only fixture. Existing
Northfield, Support and customer releases are not silently republished.

The condition was not met. The fixture is explicitly `HELD_NOT_QUALIFIED_20261114`;
TEMPLATE1.5.0 and the proposed 14 November public starter were not published or
selected. TEMPLATE1.4.0 / `public-knowledge-quality-20261113` remain selected.
They already contain the four reusable dealership-derived quality areas. No
fresh-Kit creation fixture was made for the held candidate, so none needs cleanup.

## Evidence

Raw authenticated captures, drafts, first responses, intent receipts, native
release evidence and screenshots are retained privately at
`~/.codex/private/loomai-harbor-journey-20261114/quality-defaults/`.
The earlier working location was `/private/tmp/loomai-harbor-quality-20261114/`.
Do not publish or replay consumed creation/publication/apply intents.

Authoring backend `1e4cf3eac35c1050ed9029ee39373ca257cdb72c` is now healthy on
staging and production, exact digest
`sha256:031960508f3be13e729400705c13b6cd1e01939cfc60f36a8630e7b5830e44f7`.
Image-only rollouts: staging `cldxh9zbeolbmhlzr4hpijaa`, production
`xwzhul2shu7mq3440twxa0eg`. Both assistants report enabled/ready. Final live audit
proved unchanged starter selections and the unchanged published generic1.4.0
manifest in both environments, plus the real customer's unblocked old starter,
both retained active apps and HTTP200 for the standalone app. Native verification
is **30 passed, 0 failed, 4 skipped**; the skipped checks cover the unused separate
marketplace/vectorization-runner path, not the successful document-source/index
checks. No fresh candidate Kit, extra infrastructure or framework release was made.
