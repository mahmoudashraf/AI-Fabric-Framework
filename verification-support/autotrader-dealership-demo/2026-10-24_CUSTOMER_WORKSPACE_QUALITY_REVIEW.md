# Dealership Product Quality Review

Started: 24 October 2026. Latest review: 25 October 2026.
Status: **NEEDS IMPROVEMENT**.

## 25 October: Post-Separation Recheck

The separation task reported its final hosted customer journey and cleanup
complete. Main independently confirmed both control planes healthy on backend
`c840f7a58e64651f1fe4b8d0b2d06a9a8187d736`, both existing quality services
healthy, and both published browser assets still matching their SRI hashes.
The owner's pause-until-separation condition is satisfied.

Without changing settings, images, sources or assignments, main reran an
11-question subset through the published widget and live anonymous runtime
routes. The run completed on 25 October at 00:35:51 BST: **6 PASS, 1 PARTIAL,
4 FAIL** after manual answer/evidence review. All responses were HTTP200;
there were no browser errors, blocked requests or successful business writes.
This subset is not another full20 and its counts must not be compared as an
overall improvement or regression against the full20.

- PASS: current electric stock, warranty, both compound stock/delivery clause
  orders, real vehicle-card selection and broad browsing after context removal.
- PARTIAL: all-stock exposes six cards but supplies only five detailed vehicle
  records to generation, with explicit evidence truncation.
- FAIL: lowest-mileage follow-up asks for a knowledge domain despite four
  history messages; standalone delivery omits known handover requirements;
  selected vehicle plus policies displays clarification and `Search completed.`
  without the requested synthesis; informational booking asks for a domain.

The compound status projection remains visibly correct: actual DOM assertions
and screenshot inspection show two sanitized child outcomes, including the
failed child. A truthful failure presentation is not an answer-quality pass.
The compound delivery answers correctly used25-mile coverage, three working
days after payment/preparation and photo ID; they did not invent a delivery fee.

Exact reviewed evidence is in
[the post-separation qualification](evidence/2026-10-25-post-separation-quality.json).
The preserved canary still runs Runtime473/framework0.8.41; this is an answer
recheck with the separated control planes, not a claim that main upgraded or
qualified the newer target-owned-document Runtime. That separate hosted proof
belongs to01046. Historical failed canary release and manual-host limitations
remain unchanged; no managed Engage installation acceptance is claimed here.

The three proposed framework prompt/planner/evidence-budget changes remain
unimplemented pending owner approval. The partial-compound synthesis finding
remains separately recorded, not silently added to that approval. No further
query runner, build or rollout is running after this checkpoint.

## 24 October: Projection Verified Live, Semantic Quality Still Unstable

The staging source/digest/schema210 and protected configuration/assignment/
funding hashes are verified after the asset rollout. The new first-party widget
SRI is verified. The full browser suite completed at17:50:42 UTC: **8 PASS,
2 PARTIAL,4 FAIL,6 SOURCE_GAP**, with no writes, browser errors or blocked
requests. The new compound projection passed its actual DOM assertions and
screenshot review: both clarification and successful-search child statuses are
visible, with no raw child data or executable child actions exposed.

That is a **presentation-contract pass, not an answer-quality pass**. The
selected-vehicle question still did not receive its requested answer in the
full run. Its successful child said only `Search completed.`; generation was
deferred while a sibling needed clarification. A separate two-turn repeat did
answer the selected price/mileage plus delivery/warranty correctly with four
documents and two completed obligations. Preserve both outcomes: the semantic
path is unstable, not permanently broken or consistently fixed.

Inventory-plus-policy now passed both clause orders, and unsupported towing
recommendations were avoided. However the full run again lost the lowest-
mileage follow-up and standalone delivery requirements. Booking information
still clarified its knowledge domain. More visible outcomes and faster turns
must not be presented as an overall quality improvement over the earlier9-PASS
run. The five-item evidence cap remains; the cheaper Morrow record was absent
from one five-hit RAG alternative set, so the cheapest returned alternative is
not proven globally closest.

Median5.04s/p958.16s include failed clarification turns and are not useful-answer
latency or a load benchmark. Exact reviewed evidence and hashes are in
[the projection qualification](evidence/2026-10-24-compound-projection-quality.json).

Main's Runtime/widget implementation and staging verification are complete.
The bounded framework patch is **awaiting owner approval**, not separation work.
No framework code/release, production control-plane asset rollout, original
customer runtime/assignment or old financial records were changed by this fix.
The successful-search/deferred-generation compound behavior is an additional
focused upstream finding; do not assume the three proposed prompt/planner/item-
budget changes alone repair partial synthesis.

## Runtime And Widget Implementation Record

This stream does not depend on the completed operations separation. Runtime
commit `b724d8e4a` and generic-widget commit `473bccbed` preserve sanitized
compound child status/answer/failure reason through the canonical API and the
widget's existing compound renderer. This is display-only: no raw child data,
executable child actions, automatic retry or semantic status rewriting.

The46 focused Runtime tests (including6 new cases), widget contract smoke,
typecheck/embedded build, action-presentation/semantic-boundary smokes and14
Platform asset/controller/manifest tests pass. Exact build source is
`473bccbed651d36726d21721244a2c70d5bfc9a7`. Runtime CI40115132788 and backend
asset CI40115132702 passed; the latter completed after one same-source rerun
following an overdue first attempt and repeated GitHub job/log HTTP502s. CI
failures and cancellation are retained, not relabelled green.

The internal runtime is source/digest verified and healthy on
`sha256:0523d9e41359a90024decf5ba340f150360151972b29d4321e53cd361e9a554e`.
Its normal framework mode now exposes the knowledge overview and requires an
explicit source choice when retrieval is requested. This does not enable
automatic fan-out, bypass an allowlist, or select sources by query text. Native
funding/authorization and all other settings are preserved.

A four-query live check confirms both flags in response metadata: stock,
lowest-mileage follow-up, and inventory-first plus delivery all answered
correctly; booking-information still clarified the domain. This is3 PASS/1 FAIL,
not a substitute for the full20-case repeat. Private evidence is
`compound-projection/source-catalogue-4/`, SHA256
`1d75d1d9c4e7199ed3b0454db9f6362813341bda6442095dd9d2e317fd0230b5`.

First-party widget delivery is being qualified on the **staging** control plane,
image `sha256:141755246fda0095cdb76698b3dd25e5202b20045d7199610552406763c40a59`.
Its Java/configuration source is identical to the accepted separation backend;
only packaged widget assets change. Production control-plane source, original
customer runtime/assignment and financial history remain unchanged. Browser
verification of the new child presentation is still pending at this checkpoint.

### Confirmed Framework Follow-Up Boundaries

The managed templates match the0.8.41 release tag and packaged curated JAR
(SHA256 `936d3d16b2b87e5738bdfb2d39c8828739dc64a416d8c830cb62087d32ec5eb2`).
Read-planner source and its template also match the release tag. Three bounded
changes have been proposed for owner approval, **not implemented or released**:

- Align managed extraction rules/sample schema with required booleans,
  compound obligation IDs and the effective source-recovery policy.
- Supply the read planner with the authorized knowledge-source catalogue.
  `planActions` currently supplies intent, eligible read actions and prior
  evidence, but no source catalogue/allowlist; its suggestedVectorSpaces output
  is optional. A planner may choose RAG after extraction selected no retrieval
  source, leaving routing unable to proceed. The model, not runtime phrase
  rules or unconditional fan-out, must make the source decision.
- Make the bounded evidence-item limit configurable. `sanitizeValue` currently
  uses a hard five-item list limit, so more character budget cannot make the
  sixth stock record available in that generated evidence. Keep explicit
  truncation diagnostics and finite limits.

The pending framework decision does not prevent the already-approved
Runtime/widget UI qualification from proceeding.

## Follow-Up: Explicit Guidance Trial Not Accepted

After the owner requested a working-status update, a focused11-query test ran
on the same internal runtime with configuration `ver-21e8391f`. Only the prompt
artifact changed: explicit structured extraction instructions plus stronger
evidence-only generation guidance. Numeric budgets, model, actions, source
content, framework and runtime image were unchanged. Normal draft validation
and publication passed; a cold restart loaded the new artifact. No new
infrastructure, business writes, customer assignment or funding change.

The test produced **5 PASS,1 PARTIAL,4 FAIL and1 SOURCE_GAP**. It correctly
refused to infer towing capability from AWD/SUV labels, but the lowest-mileage
follow-up regressed to knowledge-domain clarification. Inventory-first plus
policy, selected-context compound presentation and booking information still
failed. This is an11-case subset, not a new20-case total or proof that the
earlier configuration is reliably stable. **Do not promote this prompt bundle.**

The code inspection identifies two narrow contracts to investigate next:

- The managed extraction template says an uncertain vector space may be omitted
  and the system will route/fan out, while this deployment's actual recovery
  policy is `LLM_REPAIR_THEN_CLARIFY`. It also documents `needsAdvancedRAG` less
  strictly than the validator requires and omits `obligationId` in its sample
  schema. Additional deployment guidance did not consistently correct these
  decisions. Verify the exact released template before any framework change;
  this is not permission for deterministic semantic defaults.
- The framework sanitizer produces sanitized nested `children`, and the widget
  already has a compound-results renderer. The Runtime's canonical DTO does not
  project children, and the canonical widget adapter builds no `data.results`.
  A mixed success/clarification therefore loses useful per-child answers. A
  bounded safe projection must preserve each child's actual status, use only
  sanitized allowlisted fields and never replace a failed child with an invented
  success or expose raw internal data. No such code change is implemented yet.

The internal runtime remains on the explicitly recorded trial artifact; it is
not assigned to the original customer app. The independent separation chat's
12-check customer journey is different evidence and does not close these
answer-quality failures. No query runner is left active. Main released its
temporary firewall hold back to that chat for closure after its own live work.

Private raw evidence: `quality-20261024/tuned-semantic-guidance-11/`.
SHA256 `6bf033c6a2ac7855c0f435aaf6e812fd384b3f2e9cb45cb4bb781551450b957c`.

## Last Full20 Result: Configuration Trial Completed

The final internal live trial completed at15:34:44 UTC: **9 PASS,2 PARTIAL,
4 FAIL and5 SOURCE_GAP** across the same20 questions. The original baseline
had6 PASS,3 PARTIAL,5 FAIL and6 SOURCE_GAP. These are manual usefulness
judgments, not HTTP status checks or a statistically controlled model comparison.
The towing case moved from an honest missing-source response to an unsupported
recommendation and is now a failure, not an improvement.

Confirmed gains are the lowest-mileage follow-up, named two-vehicle comparison,
warranty retrieval and delivery requirements. The follow-up correctly identifies
Morrow C2,1,980 miles,Hatchback, receives four prior messages and restores three
trusted result targets. Empty stock results are unusable grounding and do lead
to indexed RAG. Removing selected context still restores broad browsing.

The four remaining failures are:

- **Unsupported towing recommendation.** AWD/SUV/automatic labels were used to
  recommend a vehicle for towing without a towing specification.
- **Mixed question still depends on clause order.** Inventory-first plus
  delivery retrieves stock but misses the delivery document. Reversing the
  clauses completes both obligations and synthesizes both evidence types.
- **Selected vehicle plus policies is not usefully projected.** The attachment
  resolves, but the browser receives only a generic `COMPOUND_HANDLED` summary,
  without outward child answers/actions/documents. Do not assume the hidden
  children succeeded; inspect them in a focused follow-up before changing the
  safe response contract.
- **Booking information still clarifies the knowledge domain.** It does not
  submit a write, but fails to explain requirements already in the policy.

The partial cases are all-stock completeness (now5/6, still explicit truncation)
and an insufficiently justified closest alternative. Five questions ask for
facts absent from the supplied policies; those refusals are correct and require
approved content, not weaker grounding.

### Exactly What Was Tested

- Internal runtime `dep-011d7f62`, framework `0.8.41`, runtime-only fix
  `1cdb1047faf0c5072c9dd963fa4606147333ab0a`. CI40105203683 passed both
  verification and publication. Source/digest were checked, and the canary was
  cold-replaced with image
  `sha256:2eb258d3710e059282cf1713d2a8ea14ac35ad396d20001e779b47342a97b418`.
  Both runtime and connector health are UP after the run. No framework release.
- Real published, integrity-checked widget and dealership pack; actual anonymous
  bootstrap/query routes; no substituted AI/action responses. The browser host
  document is an explicit manual fixture. Historical Platform release
  `rel-f7c0869c` remains FAILED and the canary Engage app remains DRAFT. This
  does **not** prove customer self-service, managed assignment, funded top-up
  acceptance or a successful Platform release.
- Same6 external stock records and3 active S3 policies; no source/business
  writes, vector transfer or dealership inventory database. The source is a
  simulator, not Auto Trader production or sandbox acceptance.
- Actual metadata verifies GPT-5.4-mini snapshot, executor/landing, ITERATIVE2,
  at most2 read actions, RAG_IF_ACTIONS_INSUFFICIENT, threshold0.40,
 8 documents/8000 RAG characters,8000 action-evidence characters and12000
  planner-context characters.
- Configuration `ver-6a9936e3` removes customer-authored prompt text but retains
  automatically generated Platform source-catalogue/compound guidance. It is
  **not** a pure canonical unmanaged framework-prompt test. The two action
  evidence/planner limits are framework-supported advanced runtime environment
  settings, not presently exposed as normal Platform draft controls.

Median observed latency improved from8.18s to5.76s; p95 from13.65s to9.25s.
All20 responses were HTTP200, with zero browser errors, blocked requests or
successful writes. Different model/settings/history and stochastic extraction
all changed together; do not attribute the result to one knob.

### Decision And Narrow Next Work

Production replacement and downtime are owner-approved. **The original customer
assignment is nevertheless unchanged**, because this candidate is not reliably
better on every important case. Its original managed token allowance remains
interrupted; prepaid OpenAI funds were not exhausted. No allowance history was
erased and no failed release was relabelled verified.

The next code investigation should focus on the active managed prompt's explicit
structured-output contract and safe compound response handling. Many turns
required configured LLM completion for missing `needsAdvancedRAG`, vector space
or compound `obligationId`; diagnostics retain the original failure and the
model's repair. Local managed template guidance differs from the ordinary
template, but a released-artifact focused reproduction is needed before claiming
that difference alone caused the remaining failures. Do not add phrase rules,
automatically select all sources, invent hidden IDs or silently fabricate fields.

Separately, expose bounded evidence/planner limits through existing deployment
settings if retained; review item-count truncation before increasing budgets
again. Correct the dedicated comparison action's unresolved INTERNAL references
without relaxing hidden-ID trust. Add any desired missing policies/specifications
as approved source content, and align fictional policy organization labels.

No more infrastructure/recovery expansion or configuration sweeps are part of
this completed comparison. The independent separation chat's control-plane
rollout hold was released after the final queries; it owns its rollout and final
firewall closure. Quality reports remain valid offline while it proceeds.

Reviewed cases: [tuned manual review](current-workspace-quality.tuned-review.json).
Compact measured evidence:
[tuned run](evidence/2026-10-24-tuned-customer-workspace-quality.json).
Private raw-report SHA256:
`cc2e74931220ad1b053bd616e117b1c337219aa74ab4d5e0fe3dcd3b50038f58`.

## Historical Baseline And Execution Record

The original baseline below is answer-quality evidence, not another infrastructure release gate. No
Platform/runtime/framework product code, deployed settings, source documents,
vector records or assignment was changed by these checks. Only verification
scripts, evidence and this review were added.

## Approved Follow-Up In Progress

The owner subsequently approved bounded Platform support for audited additive
allowance top-ups and a reviewed GPT-5.4-mini model profile, followed by a tuned
canary. These changes do not alter the historical baseline below and require no
framework release. They do not establish improved answers until the live canary
has actually run.

### Follow-Up Status At 11:05 UTC

- Product commit `9cc6721f5c4c6c00138d8561477e2d83149575d3` is pushed.
  Focused ledger78, OpenAI103, runtime83 and Platform189 tests passed. Runtime
  CI40083758635 and backend CI40083758579 passed. Both control planes run the
  new backend with schema209; the existing customer runtime/app assignment is
  unchanged and its old token allowance remains interrupted.
- A separately funded staging canary is owner-approved. The reviewed
  INFERENCE_PROFILE compiles GPT-5.4-mini snapshot `gpt-5.4-mini-2026-03-17`.
  The final candidate uses Milvus and the same1536-dimensional embedding model
  as the baseline, not the initially proposed Lucene512 configuration. The
  platform correctly rejected Lucene for customer-S3 document indexing; that
  rejected candidate was never deployed.
- Fresh canary `dep-5fff634a`/`ver-4c64d9d6` has reviewed external simulator and
  business-receiver bindings, read-only staging S3, explicit approval for the
  same three fictional public policies, and exact canary app origin. Admission
  accepted2000m CPU/4096MiB/one AI-state database/two indexes. No business
  inventory database, vector transfer, policy-filter weakening or original
  assignment replacement was performed. Prompts/planning remain unchanged;
  ITERATIVE2, threshold0.35 and increased context budgets are NOT applied.
- Apply `rel-d28483b7` failed with `MANAGED_VECTOR_TARGET_REQUIRED`. Inspection
  found Coolify still calls the legacy two-argument managed-vector method;
  Railway already passes the selected target. No canary runtime/vector service
  was created. The single resource handle is the S3 binding. One provider
  allocation exists and the admission reservation is `OUTCOME_UNKNOWN`; do not
  reset or silently refund those records. A narrow caller fix was proposed to
  the owner before implementation and is awaiting a decision.
- The tuned20-query rerun and live top-up proof are **not yet executed**. Do not
  turn deployment setup or passing unit tests into an answer-quality pass.

### Caller Fix Verified At 13:12 UTC

The owner approved the narrow Coolify caller fix. Commit
`924c05fc39c17b97a8af4ae7bada8af871b4f833` passes the release's selected target to
managed-vector provisioning in both branches, retaining the existing ownership
validation. The63 focused tests passed:43 Coolify provider tests and20 managed
vector tests, including exact staging/production propagation and visible target
validation failures. Backend-only CI40096234561 passed; the UI job was skipped.

Both control planes are live/healthy on backend image
`sha256:7ffa5b0c4d0c75b8201c41cd90c508ae5b9741210b44b3a568720b06a19f9b85`,
schema209 unchanged. Rollout readbacks preserve existing customer assignments,
configuration and funding. The customer runtime image/framework are unchanged.

The normal customer retry-plan API then returned409
`PREPARED_RECOVERY_CONTROL_FLOW_UNPROVEN`. Its existing proof supports other
failure boundaries, not the earlier `ensure_vector_backend` failure. No retry
was dispatched, reservation/allowance reset, recovery code added or safety
check bypassed. The caller fix is complete; the tuned quality suite remains
unexecuted.

The owner was asked whether to use the existing internal/staff staging-canary
workflow for answer testing rather than expand recovery scope. That workflow
would use the already-approved prepaid provider account and bounded query runner,
but not the customer canary's managed hard token/spend allowance. This is a
different operating/accounting path and must not be silently substituted or
reported as a successful customer funded-canary/top-up proof. Decision pending.

One fresh61-sample/5-minute read-only observation per host overlapped the build.
The ordinary fleet-policy renewal remains valid until15:03:05 UTC and preserves
physical/logical ceilings, headroom, customer grants and held reservations. This
does not implement the separate010.46 optional-operations plan.

### Internal Quality Run Authorized

The owner explicitly approved the existing internal/staff staging canary instead
of extending recovery for the failed funded customer attempt. They also permit
production optimization and downtime. This is not permission to erase or bypass
the existing funded allocation: its history and the original app remain unchanged.

The active preparation is `dep-011d7f62`, version `ver-04466513`, first apply
`rel-f7c0869c`, using the already-qualified runtime artifact `dsa-ebe59158` and
framework `0.8.41`. Normal draft validation passed without warnings or errors.
It uses the same external advertiser stock, S3 staging policies and1536-dimensional
embedding model as the baseline, with a new dedicated managed Milvus cluster and
AI-state PostgreSQL. No dealership business database is created. The temporary
workspace will retain executor/landing and the dealership experience pack; it is
anonymous/public for read-quality testing, not an authentication regression.

The planned tuning bundle is GPT-5.4-mini snapshot, ITERATIVE planning bounded
to2 iterations with RAG_IF_ACTIONS_INSUFFICIENT, explicit prompt retrieval
threshold0.40,8 context documents/8000 characters, and semantic instructions that
distinguish informational requirements from transactions and do not invent filter
parameters for comparative follow-ups. This is a bundle comparison, not a
model-only causal experiment. Actual response metadata must verify the settings;
normal validation/provisioning is not proof they affected live answers.

Setup failures are retained, not hidden: initial bootstrap rejected an explicit
managed-vector mode before the template replaced the base Lucene defaults; normal
incremental plugin edits could not pass whole-composition validation while other
required plugins were still unresolved; reviewed complete install defaults also
needed managed environment references rather than literal connection values.
Those configuration-only attempts created no native runtimes. The first native
staff target `dep-855ca27b` then exposed a missing explicit Milvus prefix; its retry
hit generation handling of a provisional, never-provisioned prior runtime. It is
not marked verified or repaired by editing history. A fresh first-apply target
includes its isolated prefix from the start. These are Platform setup findings,
not framework/LLM answer fixes. No recovery or drain checks were disabled.

The internal workflow uses the approved prepaid account and a bounded browser
runner, without a customer-managed hard token/spend allowance. Results from it
must not be presented as funded-canary or live top-up acceptance. At this
checkpoint the runtime is still provisioning and the tuned20-query suite has
**not yet run**.

### Live Manual-Host Run And A Concrete Runtime Fix

The20-query run completed at14:54 UTC using the integrity-checked, published
widget/dealership pack and the real canary anonymous routes. It did not activate
or substitute a Platform installation manifest: this is a manual integration
host fixture, **not customer app/release acceptance**. Three policies are ACTIVE
and all6 stock indexing operations completed without source writes. Actual
response metadata confirms executor/landing, the reviewed mini snapshot,
ITERATIVE2, RAG threshold0.40 and8 documents/8000 context characters.

The same-source comparison can now use two stock read calls; warranty retrieval
and empty-action plus indexed-stock retrieval also work. Remaining quality issues
include limited all-stock evidence, delivery wording, selected-context compound
presentation, and informational test-drive requirements. Safe refusals for
absent finance, complaints, reservation and opening-hours facts are retained.
This run is **not an all-green answer-quality result**.

All follow-up responses reported zero history messages despite a stable
conversation/session. Runtime logs reproduce `Atomic owned-session mutation is
required for confirmation state` during ConversationRecording. The cause is
LoomAI's `RuntimeRetirementStoreFencing.FencedSessions`: it wraps the framework
storage SPI but did not forward `mutateOwnedSession`, inheriting the SPI's
deliberate fail-closed default. This is not an LLM reasoning failure or a setting
to disable. It also explains why switching models cannot repair those turns.

Commit `1cdb1047faf0c5072c9dd963fa4606147333ab0a` forwards the atomic operation
through the existing conversation write fence to the real storage provider.
It does not replace atomic writes with read/save, weaken owner checks, disable
fencing, or change the framework.29 focused tests pass, including7 new tests for
two consecutive framework-recorded turns, exact delegation, empty ownership
results, failure propagation, revoked authority and lock coverage. Runtime CI
40105203683 has passed verification; image publication/live rerun are pending
at this checkpoint. No framework release or original production wallet reset.

Operator helper corrections are not product defects: the native connector uses
`/api/admin/integrations`, while `/api/admin/connector/integrations` is the
runtime's proxy route. The Platform caller correctly sends the latter to the
runtime. Likewise, `originalFilename` is system-owned metadata, and human
handover owner-message routes must not be copied to a canary without handover.
The manual harness now advertises its genuine normal query route, preserves
template braces, and stops immediately on blocked/non-success HTTP exchanges.
Failed fixture runs are retained separately and are not AI-quality passes.

Read-only production accounting inspection at 09:35 UTC found 38 of 50
interactive requests and 374,121 of 500,000 tokens accounted, with conservative
cost USD 0.06335726 of USD 2. All 130 provider attempts were SETTLED. The next
full-context reservation (128,000 input plus 2,048 output tokens) cannot fit the
remaining token ceiling. This is **not** exhaustion of the owner's prepaid
OpenAI account, and there are no ambiguous holds to erase or refund.

Execution sequence: qualify additive accounting and exact private administration;
qualify the reviewed model/price profile; publish source-frozen backend/runtime
images; use supported audited rollout/funding boundaries; rerun the original
query corpus in a separate evidence root. Keep authorization, trusted hidden
IDs, tenant/source filters, finite iteration limits and the original ledger.
Do not turn this task into drain/recovery engineering. Existing immutable v1
grants permit only GPT-4o-mini; they cannot silently authorize the new model.

## Scope And Method

- Live app: https://northfield-console-live.apps.loomai.pro
- Deployment `dep-3c4dbe4f`, production release `rel-572ceb27`, version
  `ver-26d678f3`, AI Fabric `0.8.41`.
- Actual browser requests used the app's existing **executor / landing**
  contract. No mode, position, retrieval scope, prompt or policy was forced by
  the runner. This is different from the historical executor/search demo.
- The earlier 14-query catalogue was retained and extended with six current
  product checks: all stock, relaxed-budget follow-up, finance evidence boundary,
  actual vehicle selection, selected-vehicle-plus-policy and broad browsing
  after removal. Twenty questions ran across six isolated anonymous browser
  conversations, with later turns preserving their session's conversation.
- Responses, request mode/position, action parameters, LLM extraction diagnostics,
  source routing, returned documents, scores, screenshots and latency were
  captured from actual network exchanges. The debug UI was not needed to obtain
  the same outward metadata. Answers were reviewed manually; no lexical
  answer-matching rule determined the verdicts.
- No booking/lead/callback was submitted, no confirmation was sent, and no human
  handover was started. The suite's only successful actions were read actions.

Baseline: `2026-10-24T08:04:11.910Z` to `08:07:55.222Z`.
All twenty responses were HTTP200; zero browser errors or blocked requests.
That is NOT equivalent to twenty useful answers.

## Results

- **6 fully answered:** electric stock, higher-budget diesel no-match, reversed
  inventory/delivery question, vehicle-card selection, selected vehicle plus
  delivery/warranty, and broad browsing after context removal.
- **3 partial:** all-stock list omitted half its results; an honest no-match
  suggested an alternative without proving it was closest or using the requested
  indexed evidence; one mixed answer contained a truncated image URL.
- **5 quality failures:** lowest-mileage follow-up, named comparison, warranty
  wording, delivery/handover wording, and asking about booking requirements
  without requesting submission.
- **6 missing-source cases:** towing specifications, reservation terms, exact
  minimum test-drive age/licence duration, complaint SLA, showroom hours/access
  and guaranteed finance terms are absent from current supplied content.
  Refusing to invent these facts is correct; it still limits the product.

Median observed turn latency: **8.18 seconds**. Nearest-rank p95: **13.65
seconds**, maximum14.92 seconds. These are twenty sequential browser timings,
not a throughput or load benchmark.

## Findings And Owning Boundaries

### 1. Follow-Up Reasoning Fails Despite Available Facts

After the correct three electric vehicles, the query
`Of those, which has the lowest mileage, and what body type is it?` did not
identify Morrow C2,1,980 miles,Hatchback. The planner instead called inventory
with `maxMileage:1, limit:1`, received no results and generated a no-evidence
answer. History was present. This was not missing inventory or a transport error.

First canary: restore the previously quality-tested orchestration model and
bounded ITERATIVE planning; strengthen the parameter description so a maximum
mileage must be a buyer-specified numeric upper limit, not a ranking request.
Let the LLM compare the returned evidence. Do not detect phrases such as
"lowest mileage" with runtime text matching or hard-code a winning vehicle.

### 2. Comparison Has A Plugin Contract Problem

`Compare the Aster E1 and Morrow C2 ...` returned a trusted-context clarification
after those vehicles had already been returned by inventory. Debug shows one
untrusted hidden parameter.

The current comparison action declares `vehicleReferences` as an INTERNAL,
required, evidence-bound STRING but supplies **no `resolveFrom`**. Framework
validation deliberately rejects unresolved hidden parameters even in WARN mode.
The approved0.8.41 source confirms this behavior; these inspected files have no
diff from the release tag. This is not evidence that WARN is broken.

Correct the plugin's trusted collection-resolution contract. First write a
focused contract test using existing configured resolution. If0.8.41 cannot
resolve a selected subset of multiple trusted targets, request a narrow generic
framework capability. Do not make stock IDs public, trust model-generated CSV
identifiers, relax evidence validation or ask customers for internal IDs.

### 3. Policy Retrieval Is Sensitive To Wording, Not Wrong-Domain Routing

The Northfield warranty question returned no evidence although `warranty.txt`
was ACTIVE. Debug shows the correct `document` vector space and
`dealership-public-document-knowledge` source were searched successfully, with
zero results, no provider error and no post-filter rejection.

Plain wording asking for the approved dealership warranty and delivery policies
retrieved the registered documents and produced correct answers. The selected
vehicle compound query also retrieved warranty and delivery at scores about
0.513 and0.505, just above today's0.50 cutoff.

This supports a relevance/query/content calibration problem. It does **not**
prove the exact suppressed candidate score: below-threshold candidates are not
exposed in this response. Canary0.35-0.40 within the same tenant/deployment/source
filters, and compare recall plus irrelevant-policy leakage. Do not globally
disable filtering, force every source or add a keyword routing table.

The policy documents currently identify a "Fictional Canary Dealership", not
Northfield. Use meaningful, consistently named fictional dealership documents
for the demo; this is customer/source data, not a prompt authorizing new access.

### 4. Context Budgets Hide Available Inventory Facts

The all-stock action returned six complete records. Its planner evidence was
compacted to three plus `_omittedItems:3` under the2,400-character action budget.
The answer honestly stated that the other three were omitted, but did not satisfy
the request to show all stock.

Canary an8,000-character action-evidence budget and8,000-character generation
context while retaining finite limits. This cannot currently be expressed fully
through the normal Platform read-action override: its validator accepts only
`planningMode`, `maxIterations` and `ragCooperationMode`, and provisioning maps
only those fields. Exposing the existing framework budget is a **Platform code
change**, not a new framework feature or permission to hand-edit Coolify env.

### 5. Informational Booking Requests Are Misclassified

`Tell me what details and confirmation you need, but do not submit anything`
was classified as the protected test-drive write and denied to the anonymous
visitor. Security held, but the product failed to answer a legitimate public
question. No write succeeded and no receipt was created.

Use semantic instructions in the action-selection/extraction prompts to
distinguish information about requirements/procedure from permission to submit.
Restore the tuned model and test the exact prompt again. Keep actual booking
submission protected and confirmed. No phrase detector or automatic fallback
from a denied write should silently rewrite the model's decision.

### 6. The Demo Data Is Thinner Than The Earlier Quality Fixture

Only `delivery.txt`, `warranty.txt` and `test-drive.txt` are registered. Delivery
mentions25-mile coverage, three working days after payment/preparation and photo
ID, but no delivery fee. Test-drive policy says30minutes, valid driving licence,
accompaniment, availability and identity checks; it does not state minimum age or
licence-holding duration. Reservation, complaint SLA and showroom accessibility
documents from the older demo are not part of this source set.

The provider simulator's official-shaped stock projection currently adds generic
"Synthetic provider contract fixture" and transmission features. It does not
supply towing capacity, seat count, boot capacity or detailed equipment. A
stronger model cannot legitimately manufacture those attributes. Enrich approved
fictional simulator data and customer-owned document content separately, then
sync/index normally. Keep this out of generic chat components and do not create
a dealership business database per AI deployment.

### 7. Compound Media Truncation Needs Focused Code Investigation

The forward mixed inventory/delivery answer cut the third image URL at
`.../simulator-ve`; the reverse order returned complete links. Vehicle facts and
the delivery answer were otherwise correct, and structured media remained intact.

The0.8.41 `CompoundReadEvidenceSupport.buildActionEvidenceContext` truncates an
action evidence string at an arbitrary character boundary. That can feed an
incomplete URL into final synthesis. This is a concrete framework code-risk
candidate, not proof that the model alone invented the malformed URL. Reproduce
the actual evidence/context cut in a focused test before changing it. The
appropriate fix would preserve structured values/whole items under a budget,
not repair generated URLs with regex or append guessed image paths.

## Earlier Tuned Settings And Current Constraints

The historical0.8.21 quality run was14/14 on a different deployment, with
executor/search, ITERATIVE planning, two iterations and the same explicit
`RAG_IF_ACTIONS_INSUFFICIENT` policy. Earlier quality notes identify
`gpt-5.4-mini` for orchestration/generation. Today's live responses demonstrate
`gpt-4o-mini-2024-07-18`, SINGLE_PASS/one iteration and executor/landing. Both use
the default curated module/pack; there is no evidence that switching to another
curated module alone would correct the observed defects.

Proposed bounded canary, not yet applied:

- Restore reviewed `gpt-5.4-mini` orchestration and generation settings, with
  finite output/token/cost limits. Keep embeddings1536-dimensional and the
  current vector bindings unchanged; model tuning does not require reindexing.
- Restore ITERATIVE/two iterations and retain RAG_IF_ACTIONS_INSUFFICIENT,
  current allowed read actions and semantic-recovery policy.
- Calibrate document similarity0.35-0.40; increase generation context8,000chars
  and action evidence8,000chars. Keep the same data and UI position for the first
  comparison so changes are attributable. A later search-position experiment is
  separate; do not claim it is required to repair retrieval.
- Improve semantic parameter/action descriptions; keep IDs INTERNAL and
  evidence-bound. Provide real approved data for capability claims.
- Rerun the exact suite, including negative/source-gap cases and multiple sessions.
  A new green run would not erase the baseline or operationally blocked controls.

**Two product-code restrictions require review first:**

1. `ProviderUsageRuntimeConfiguration` hard-codes gpt-4o-mini for primary,
   orchestration and generation. Model fields exist in the UI, but this managed
   spending profile rejects a different model. Add a reviewed model/rate-card
   profile, not an unchecked bypass. The currently published GPT-5.4 Mini model
   supports structured output and function calls; its standard text pricing is
   USD0.75input/0.075cached-input/4.50output per million tokens as checked on
   24October. Verify request/account compatibility before rollout.
   [Official model documentation](https://developers.openai.com/api/docs/models/gpt-5.4-mini).
2. Read-action evidence budget overrides need validated configuration/provisioning
   support. Existing RAG prompt controls already support deployment-owned context
   and similarity values; do not incorrectly classify those as absent APIs.

## Operational Interruption And Current Live Consequence

The baseline20 completed before a usage interruption. Of six later controls,
the first two plain policy questions answered correctly. The third retrieved all
three documents but final generation stopped with
`fallbackReason=PROVIDER_BUDGET_INTERRUPTED`; three later controls returned the
same operational error. Those four are **inconclusive**, not model-quality fails.
The runner now stops on that structured reason even when HTTP status is200;
the original four interrupted exchanges are preserved, not replayed as passes.

Further AI queries on this canary are currently blocked by that built-in
allowance. This is not proof that the prepaid OpenAI account has no funds. We did
not obtain a privileged ledger readback, so the exact exhausted dimension
(chat attempts, tokens or USD envelope) is not yet established. Normal Platform
grants and active allocation caps are immutable; currently exposed renewal only
extends validity, not allowance. No counters were reset, approval switched,
ledger edited or financial policy disabled.

The user has approved spending and reuse of earlier tuning. A separate explicit
code-review question was sent before implementation: bounded audited top-up plus
reviewed-model support, followed by the tuned canary. **No such product code
change, allowance increase, tuned rollout or new framework release is claimed.**

## Evidence And Reuse

- [Captured, reviewed evidence](evidence/2026-10-24-current-customer-workspace-quality.json)
- [Current query list](current-workspace-quality.scenarios.json)
- [Wording/fresh-session controls](current-workspace-quality.controls.json)
- [Manual review decisions](current-workspace-quality.review.json)
- Real browser runner: `current-workspace-quality.mjs`; requires private `--root`
  and `--scope`, discovers runtime routes through the actual public manifest and
  does not force retrieval scope, UI mode or UI position.
- Private full exchanges/screenshots/config reads remain under
  `/Users/mahmoudashraf/.codex/private/loomai-m4/quality-20261024/`.
- Current analysis UI is a separate local canvas, not a deployed Platform page.

Source references for the proposed code boundaries:

- Platform: `ProviderUsageRuntimeConfiguration.java:32`,
  `DeploymentDraftValidationService.java:1931`,
  `RailwayProvisioningPlanService.java:1202`,
  `RuntimeProviderBudgetAdminController.java:29` and
  `ProviderUsageAllowanceService.java:84`.
- AI Fabric0.8.41: `ActionParamValidationSupport.java:104`,
  `ActionContextParamResolutionSupport.java:165`,
  `ReadActionResolutionService.java:836`,
  `CompoundReadEvidenceSupport.java:664`.

No new text-matching decision engine, hard-coded answer, silent model fallback,
scope relaxation, recovery/drain redesign or broad release matrix was added.
