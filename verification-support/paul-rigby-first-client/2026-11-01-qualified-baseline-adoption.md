# Dealership Qualified Baseline Adoption

Date: 1 November 2026.
Status: BASELINE ADOPTION COMPLETE AND LIVE. The broader conversational defect
is not fully closed. Owner approved the existing framework release/tuning,
not an unreviewed new framework patch or runtime downgrade.

## Scope

Preserve Northfield deployment `dep-3c4dbe4f`, existing production Engage app,
customer/tenant bindings, current product features, sources, auth and Desk.
Build the current maintained runtime against published AI Fabric0.8.42.
No old canary image, lexical routing, hidden query rewrite, weakened trust
boundary or unconditional search-all. Missing-source recovery remains explicit
and bounded to the configured two authorized sources.

Carry the qualified settings in normal versioned deployment configuration:
GPT-5.4-mini unchanged; source overview enabled; ITERATIVE/maxIterations2;
RAG_IF_ACTIONS_INSUFFICIENT; threshold0.4;8context documents/8,000characters;
planner12,000characters; action evidence8,000characters/10items. These are finite
operator-owned budgets, not semantic hard validators.

## Execution

- [x] Upgrade dependency/metadata defaults and qualify current consumer from
  a new empty Central-only cache, including runtime, connector, accounting and
  packaged library identity. No framework source/release change.
- [x] Implement and test generic versioned READ evidence-budget configuration,
  preserving existing no-override behavior. Publish only affected images.
- [x] Publish and apply one normal version to the existing staging target.
  Read back actual framework, prompt and effective budgets. Qualify the original
  20 scenarios, selected-context UI behavior and repeated two-car follow-ups.
- [x] If staging quality qualifies, apply the same version/image through normal
  production artifact promotion/apply/verify and update the existing app binding.
  Verify real published workspace behavior; do not substitute infrastructure
  readiness for semantic quality.
- [x] Record actual outcomes/remaining gaps, retain failed diagnostic evidence,
  and remove only disposable test resources created by this task.

## Baseline And Evidence

The previous production runtime is framework0.8.41, source63888284, digest902a5d00,
version `ver-73bc5b03`, release `rel-4163ea35`, binding revision12. Staged v7
`ver-dcdb7f61`/`rel-7b983289` removed the domain question but did not qualify
useful follow-ups and was not assigned to production.

The [root-cause record](2026-11-01-domain-routing-correction.md) explains why
restoring and qualifying the existing0.8.42/tuning baseline comes before any
new conversational-context framework work. Historical20-case scores are not
new acceptance evidence. Fresh results and exact artifact/release identities
will be appended as each step completes.

## Local Qualification

- PASS:887 current-consumer tests, zero failures/errors/skips: accounting78,
  guarded transport103, generic connector88, runtime582, vectorization core11,
  runner21 and embedding worker4. New cache began empty and used a Central-only
  mirror; no sibling framework install or old runtime image was consumed.
- PASS:20 packaged public framework libraries exclusively0.8.42; core,
  execution and curated-default packaged bytes independently match Central.
  The two private accounting libraries retain their own1.0.0 product version;
  an initial package-check harness assertion incorrectly included them in the
  framework set, then was corrected to distinguish those exact product JARs.
- PASS:126 focused Platform tests:76draft validation,50provisioning; finite
  budget types/ranges, exact environment mapping, no-override preservation and
  mode/authority isolation. Whitespace check passed. No semantic validator added.
- A normal staging connection/app draft was created for the same deployment on
  the existing shared Engage frontend: `northfield-staging.apps.loomai.pro`,
  installation `awi_pub_2a877305f6066b698c0ba3bf1dbd7455`. Its origin is added
  through the normal managed security projection. No runtime, database, vector
  service or customer was created. App is not activated yet; production binding
  remains unchanged.

## Images And Configuration

- Source commit `469b960ed65410d0908a679df9e6cbca38bfb63f` is pushed. Runtime image
  CI41302524746 and backend image CI41302524836 both passed; UI image skipped.
  Both control-plane backends are healthy/native-source verified on that commit:
  staging job `tdbb3r5wbisldrb1a2e32cfl`, production `ka2cd0nzvb4kmpr891792rij`.
- Runtime artifact `dsa-b9a896a3` is source/digest attested and promoted to
  staging only. Digest:
  `sha256:0ec69803aa91946cce0cfaa5f390499bcba095f9351b4ac6d0dd055c5bfb3e3a`.
  Its capability manifest preserves every current product capability and only
  changes the declared framework release to0.8.42.
- Normal published v8 `ver-9cd1de68` records framework0.8.42 and the reviewed
  tuning; draft validation0errors/0warnings, `reindexRequired=false`. Source,
  action, identity, Desk, document and business-routing sections were preserved.
  The normal managed origin projection includes the new staging Engage app.
  Infrastructure apply and semantic qualification are still pending.

- Live staging runtime admin readback confirms actual and configured framework
  `0.8.42`, version `ver-9cd1de68`, source `469b960ed`, both stock/document
  sources and no auth warnings. Native image matches the registered digest;
  ITERATIVE/2, planner12,000, action8,000 and10items are present. The shared
  Providers editor clones/preserves nested READ settings on ordinary saves.
  Release `rel-91feec3f` is still completing normal connector verification;
  no semantic acceptance or production assignment is claimed yet.

## Fresh Staging Qualification

Release `rel-91feec3f` completed APPLIED_VERIFIED/PASSED. Staging Engage is
normally bound to it at consumer revision2, its domain is verified, all16app
readiness checks passed and the app was activated. Public manifest resolves the
actual staging runtime. No product readiness gate was overridden.

The original20scenario corpus was exercised as17canonical live API exchanges
plus3selected-context interactions in the published Engage UI. These are fresh
results, manually checked against returned stock and approved document evidence,
not automatic grading from HTTP200 or `success=true`:

- PASS: complete six-item inventory, electric budget search, contextual lowest
  mileage, raised-budget/no-diesel result and warranty facts.
- PASS: both stock+delivery clause orders include all three electric cars and
  the25-mile/three-working-day/photo-ID policy.
- PASS, actual UI: search cards, pin Aster E1, answer selected price31,950 and
  mileage4,850 together with delivery/warranty, remove context and search stock
  under25,000 returning Morrow C2 rather than retaining the old vehicle scope.
- PARTIAL: named motorway comparison gives correct prices/mileages/body styles
  and acknowledges missing specifications, but calls the SUV larger without
  dimensional evidence. Towing/weather recommendation acknowledges absent
  towing ratings but uses AWD/name/body-style inference and calls a five-item
  retrieved subset current stock. Empty-result alternative is correctly labeled
  nonmatching but similarly describes five retrieved items as current stock.
- PARTIAL/SOURCE GAP: delivery cost is explicitly unavailable, while the
  supported delivery requirements are correctly answered. Reservation fee/duration,
  complaints SLA, opening/accessibility and finance APR/payment are unavailable
  in the approved evidence and are not invented.
- FAIL: the test-drive policy answer incorrectly turns a30-minute drive
  duration into a requirement to book30minutes ahead. The approved document
  actually says "Book a30-minute test drive in advance." No age/history is
  provided by that document. This residual synthesis error is not hidden.
- PARTIAL: informational booking query requests name/phone/contact confirmation
  but does not fully explain the email/details contract. No request was submitted.

Two additional independent fresh three-query API sessions passed search under
35,000, "of those two" lowest mileage/body style and named comparison. Both
correctly answer Morrow C2/1,980/Hatchback and2,870-mile difference. In one,
late routing uses EXPLICIT_BOUNDED_FAN_OUT_RECOVERY to exactly dealer-vehicle
and document; the returned Morrow/Aster scores0.43737/0.40965 would both fail
the previous0.5cutoff. The other uses LLM-selected dealer-vehicle. Model
readback is gpt-5.4-mini-2026-03-17; effective policy confirms every reviewed
budget and the unchanged generated allowlist. No business write occurred.

This qualifies adoption of the existing baseline for the reported regression,
not a100%semantic-quality claim or complete dealership launch acceptance.
Known sign-in/detail-link/raw-evidence UI gaps are outside this bounded change.
Conversational evidence propagation remains a future framework improvement;
the tuned retrieval baseline does not erase that diagnostic finding.
Private receipts: staging-qualified-corpus, staging-qualified-follow-up-1/2
and staging-selected-context-ui/browse-after-removal-ui under the task's ignored
private evidence directory. The actual UI additionally passed the two-car
search/follow-up and explicit mileage/body comparison. Its diagnostic snapshot
and screenshot are retained; image/card rendering is unchanged by this task.

## Production Adoption

The reviewed staging decision authorizes adoption of this baseline with the
listed residuals, not a new framework fix. The same artifact was promoted to
production, and normal apply `rel-ec276332` completed APPLIED_VERIFIED/PASSED on
the existing production runtime/connector with version `ver-9cd1de68`. Actual
runtime readback confirms framework0.8.42/source469b960ed and no auth warnings.
The existing consumer moved from revision12 to13 using normal compare-and-set
binding. Both existing ACTIVE installations (standalone and embedded) resolve
the production runtime and that release. Branding, experience pack, auth profile,
interaction policy, allowed origins, delivery mode and slug match their saved
pre-change configurations. No customer app/deployment replacement was used.
All16standalone readiness checks passed. The existing address remains:
https://northfield-console-live.apps.loomai.pro/.

## Production Quality: Improvement, Not Full Closure

Fresh production verification used17original API scenarios, an additional
three-query two-car session and a two-query alternate-pair session. Real UI
checks added four standalone questions and one embedded compound question.
Together with staging, this task submitted56AI questions, with no business write.
These bounded observations are not a reliability percentage or benchmark.

- PASS: complete inventory, budget searches, the original lowest-mileage
  follow-up, named comparisons, both stock+delivery clause orders and warranty
  facts. The independent two-car API session and real standalone UI correctly
  answer Morrow C2/1,980/Hatchback. No technical-domain question appeared.
- PASS: the actual embedded dealership demo answers all three electric cars
  plus25-mile/three-working-day/photo-ID delivery requirements; the answer
  persists when opening Max Mode. Public manifests verify the same upgraded
  assignment for embedded and standalone installations.
- FAIL: after an explicit Aster E1/Northstar S4 comparison, the next "of those
  two" question loses that pair. UI says it has no records; a fresh API session
  retrieves only unrelated Aster E2 and cannot compare. This is an ordinary
  conversational-reference failure, not an absent-source success. The initial
  comparison correctly supplied Aster4,850/SUV and Northstar8,920/Estate.
- FAIL: delivery-cost+handover-only compound wording returns no evidence for
  either part, despite the available handover policy working in other queries.
  Extraction selects document and two obligations; the merged response has
  compoundEvidence=true and no documents. The exact per-intent loss is not
  proven by that public response. Cost itself remains an actual source gap.
- Remaining limitations: earlier unsupported comparison/subset claims,
  informational booking guidance, and the staging test-drive duration error.
  Production's test-drive wording is ambiguous about duration/advance booking.
  Missing reservation/complaints/opening/finance facts remain explicit source
  gaps. Existing auth/detail/raw-evidence presentation issues were not changed
  or declared qualified by this rollout.

### Confirmed Residual Evidence-Handoff Gap

The fresh alternate-pair API failure has two history messages/324characters
in APPROVED_SNAPSHOT. Extraction selects dealer-vehicle with one valid model
call. READ planning executes no action and reports missing vehicle identifiers.
The generated retrieval query still says "the two referenced current stock
vehicles", without their names. LLM-selected stock retrieval returns only
DEMO-1006/Aster E2 at0.41133; no target hint is applied. Removing the technical
domain question and lowering the cutoff does not repair this evidence path.

Immutable0.8.42 source confirms READ planning starts each resolve call with an
empty executedActions list, and its prior-evidence prompt is built from only
this turn's reads/rejections. It receives the intent/catalogue, not the earlier
authorized conversation/target fact bundle. ANSWER_FROM_CONTEXT can still be
promoted to RAG for lack of current-turn evidence. This is the residual generic
gap identified in the earlier analysis, now reproduced on the adopted baseline.

Next bounded correction, pending separate owner review: carry approved,
budgeted conversational/target evidence and provenance through READ planning
and answer generation, and guide the model to return self-contained retrieval
queries when another read is needed. Earlier facts are not fresh availability
or new authorization. Keep tenant/conversation ownership, source authority,
action parameter trust, confirmation and explicit recovery policy unchanged.
Do not lower the cutoff again, append guessed names, match phrases, or force
search-all to conceal missing context. The compound-delivery and policy-timing
issues are separately recorded, not silently included in a new release.

Private receipts: production-qualified-corpus, production-qualified-follow-up-1,
production-qualified-alternate-pair; production-two-car-follow-up-ui,
production-alternate-pair-ui and production-embedded-compound-ui snapshots;
production-installation-preservation.private.json. Visible proof is in
output/qualified-baseline-20261101/. No new service/database/vector store was
created. The staging app uses the existing shared frontend and existing target.
All query/build/rollout processes have finished; no framework source was edited.
